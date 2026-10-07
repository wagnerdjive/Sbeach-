package mz.co.southbeach.gate;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** A signed-in door-staff account: besides the login data it knows the one event it may work on (or null for any published event). */
public class GatePrincipal extends User {
    private final Long gateUserId;
    private final Long eventId;

    public GatePrincipal(Long gateUserId, String username, String passwordHash, boolean enabled, Long eventId, Collection<? extends GrantedAuthority> authorities) {
        super(username, passwordHash, enabled, true, true, true, authorities);
        this.gateUserId = gateUserId;
        this.eventId = eventId;
    }

    public Long gateUserId() { return gateUserId; }
    public Long eventId() { return eventId; }
}
