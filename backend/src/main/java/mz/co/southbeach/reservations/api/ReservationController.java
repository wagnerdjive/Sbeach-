package mz.co.southbeach.reservations.api;

import jakarta.validation.Valid;
import mz.co.southbeach.reservations.api.dto.CreateReservationRequest;
import mz.co.southbeach.reservations.api.dto.CreatedReservationResponse;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import mz.co.southbeach.reservations.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReservationController {
    private final ReservationService service;
    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping("/reservations")
    public ResponseEntity<CreatedReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        var reservation = service.create(request);
        var response = new CreatedReservationResponse(reservation.getReference(), reservation.getStatus(), reservation.getCreatedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "south-beach-reservations");
    }
}
