package com.chamith.eventbook.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String venue;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();

    protected Event() {
    }

    public Event(String name, String venue, Instant eventTime) {
        this.name = name;
        this.venue = venue;
        this.eventTime = eventTime;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVenue() {
        return venue;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public List<Seat> getSeats() {
        return seats;
    }
}
