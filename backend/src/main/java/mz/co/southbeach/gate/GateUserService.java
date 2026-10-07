package mz.co.southbeach.gate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mz.co.southbeach.tickets.repository.EventRepository;
import mz.co.southbeach.tickets.service.TicketNotFoundException;
import mz.co.southbeach.tickets.service.TicketRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class GateUserService {
    public record CreateRequest(@NotBlank @Size(max = 80) String label, Long eventId, Instant validUntil) { }
    public record UpdateRequest(@NotBlank @Size(max = 80) String label, Long eventId, Instant validUntil, Boolean active) { }

    /** {@code password} is only filled in when an account is created or its code is renewed: it is never stored in clear. */
    public record UserResponse(Long id, String username, String label, Long eventId, String eventTitle, boolean active, boolean usable,
                               Instant validUntil, Instant lastUsedAt, Instant createdAt, String password) { }

    // No 0/O/1/I/L: a code read out loud or typed on a phone must be hard to get wrong.
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GateUserRepository users;
    private final EventRepository events;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public GateUserService(GateUserRepository users, EventRepository events, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.events = events;
        this.encoder = encoder;
        this.clock = clock;
    }

    private static String random(int length) {
        var sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private UserResponse response(GateUser u, String password) {
        var title = u.getEventId() == null ? null : events.findById(u.getEventId()).map(e -> e.getTitle()).orElse(null);
        return new UserResponse(u.getId(), u.getUsername(), u.getLabel(), u.getEventId(), title, u.isActive(), u.usableAt(clock.instant()),
                u.getValidUntil(), u.getLastUsedAt(), u.getCreatedAt(), password);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByCreatedAtDescIdDesc().stream().map(u -> response(u, null)).toList();
    }

    @Transactional
    public UserResponse create(CreateRequest request) {
        checkEvent(request.eventId());
        checkValidity(request.validUntil());
        String username;
        do { username = "porta-" + random(5).toLowerCase(java.util.Locale.ROOT); } while (users.existsByUsername(username));
        var password = random(10);
        var user = users.save(new GateUser(username, request.label().strip(), encoder.encode(password), request.eventId(), request.validUntil(), clock.instant()));
        return response(user, password);
    }

    @Transactional
    public UserResponse update(Long id, UpdateRequest request) {
        var user = users.findById(id).orElseThrow(() -> new TicketNotFoundException("Gate access"));
        checkEvent(request.eventId());
        user.update(request.label().strip(), request.eventId(), request.validUntil(), request.active() == null || request.active());
        return response(user, null);
    }

    /** Issues a new code: the old one stops working at once. */
    @Transactional
    public UserResponse renewCode(Long id) {
        var user = users.findById(id).orElseThrow(() -> new TicketNotFoundException("Gate access"));
        var password = random(10);
        user.setPasswordHash(encoder.encode(password));
        return response(user, password);
    }

    @Transactional
    public void remove(Long id) {
        users.delete(users.findById(id).orElseThrow(() -> new TicketNotFoundException("Gate access")));
    }

    @Transactional
    public void touch(Long id) {
        users.findById(id).ifPresent(u -> u.touch(clock.instant()));
    }

    private void checkEvent(Long eventId) {
        if (eventId != null && !events.existsById(eventId)) throw new TicketRequestException("That event does not exist.");
    }

    private void checkValidity(Instant validUntil) {
        if (validUntil != null && !validUntil.isAfter(clock.instant())) throw new TicketRequestException("The access must end in the future.");
    }
}
