package mz.co.southbeach.tickets.repository;

import mz.co.southbeach.tickets.domain.EventPoster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventPosterRepository extends JpaRepository<EventPoster, Long> { }
