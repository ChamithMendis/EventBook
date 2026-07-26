package com.chamith.eventbook.concurrency;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One ReentrantLock per seat, created lazily. A single global lock would
 * serialize bookings for every seat across every event; keying by seatId
 * means only requests fighting over the SAME seat ever block each other.
 *
 * The registry itself uses ConcurrentHashMap so that concurrent calls to
 * lockFor() for different (or even the same) seatId don't race while
 * creating entries — computeIfAbsent is atomic per key.
 *
 * ReentrantLock over synchronized: same-thread reentrancy is still there,
 * but we also get tryLock(timeout) so a caller can fail fast instead of
 * queueing forever, plus (if we ever needed it) lockInterruptibly() and
 * a fair-ordering constructor (new ReentrantLock(true)) — none of which
 * plain synchronized offers.
 *
 * Note: this only coordinates threads inside this one JVM. Run two
 * instances of this app and each has its own registry, so the race
 * reopens across instances (that's what distributed locking fixes later).
 */
@Component
public class SeatLockRegistry {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock lockFor(Long seatId) {
        return locks.computeIfAbsent(seatId, id -> new ReentrantLock());
    }
}
