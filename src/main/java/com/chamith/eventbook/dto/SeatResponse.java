package com.chamith.eventbook.dto;

import com.chamith.eventbook.domain.Seat;
import com.chamith.eventbook.domain.SeatStatus;

public record SeatResponse(Long id, String seatNumber, SeatStatus status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getSeatNumber(), seat.getStatus());
    }
}
