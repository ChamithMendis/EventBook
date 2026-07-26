package com.chamith.eventbook.web;

import com.chamith.eventbook.domain.Booking;
import com.chamith.eventbook.dto.BookingResponse;
import com.chamith.eventbook.dto.CreateBookingAdjacentRequest;
import com.chamith.eventbook.dto.CreateBookingRequest;
import com.chamith.eventbook.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse bookSeat(@Valid @RequestBody CreateBookingRequest request) throws InterruptedException {
        return BookingResponse.from(bookingService.bookSeat(request));
    }

    @PostMapping("/adjacent")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Booking> bookAdjacentSeats(@Valid @RequestBody CreateBookingAdjacentRequest request) throws InterruptedException {
        return bookingService.bookAdjacentSeats(request);
    }
}
