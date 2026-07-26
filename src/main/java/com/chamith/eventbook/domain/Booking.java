package com.chamith.eventbook.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false, unique = true)
    private Seat seat;

//    @Column(name = "seat_id", nullable = false)
//    private Long seatId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt = Instant.now();

    protected Booking() {
    }

    public Booking(Seat seat, String userId) {
        this.seat = seat;
        this.userId = userId;
    }

//    public Booking(Long seatId, String userId) {
//        this.seatId = seatId;
//        this.userId = userId;
//    }

    public Long getId() {
        return id;
    }

    public Seat getSeat() {
        return seat;
    }

//    public Long getSeatId() {
//        return seatId;
//    }

    public String getUserId() {
        return userId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }
}
