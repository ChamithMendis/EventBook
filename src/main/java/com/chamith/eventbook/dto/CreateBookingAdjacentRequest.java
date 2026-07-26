package com.chamith.eventbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateBookingAdjacentRequest(
        @NotNull List<Long> seatIds,
        @NotBlank String userId
) {
}
