package mz.co.southbeach.reservations.api;

import jakarta.validation.Valid;
import mz.co.southbeach.reservations.api.dto.AdminReservationResponse;
import mz.co.southbeach.reservations.api.dto.UpdateReservationStatusRequest;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import mz.co.southbeach.reservations.service.ReservationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationController {
    private final ReservationService service;

    public AdminReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AdminReservationResponse> list(
            @RequestParam(required = false) ReservationStatus status,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var safePage = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return service.list(status, safePage).map(AdminReservationResponse::from);
    }

    @PatchMapping("/{reference}/status")
    public ResponseEntity<AdminReservationResponse> updateStatus(
            @PathVariable String reference,
            @Valid @RequestBody UpdateReservationStatusRequest request) {
        return ResponseEntity.ok(AdminReservationResponse.from(service.changeStatus(reference, request)));
    }
}
