package mz.co.southbeach.tickets.repository;

import mz.co.southbeach.tickets.domain.Event;
import mz.co.southbeach.tickets.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStatusOrderByStartsAtAsc(EventStatus status);
    List<Event> findAllByOrderByStartsAtDesc();
    Optional<Event> findBySlugAndStatus(String slug, EventStatus status);
}
