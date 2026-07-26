package com.chamith.eventbook.concurrency;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * One lock object per seat, created lazily. A single global lock would
 * serialize bookings for every seat across every event; keying by seatId
 * means only requests fighting over the SAME seat ever block each other.
 *
 * The registry itself uses ConcurrentHashMap so that concurrent calls to
 * lockFor() for different (or even the same) seatId don't race while
 * creating entries — computeIfAbsent is atomic per key.
 *
 * Note: this only coordinates threads inside this one JVM. Run two
 * instances of this app and each has its own registry, so the race
 * reopens across instances (that's what distributed locking fixes later).
 */
@Component
public class SeatLockRegistry {

    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public Object lockFor(Long seatId) {
        return locks.computeIfAbsent(seatId, id -> new Object());
    }
}
