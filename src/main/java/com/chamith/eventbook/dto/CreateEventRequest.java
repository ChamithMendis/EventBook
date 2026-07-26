package com.chamith.eventbook.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank String name,
        @NotBlank String venue,
        @NotNull @Future Instant eventTime,
        @Min(1) int totalSeats
) {
}
