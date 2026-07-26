package com.chamith.eventbook.repository;

import com.chamith.eventbook.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
