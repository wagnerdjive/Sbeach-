package mz.co.southbeach.reservations.service;

import mz.co.southbeach.reservations.api.dto.CreateReservationRequest;
import mz.co.southbeach.reservations.api.dto.UpdateReservationStatusRequest;
import mz.co.southbeach.reservations.domain.Reservation;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import mz.co.southbeach.reservations.domain.ReservationVenue;
import mz.co.southbeach.reservations.repository.ReservationRepository;
import mz.co.southbeach.reservations.repository.VenueCapacityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class ReservationService {
    private static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");

    private final ReservationRepository repository;
    private final VenueCapacityRepository capacities;
    private final Clock clock;
    private final int durationMinutes;

    public ReservationService(ReservationRepository repository, VenueCapacityRepository capacities, Clock clock,
                              @Value("${app.reservations.duration-minutes}") int durationMinutes) {
        this.repository = repository;
        this.capacities = capacities;
        this.clock = clock;
        this.durationMinutes = durationMinutes;
    }

    @Transactional
    public Reservation create(CreateReservationRequest request) {
        LocalDate todayInMaputo = LocalDate.now(clock.withZone(MAPUTO));
        if (request.requestedDate().isBefore(todayInMaputo)) {
            throw new ReservationDateInPastException();
        }
        var now = clock.instant();
        var reference = "SB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        var reservation = new Reservation(
                reference, request.fullName().trim(), request.phone().trim(), request.requestedDate(),
                request.requestedTime(), request.partySize(), request.venue(), clean(request.occasion()),
                clean(request.notes()), now
        );
        return repository.save(reservation);
    }

    @Transactional(readOnly = true)
    public Page<Reservation> list(ReservationStatus status, Pageable pageable) {
        return status == null ? repository.findAllByOrderByCreatedAtDesc(pageable) : repository.findByStatus(status, pageable);
    }

    @Transactional
    public Reservation changeStatus(String reference, UpdateReservationStatusRequest request) {
        var reservation = repository.findByReference(reference)
                .orElseThrow(() -> new ReservationNotFoundException(reference));
        if (request.status() == ReservationStatus.CONFIRMED && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            confirmWithinCapacity(reservation, request.venue());
        }
        reservation.changeStatus(request.status(), clock.instant());
        return reservation;
    }

    /** Locks the venue's capacity row, so two concurrent confirmations cannot both take the last seats. */
    private void confirmWithinCapacity(Reservation reservation, ReservationVenue chosenVenue) {
        var venue = reservation.getVenue() == ReservationVenue.NO_PREFERENCE ? chosenVenue : reservation.getVenue();
        if (venue == null || venue == ReservationVenue.NO_PREFERENCE) throw new ReservationVenueRequiredException();
        var capacity = capacities.findForUpdate(venue).orElseThrow(ReservationVenueRequiredException::new).getCapacity();
        var time = reservation.getRequestedTime();
        // Bookings overlap when their start times are less than one duration apart.
        var window = durationMinutes - 1;
        var from = time.minusMinutes(window).isAfter(time) ? LocalTime.MIN : time.minusMinutes(window);
        var to = time.plusMinutes(window).isBefore(time) ? LocalTime.MAX : time.plusMinutes(window);
        var taken = repository.confirmedSeatsBetween(venue, reservation.getRequestedDate(), from, to, reservation.getId());
        var remaining = capacity - taken - reservation.getPartySize();
        if (remaining < 0) throw new ReservationCapacityExceededException(capacity - (int) taken);
        reservation.assignVenue(venue);
    }

    private String clean(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
