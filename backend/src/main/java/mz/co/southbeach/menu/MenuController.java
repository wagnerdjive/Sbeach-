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
    public record GroupOrderRequest(@NotNull MenuItem.Venue venue, @NotNull List<Long> ids) { }

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

    @GetMapping("/api/admin/menu-groups")
    public List<MenuService.GroupResponse> groups() { return service.allGroups(); }

    @PostMapping("/api/admin/menu-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuService.GroupResponse addGroup(@Valid @RequestBody MenuService.GroupRequest request) { return service.addGroup(request); }

    @PutMapping("/api/admin/menu-groups/{id}")
    public MenuService.GroupResponse changeGroup(@PathVariable Long id, @Valid @RequestBody MenuService.GroupRequest request) {
        return service.changeGroup(id, request);
    }

    @DeleteMapping("/api/admin/menu-groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeGroup(@PathVariable Long id) { service.removeGroup(id); }

    @PutMapping("/api/admin/menu-group-order")
    public List<MenuService.GroupResponse> reorderGroups(@Valid @RequestBody GroupOrderRequest request) {
        return service.reorderGroups(request.venue(), request.ids());
    }
}
