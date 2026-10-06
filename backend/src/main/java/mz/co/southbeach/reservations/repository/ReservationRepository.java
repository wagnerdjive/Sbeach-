package mz.co.southbeach.reservations.repository;

import mz.co.southbeach.reservations.domain.Reservation;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);
    Page<Reservation> findAllByOrderByCreatedAtDesc(Pageable pageable);
    java.util.Optional<Reservation> findByReference(String reference);
}
