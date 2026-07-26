package com.chamith.eventbook.service;

import com.chamith.eventbook.concurrency.EventSeatAvailabilityCache;
import com.chamith.eventbook.domain.Event;
import com.chamith.eventbook.domain.Seat;
import com.chamith.eventbook.domain.SeatStatus;
import com.chamith.eventbook.dto.CreateEventRequest;
import com.chamith.eventbook.dto.EventResponse;
import com.chamith.eventbook.dto.SeatAvailabilityResponse;
import com.chamith.eventbook.repository.EventRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventSeatAvailabilityCache availabilityCache;

    public EventService(EventRepository eventRepository, EventSeatAvailabilityCache availabilityCache) {
        this.eventRepository = eventRepository;
        this.availabilityCache = availabilityCache;
    }

    /**
     * Evicts the "all events" list cache: the next listEventResponses() call
     * is a genuine cache miss and repopulates from Postgres. We don't touch
     * the "event" (single) cache here since existing events weren't modified.
     *
     * Also seeds the in-memory availability cache for this event so the new
     * /availability endpoint has something to read — events created before
     * this feature existed (or before the last restart) won't have an entry.
     */
    @Transactional
    @CacheEvict(cacheNames = "events", key = "'all'")
    public Event createEvent(CreateEventRequest request) {
        Event event = new Event(request.name(), request.venue(), request.eventTime());
        for (int i = 1; i <= request.totalSeats(); i++) {
            event.getSeats().add(new Seat(event, "S" + i));
        }
        Event saved = eventRepository.save(event);

        Map<Long, SeatStatus> initialStatuses = new HashMap<>();
        saved.getSeats().forEach(seat -> initialStatuses.put(seat.getId(), seat.getStatus()));
        availabilityCache.initEvent(saved.getId(), initialStatuses);

        return saved;
    }

    public List<SeatAvailabilityResponse> getAvailability(Long eventId) {
        return availabilityCache.snapshot(eventId).entrySet().stream()
                .map(e -> new SeatAvailabilityResponse(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Cache-aside: on a miss this method runs and Spring stores the result
     * in Redis under cache "events" / key "all"; on a hit the method body
     * (and the DB query inside it) never runs at all.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "events", key = "'all'")
    public List<EventResponse> listEventResponses() {
        return eventRepository.findAll().stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "event", key = "#id")
    public EventResponse getEventResponse(Long id) {
        return EventResponse.from(getEvent(id));
    }

    @Transactional(readOnly = true)
    public Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + id));
    }
}
