package mz.co.southbeach.tickets.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mz.co.southbeach.tickets.service.EntryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminEntryController {
    public record CheckInRequest(@NotNull Long eventId, @NotBlank @Size(max = 64) String code) { }

    private final EntryService entry;

    public AdminEntryController(EntryService entry) { this.entry = entry; }

    /** Always answers 200 with an {@code outcome}, so the scanner can show why a ticket was refused. */
    @PostMapping("/check-in")
    public EntryService.Result checkIn(@Valid @RequestBody CheckInRequest request) {
        return entry.checkIn(request.eventId(), request.code());
    }

    /** Reads a ticket without using it, for "verify only" scanning. */
    @PostMapping("/check-in/peek")
    public EntryService.Result peek(@Valid @RequestBody CheckInRequest request) {
        return entry.peek(request.eventId(), request.code());
    }

    @PostMapping("/check-in/undo")
    public EntryService.Result undo(@Valid @RequestBody CheckInRequest request) {
        return entry.undo(request.eventId(), request.code());
    }

    @GetMapping("/events/{id}/entry-recent")
    public java.util.List<EntryService.Recent> recent(@PathVariable Long id) { return entry.recent(id); }

    @GetMapping("/events/{id}/entry-stats")
    public EntryService.Stats stats(@PathVariable Long id) { return entry.stats(id); }
}
