package com.chamith.eventbook.repository;

import com.chamith.eventbook.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}
