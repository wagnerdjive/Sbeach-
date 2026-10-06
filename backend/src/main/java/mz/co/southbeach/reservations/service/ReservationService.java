package mz.co.southbeach.reservations.service;

import mz.co.southbeach.reservations.api.dto.CreateReservationRequest;
import mz.co.southbeach.reservations.api.dto.UpdateReservationStatusRequest;
import mz.co.southbeach.reservations.domain.Reservation;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import mz.co.southbeach.reservations.repository.ReservationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class ReservationService {
    private static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");

    private final ReservationRepository repository;
    private final Clock clock;

    public ReservationService(ReservationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
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
        reservation.changeStatus(request.status(), clock.instant());
        return reservation;
    }

    private String clean(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
