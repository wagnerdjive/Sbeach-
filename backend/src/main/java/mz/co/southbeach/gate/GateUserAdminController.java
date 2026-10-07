package mz.co.southbeach.gate;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin only (the /api/admin/** rule): create, change, renew and remove door-staff accesses. */
@RestController
@RequestMapping("/api/admin/gate-users")
public class GateUserAdminController {
    private final GateUserService service;

    public GateUserAdminController(GateUserService service) { this.service = service; }

    @GetMapping
    public List<GateUserService.UserResponse> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GateUserService.UserResponse create(@Valid @RequestBody GateUserService.CreateRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    public GateUserService.UserResponse update(@PathVariable Long id, @Valid @RequestBody GateUserService.UpdateRequest request) { return service.update(id, request); }

    @PostMapping("/{id}/renew-code")
    public GateUserService.UserResponse renew(@PathVariable Long id) { return service.renewCode(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) { service.remove(id); }
}
