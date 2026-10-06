package mz.co.southbeach.reservations.repository;

import mz.co.southbeach.reservations.domain.Reservation;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import mz.co.southbeach.reservations.domain.ReservationVenue;

import java.time.LocalDate;
import java.time.LocalTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);
    Page<Reservation> findAllByOrderByCreatedAtDesc(Pageable pageable);
    java.util.List<Reservation> findByStatusAndRequestedDateAndReminderSentAtIsNull(ReservationStatus status, LocalDate date);
    java.util.Optional<Reservation> findByReference(String reference);

    @Query("""
            select coalesce(sum(r.partySize), 0) from Reservation r
            where r.status = mz.co.southbeach.reservations.domain.ReservationStatus.CONFIRMED
              and r.venue = :venue and r.requestedDate = :date
              and r.requestedTime >= :from and r.requestedTime <= :to
              and r.id <> :excludeId
            """)
    long confirmedSeatsBetween(ReservationVenue venue, LocalDate date, LocalTime from, LocalTime to, Long excludeId);
}
