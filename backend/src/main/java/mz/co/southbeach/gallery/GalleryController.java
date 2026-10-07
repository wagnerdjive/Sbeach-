package mz.co.southbeach.gallery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
public class GalleryController {
    public record OrderRequest(@NotNull List<Long> ids) { }

    private final GalleryService service;

    public GalleryController(GalleryService service) { this.service = service; }

    @GetMapping("/api/gallery")
    public ResponseEntity<List<GalleryService.PhotoResponse>> published() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()).body(service.published());
    }

    @GetMapping("/api/admin/gallery")
    public List<GalleryService.PhotoResponse> all() { return service.all(); }

    @PostMapping("/api/admin/gallery")
    @ResponseStatus(HttpStatus.CREATED)
    public GalleryService.PhotoResponse add(@Valid @RequestBody GalleryService.PhotoRequest request) { return service.add(request); }

    @PutMapping("/api/admin/gallery/{id}")
    public GalleryService.PhotoResponse change(@PathVariable Long id, @Valid @RequestBody GalleryService.PhotoRequest request) {
        return service.change(id, request);
    }

    @DeleteMapping("/api/admin/gallery/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) { service.remove(id); }

    @PutMapping("/api/admin/gallery-order")
    public List<GalleryService.PhotoResponse> reorder(@Valid @RequestBody OrderRequest request) { return service.reorder(request.ids()); }
}
