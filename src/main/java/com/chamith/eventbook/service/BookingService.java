package com.chamith.eventbook.service;

import com.chamith.eventbook.concurrency.SeatLockRegistry;
import com.chamith.eventbook.domain.Booking;
import com.chamith.eventbook.domain.Seat;
import com.chamith.eventbook.domain.SeatStatus;
import com.chamith.eventbook.dto.CreateBookingAdjacentRequest;
import com.chamith.eventbook.dto.CreateBookingRequest;
import com.chamith.eventbook.repository.BookingRepository;
import com.chamith.eventbook.repository.SeatRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Deliberately naive: read-then-write with no locking of any kind.
 * Under concurrent requests for the same seat this allows more than
 * one booking to succeed for a single seat (a lost-update race).
 * That failure is the starting point for the locking modules that follow.
 */
@Service
public class BookingService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final SeatLockRegistry seatLockRegistry;

    public BookingService(SeatRepository seatRepository, BookingRepository bookingRepository,
                           SeatLockRegistry seatLockRegistry) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.seatLockRegistry = seatLockRegistry;
    }

    /**
     * synchronized on a per-seat lock object: only one thread in this JVM
     * can be inside the block for a given seatId at a time, so the second
     * thread's status check can no longer run concurrently with the first
     * thread's write.
     *
     * Caveat to notice when testing: this block ends (and the lock is
     * released) when the method returns, but @Transactional's commit
     * happens in the proxy AFTER the method returns — so there's a small
     * window between "lock released" and "write actually committed" where
     * @Version is still doing real work as a safety net, not just standing
     * by unused.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 20)
    )
    @Transactional
    public Booking bookSeat(CreateBookingRequest request) throws InterruptedException {
        Object lock = seatLockRegistry.lockFor(request.seatId());
        synchronized (lock) {
            Seat seat = seatRepository.findById(request.seatId())
                    .orElseThrow(() -> new NoSuchElementException("Seat not found: " + request.seatId()));

            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new IllegalStateException("Seat " + seat.getSeatNumber() + " is already booked");
            }

            Thread.sleep(100);

            seat.setStatus(SeatStatus.BOOKED);
            seatRepository.save(seat);

            Booking booking = new Booking(seat, request.userId());
            return bookingRepository.save(booking);
        }
    }

    @Transactional
    public List<Booking> bookAdjacentSeats(CreateBookingAdjacentRequest request) throws InterruptedException {
//        Seat seat = seatRepository.findById(request.seatId())
//                .orElseThrow(() -> new NoSuchElementException("Seat not found: " + request.seatId()));
        List<Booking> bookingList = new ArrayList<>();
        for (int i = 0; i < request.seatIds().size(); i++) {
            Long seatId = request.seatIds().get(i);
            Seat seat = seatRepository.findBySeatId(seatId)
                    .orElseThrow(() -> new NoSuchElementException("Seat not found: " + (seatId)));

            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new IllegalStateException("Seat " + seatId + " is already booked");
            }

            Thread.sleep(100);

            seat.setStatus(SeatStatus.BOOKED);
            seatRepository.save(seat);

                    Booking booking = new Booking(seat, request.userId());
//            Booking booking = new Booking(seat.getId(), request.userId());
            bookingList.add(booking);
            bookingRepository.save(booking);
        }
        return bookingList;
    }
}
