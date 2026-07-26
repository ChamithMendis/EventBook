package com.chamith.eventbook.web;

import com.chamith.eventbook.domain.Event;
import com.chamith.eventbook.dto.CreateEventRequest;
import com.chamith.eventbook.dto.EventResponse;
import com.chamith.eventbook.dto.SeatResponse;
import com.chamith.eventbook.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        return EventResponse.from(eventService.createEvent(request));
    }

    @GetMapping
    public List<EventResponse> listEvents() {
        return eventService.listEventResponses();
    }

    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable Long id) {
        return eventService.getEventResponse(id);
    }

    @GetMapping("/{id}/seats")
    public List<SeatResponse> listSeats(@PathVariable Long id) {
        Event event = eventService.getEvent(id);
        return event.getSeats().stream().map(SeatResponse::from).toList();
    }
}
