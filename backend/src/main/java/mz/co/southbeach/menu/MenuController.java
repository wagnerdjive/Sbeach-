package mz.co.southbeach.menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
public class MenuController {
    public record OrderRequest(@NotNull List<Long> ids) { }

    private final MenuService service;

    public MenuController(MenuService service) { this.service = service; }

    @GetMapping("/api/menu")
    public ResponseEntity<List<MenuService.ItemResponse>> published() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()).body(service.published());
    }

    @GetMapping("/api/admin/menu")
    public List<MenuService.ItemResponse> all() { return service.all(); }

    @PostMapping("/api/admin/menu")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuService.ItemResponse add(@Valid @RequestBody MenuService.ItemRequest request) { return service.add(request); }

    @PutMapping("/api/admin/menu/{id}")
    public MenuService.ItemResponse change(@PathVariable Long id, @Valid @RequestBody MenuService.ItemRequest request) {
        return service.change(id, request);
    }

    @DeleteMapping("/api/admin/menu/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) { service.remove(id); }

    @PutMapping("/api/admin/menu-order")
    public List<MenuService.ItemResponse> reorder(@Valid @RequestBody OrderRequest request) { return service.reorder(request.ids()); }
}
