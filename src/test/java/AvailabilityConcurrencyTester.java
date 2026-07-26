import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Many reader threads hammer GET /api/events/{eventId}/availability in a
 * tight loop while one writer thread books a seat partway through, then
 * exits. Afterwards we print every read's timestamp/latency relative to
 * the write and flag which ones overlapped it.
 *
 * Manage expectations: the write's actual critical section is a single
 * HashMap.put() under a lock — sub-millisecond. Each HTTP round trip is
 * likely slower than that. So don't expect to see reads pause for
 * hundreds of milliseconds like the DB-booking race demo; the interesting
 * things to look for are (a) whether any read result is inconsistent/torn,
 * and (b) exactly which read is the first to observe BOOKED.
 */
public class AvailabilityConcurrencyTester {

    public static void main(String[] args) throws InterruptedException {
        String baseUrl = "http://localhost:8080";
        long eventId = 7L;   // change to an event you've created
        long seatId = 9768L; // change to a seat belonging to that event
        String userId = "rw-lock-tester";

        int readerThreads = 8;
        long warmupMillis = 300;    // let readers run a bit before the write fires
        long postWriteMillis = 500; // keep reading a bit after the write, to see the transition

        String availabilityUrl = baseUrl + "/api/events/" + eventId + "/availability";
        String bookingUrl = baseUrl + "/api/bookings";
        Pattern statusForTargetSeat = Pattern.compile(
                "\\{\"seatId\":" + seatId + ",\"status\":\"(\\w+)\"}");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicBoolean keepReading = new AtomicBoolean(true);
        AtomicLong readSeq = new AtomicLong();
        ConcurrentLinkedQueue<ReadRecord> reads = new ConcurrentLinkedQueue<>();
        WriteRecord[] writeResult = new WriteRecord[1];

        long testStartNanos = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(readerThreads + 1);

        for (int r = 0; r < readerThreads; r++) {
            int readerId = r;
            pool.submit(() -> {
                try {
                    startGate.await();
                    while (keepReading.get()) {
                        long seq = readSeq.incrementAndGet();
                        long start = System.nanoTime();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(availabilityUrl))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();
                        try {
                            HttpResponse<String> response =
                                    client.send(request, HttpResponse.BodyHandlers.ofString());
                            long end = System.nanoTime();
                            String status = extractStatus(statusForTargetSeat, response.body());
                            reads.add(new ReadRecord(readerId, seq, start - testStartNanos,
                                    end - testStartNanos, response.statusCode(), status));
                        } catch (Exception e) {
                            reads.add(new ReadRecord(readerId, seq, start - testStartNanos,
                                    System.nanoTime() - testStartNanos, -1, "ERROR:" + e.getMessage()));
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        pool.submit(() -> {
            try {
                startGate.await();
                Thread.sleep(warmupMillis);

                String json = """
                        {"seatId": %d, "userId": "%s"}
                        """.formatted(seatId, userId);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(bookingUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                long start = System.nanoTime();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long end = System.nanoTime();
                writeResult[0] = new WriteRecord(start - testStartNanos, end - testStartNanos,
                        response.statusCode(), response.body());

                Thread.sleep(postWriteMillis);
            } catch (Exception e) {
                System.err.println("Writer failed: " + e.getMessage());
            } finally {
                keepReading.set(false);
            }
        });

        System.out.println("Starting: " + readerThreads + " readers + 1 writer...");
        startGate.countDown();

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        report(reads, writeResult[0]);
    }

    private static String extractStatus(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        return m.find() ? m.group(1) : "NOT_FOUND";
    }

    private static void report(ConcurrentLinkedQueue<ReadRecord> reads, WriteRecord write) {
        List<ReadRecord> sorted = reads.stream()
                .sorted((a, b) -> Long.compare(a.startNanos, b.startNanos))
                .toList();

        System.out.println("=====================================");
        System.out.println("Total reads: " + sorted.size());
        if (write == null) {
            System.out.println("Writer never completed!");
            return;
        }

        double avgLatencyMs = sorted.stream()
                .mapToLong(r -> r.endNanos - r.startNanos)
                .average().orElse(0) / 1_000_000.0;
        long maxLatencyMs = sorted.stream()
                .mapToLong(r -> r.endNanos - r.startNanos)
                .max().orElse(0) / 1_000_000;

        System.out.printf("Read latency: avg=%.2fms max=%dms%n", avgLatencyMs, maxLatencyMs);
        System.out.printf("Write window: [%.2fms - %.2fms] status=%d%n",
                write.startNanos / 1_000_000.0, write.endNanos / 1_000_000.0, write.statusCode);
        System.out.println("Write response: " + write.body);

        ReadRecord lastAvailableBeforeWrite = null;
        ReadRecord firstBookedAfterWrite = null;
        int overlappingWrite = 0;
        int inconsistentAfterFirstBooked = 0;
        boolean seenBooked = false;

        for (ReadRecord r : sorted) {
            boolean overlaps = r.startNanos < write.endNanos && r.endNanos > write.startNanos;
            if (overlaps) {
                overlappingWrite++;
            }
            if ("AVAILABLE".equals(r.seatStatus) && r.startNanos < write.startNanos) {
                lastAvailableBeforeWrite = r;
            }
            if ("BOOKED".equals(r.seatStatus)) {
                if (!seenBooked) {
                    firstBookedAfterWrite = r;
                    seenBooked = true;
                }
            } else if (seenBooked && "AVAILABLE".equals(r.seatStatus)) {
                // flip-flopped back to AVAILABLE after we'd already seen BOOKED — inconsistent
                inconsistentAfterFirstBooked++;
            }
        }

        System.out.println("Reads overlapping the write's time window: " + overlappingWrite);
        System.out.println("Last read seeing AVAILABLE before write started: "
                + describe(lastAvailableBeforeWrite));
        System.out.println("First read seeing BOOKED: " + describe(firstBookedAfterWrite));
        System.out.println("Reads that flip-flopped back to AVAILABLE after BOOKED was seen "
                + "(should be 0 — would indicate a torn/inconsistent read): " + inconsistentAfterFirstBooked);
        System.out.println("=====================================");
    }

    private static String describe(ReadRecord r) {
        if (r == null) {
            return "none";
        }
        return String.format("reader=%d seq=%d at %.2fms (latency %.2fms)",
                r.readerId, r.seq, r.startNanos / 1_000_000.0,
                (r.endNanos - r.startNanos) / 1_000_000.0);
    }

    private record ReadRecord(int readerId, long seq, long startNanos, long endNanos,
                               int statusCode, String seatStatus) {
    }

    private record WriteRecord(long startNanos, long endNanos, int statusCode, String body) {
    }
}
