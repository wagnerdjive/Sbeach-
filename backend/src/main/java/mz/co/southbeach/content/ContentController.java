package mz.co.southbeach.content;

import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@RestController
public class ContentController {
    public record SaveRequest(@NotNull Map<String, ContentService.Entry> entries) { }

    private final ContentService service;

    public ContentController(ContentService service) { this.service = service; }

    /** Overrides for the website's text, images and links. Keys that are absent keep the page's own content. */
    @GetMapping("/api/content")
    public ResponseEntity<Map<String, ContentService.Entry>> content() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()).body(service.all());
    }

    @PutMapping("/api/admin/content")
    public Map<String, ContentService.Entry> save(@org.springframework.validation.annotation.Validated @RequestBody SaveRequest request) {
        service.save(request.entries());
        return service.all();
    }

    @PostMapping(path = "/api/admin/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(@RequestPart("file") MultipartFile file) throws IOException {
        return Map.of("url", "/api/media/" + service.storeImage(file.getBytes()));
    }

    @GetMapping("/api/media/{id}")
    public ResponseEntity<byte[]> media(@PathVariable Long id) {
        var file = service.image(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable()).body(file.getData());
    }
}
