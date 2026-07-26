package com.chamith.eventbook.dto;

import com.chamith.eventbook.domain.Event;

import java.time.Instant;

public record EventResponse(Long id, String name, String venue, Instant eventTime, int totalSeats) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getVenue(),
                event.getEventTime(),
                event.getSeats().size()
        );
    }
}
