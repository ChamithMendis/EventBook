package com.chamith.eventbook.service;

import com.chamith.eventbook.concurrency.EventSeatAvailabilityCache;
import com.chamith.eventbook.concurrency.SeatLockRegistry;
import com.chamith.eventbook.concurrency.SeatLockTimeoutException;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
    private final EventSeatAvailabilityCache availabilityCache;

    public BookingService(SeatRepository seatRepository, BookingRepository bookingRepository,
                           SeatLockRegistry seatLockRegistry, EventSeatAvailabilityCache availabilityCache) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.seatLockRegistry = seatLockRegistry;
        this.availabilityCache = availabilityCache;
    }

    private static final long LOCK_WAIT_MILLIS = 500;

    /**
     * ReentrantLock per seat instead of synchronized: tryLock(timeout) lets
     * us fail fast with a clear "try again" response instead of queueing
     * every contending request indefinitely behind whoever got there first.
     *
     * Caveat to notice when testing: the lock is released when the method
     * returns, but @Transactional's commit happens in the proxy AFTER the
     * method returns — so there's a small window between "lock released"
     * and "write actually committed" where @Version is still doing real
     * work as a safety net, not just standing by unused.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 20)
    )
    @Transactional
    public Booking bookSeat(CreateBookingRequest request) throws InterruptedException {
        ReentrantLock lock = seatLockRegistry.lockFor(request.seatId());

        if (!lock.tryLock(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            throw new SeatLockTimeoutException(
                    "Seat " + request.seatId() + " is busy, try again in a moment");
        }
        try {
            Seat seat = seatRepository.findById(request.seatId())
                    .orElseThrow(() -> new NoSuchElementException("Seat not found: " + request.seatId()));

            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new IllegalStateException("Seat " + seat.getSeatNumber() + " is already booked");
            }

            Thread.sleep(100);

            seat.setStatus(SeatStatus.BOOKED);
            seatRepository.save(seat);
            availabilityCache.updateSeatStatus(seat.getEvent().getId(), seat.getId(), SeatStatus.BOOKED);

            Booking booking = new Booking(seat, request.userId());
            return bookingRepository.save(booking);
        } finally {
            lock.unlock();
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
