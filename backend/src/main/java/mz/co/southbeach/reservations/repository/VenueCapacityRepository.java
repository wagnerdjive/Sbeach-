package mz.co.southbeach.reservations.repository;

import jakarta.persistence.LockModeType;
import mz.co.southbeach.reservations.domain.ReservationVenue;
import mz.co.southbeach.reservations.domain.VenueCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface VenueCapacityRepository extends JpaRepository<VenueCapacity, ReservationVenue> {
    /** Row lock that serialises confirmations for one venue so capacity checks cannot race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VenueCapacity v where v.venue = :venue")
    Optional<VenueCapacity> findForUpdate(ReservationVenue venue);
}
