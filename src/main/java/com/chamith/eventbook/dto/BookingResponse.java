package com.chamith.eventbook.dto;

import com.chamith.eventbook.domain.Booking;

import java.time.Instant;

public record BookingResponse(Long id, Long seatId, String seatNumber, String userId, Instant bookedAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getSeat().getId(),
//                booking.getSeatId(),
                booking.getSeat().getSeatNumber(),
//                booking.getSeatId().toString(),
                booking.getUserId(),
                booking.getBookedAt()
        );
    }
}
