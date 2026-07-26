package com.chamith.eventbook.dto;

import com.chamith.eventbook.domain.SeatStatus;

public record SeatAvailabilityResponse(Long seatId, SeatStatus status) {
}
