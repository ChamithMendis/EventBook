import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelApiCaller {

    public static void main(String[] args) throws InterruptedException {
        String apiUrl = "http://localhost:8080/api/bookings";
        long eventId = 1L;
        long seatId = 1L;          // everyone fights for THIS seat
        int noOfUsers = 200;

        CountDownLatch startGun = new CountDownLatch(1);        // fired by main
        CountDownLatch doneLatch = new CountDownLatch(noOfUsers); // counted by workers

        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        ExecutorService executorService = Executors.newFixedThreadPool(noOfUsers);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        for (int i = 0; i < noOfUsers; i++) {
            final long userId = i + 1;
            executorService.submit(() -> {
                try {
                    String json = """
                            {"eventId": %d, "seatId": %d, "userId": %d}
                            """.formatted(eventId, seatId, userId);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(apiUrl))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    startGun.await(); // everyone waits here, ready to fire

                    HttpResponse<String> response =
                            client.send(request, HttpResponse.BodyHandlers.ofString());

                    int status = response.statusCode();
                    if (status == 200 || status == 201) {
                        success.incrementAndGet();
                        System.out.println("User " + userId + " BOOKED the seat! Status: " + status);
                    } else if (status == 409) {
                        conflict.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                        System.out.println("User " + userId + " got unexpected status: "
                                + status + " body: " + response.body());
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                    System.err.println("User " + userId + " failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        System.out.println("All threads ready. Firing simultaneously...");
        startGun.countDown();   // 🔫 release all threads at once

        doneLatch.await();
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("=====================================");
        System.out.println("Total requests : " + noOfUsers);
        System.out.println("Successes      : " + success.get() + "  (should be 1!)");
        System.out.println("Conflicts (409): " + conflict.get());
        System.out.println("Other/Errors   : " + other.get());
        System.out.println("=====================================");
    }
}