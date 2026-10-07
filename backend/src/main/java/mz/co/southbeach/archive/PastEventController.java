package mz.co.southbeach.archive;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
public class PastEventController {
    public record OrderRequest(@NotNull List<Long> ids) { }

    private final PastEventService service;

    public PastEventController(PastEventService service) { this.service = service; }

    @GetMapping("/api/past-events")
    public ResponseEntity<List<PastEventService.EventResponse>> published() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()).body(service.published());
    }

    @GetMapping("/api/past-events/{slug}")
    public ResponseEntity<PastEventService.EventResponse> published(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()).body(service.publishedBySlug(slug));
    }

    @GetMapping("/api/admin/past-events")
    public List<PastEventService.EventResponse> all() { return service.all(); }

    @GetMapping("/api/admin/past-events/{id}")
    public PastEventService.EventResponse one(@PathVariable Long id) { return service.one(id); }

    @PostMapping("/api/admin/past-events")
    @ResponseStatus(HttpStatus.CREATED)
    public PastEventService.EventResponse add(@Valid @RequestBody PastEventService.EventRequest request) { return service.add(request); }

    @PutMapping("/api/admin/past-events/{id}")
    public PastEventService.EventResponse change(@PathVariable Long id, @Valid @RequestBody PastEventService.EventRequest request) {
        return service.change(id, request);
    }

    @DeleteMapping("/api/admin/past-events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) { service.remove(id); }

    @PutMapping("/api/admin/past-event-order")
    public List<PastEventService.EventResponse> reorder(@Valid @RequestBody OrderRequest request) { return service.reorder(request.ids()); }

    @PostMapping("/api/admin/past-events/{id}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public PastEventService.PhotoResponse addPhoto(@PathVariable Long id, @Valid @RequestBody PastEventService.PhotoRequest request) {
        return service.addPhoto(id, request);
    }

    @DeleteMapping("/api/admin/past-event-photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto(@PathVariable Long photoId) { service.removePhoto(photoId); }

    @PutMapping("/api/admin/past-events/{id}/photo-order")
    public List<PastEventService.PhotoResponse> reorderPhotos(@PathVariable Long id, @Valid @RequestBody OrderRequest request) {
        return service.reorderPhotos(id, request.ids());
    }
}
