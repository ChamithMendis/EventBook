package com.chamith.eventbook.concurrency;

import com.chamith.eventbook.domain.SeatStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An in-process, always-up-to-date view of seat availability per event,
 * separate from the Redis cache built earlier. The contrast is the point:
 * Redis is cross-instance and TTL-based (can be briefly stale); this is
 * single-instance and synchronously updated on every booking, so within
 * this one JVM it's never stale.
 *
 * One ReentrantReadWriteLock per event: many concurrent readers (the
 * availability endpoint) can hold the read lock together, but a booking
 * write for that event excludes all of them for the moment it's applying
 * an update. Different events don't contend with each other at all.
 *
 * Caveat worth noticing: BookingService updates this cache from inside
 * bookSeat's method body, before @Transactional's proxy-managed commit
 * actually happens. If the transaction were to roll back after that point,
 * this cache would say BOOKED for a booking that never really committed —
 * the same class of "in-process state vs. proxy commit boundary" mismatch
 * we flagged with the synchronized/ReentrantLock version.
 */
@Component
public class EventSeatAvailabilityCache {

    private static final class EventEntry {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final Map<Long, SeatStatus> statuses = new HashMap<>();
    }

    private final ConcurrentHashMap<Long, EventEntry> events = new ConcurrentHashMap<>();

    public void initEvent(Long eventId, Map<Long, SeatStatus> initialStatuses) {
        EventEntry entry = events.computeIfAbsent(eventId, id -> new EventEntry());
        entry.lock.writeLock().lock();
        try {
            entry.statuses.putAll(initialStatuses);
        } finally {
            entry.lock.writeLock().unlock();
        }
    }

    public void updateSeatStatus(Long eventId, Long seatId, SeatStatus status) {
        EventEntry entry = events.computeIfAbsent(eventId, id -> new EventEntry());
        entry.lock.writeLock().lock();
        try {
            entry.statuses.put(seatId, status);
        } finally {
            entry.lock.writeLock().unlock();
        }
    }

    /** Returns a defensive copy so callers never hold a live view outside the lock. */
    public Map<Long, SeatStatus> snapshot(Long eventId) {
        EventEntry entry = events.computeIfAbsent(eventId, id -> new EventEntry());
        entry.lock.readLock().lock();
        try {
            return new HashMap<>(entry.statuses);
        } finally {
            entry.lock.readLock().unlock();
        }
    }
}
