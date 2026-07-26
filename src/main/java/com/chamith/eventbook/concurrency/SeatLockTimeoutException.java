package com.chamith.eventbook.concurrency;

/**
 * Thrown when we couldn't acquire a seat's lock within the wait timeout —
 * someone else is actively booking (or holding) that seat right now.
 * Distinct from "already booked": this is "try again in a moment", not
 * "this seat is gone".
 */
public class SeatLockTimeoutException extends RuntimeException {

    public SeatLockTimeoutException(String message) {
        super(message);
    }
}
