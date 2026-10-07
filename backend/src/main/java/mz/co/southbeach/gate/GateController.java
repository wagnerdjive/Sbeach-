package mz.co.southbeach.gate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mz.co.southbeach.tickets.domain.Event;
import mz.co.southbeach.tickets.domain.EventStatus;
import mz.co.southbeach.tickets.repository.EventRepository;
import mz.co.southbeach.tickets.service.EntryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** The only part of the API door staff can reach: reading and admitting tickets. Admins may use it too. */
@RestController
@RequestMapping("/api/gate")
public class GateController {
    public record CheckInRequest(@NotNull Long eventId, @NotBlank @Size(max = 64) String code) { }
    public record GateEvent(Long id, String title, Instant startsAt, Instant endsAt, String location) { }

    private final EntryService entry;
    private final EventRepository events;
    private final GateUserService gateUsers;

    public GateController(EntryService entry, EventRepository events, GateUserService gateUsers) {
        this.entry = entry;
        this.events = events;
        this.gateUsers = gateUsers;
    }

    /** An account tied to one event may only touch that event. */
    private void allow(Authentication auth, Long eventId) {
        if (auth.getPrincipal() instanceof GatePrincipal gate) {
            if (gate.eventId() != null && !gate.eventId().equals(eventId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This access is not valid for that event.");
            }
            gateUsers.touch(gate.gateUserId());
        }
    }

    @GetMapping("/events")
    public List<GateEvent> events(Authentication auth) {
        boolean admin = !(auth.getPrincipal() instanceof GatePrincipal);
        Long only = auth.getPrincipal() instanceof GatePrincipal gate ? gate.eventId() : null;
        return events.findAll().stream()
                .filter(e -> admin ? e.getStatus() != EventStatus.CANCELLED : e.getStatus() == EventStatus.PUBLISHED)
                .filter(e -> only == null || only.equals(e.getId()))
                .sorted(java.util.Comparator.comparing(Event::getStartsAt))
                .map(e -> new GateEvent(e.getId(), e.getTitle(), e.getStartsAt(), e.getEndsAt(), e.getLocation())).toList();
    }

    @PostMapping("/check-in/peek")
    public EntryService.Result peek(@Valid @RequestBody CheckInRequest request, Authentication auth) {
        allow(auth, request.eventId());
        return entry.peek(request.eventId(), request.code());
    }

    @PostMapping("/check-in")
    public EntryService.Result checkIn(@Valid @RequestBody CheckInRequest request, Authentication auth) {
        allow(auth, request.eventId());
        return entry.checkIn(request.eventId(), request.code());
    }

    @PostMapping("/check-in/undo")
    public EntryService.Result undo(@Valid @RequestBody CheckInRequest request, Authentication auth) {
        allow(auth, request.eventId());
        return entry.undo(request.eventId(), request.code());
    }

    @GetMapping("/events/{id}/entry-stats")
    public EntryService.Stats stats(@PathVariable Long id, Authentication auth) {
        allow(auth, id);
        return entry.stats(id);
    }

    @GetMapping("/events/{id}/entry-recent")
    public List<EntryService.Recent> recent(@PathVariable Long id, Authentication auth) {
        allow(auth, id);
        return entry.recent(id);
    }
}
