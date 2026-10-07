package mz.co.southbeach.gate;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Clock;
import java.util.List;

/** Looks door-staff accounts up in the database; an inactive or expired one behaves like a disabled account (401). */
public class GateUserDetailsService implements UserDetailsService {
    private final GateUserRepository users;
    private final Clock clock;

    public GateUserDetailsService(GateUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return new GatePrincipal(user.getId(), user.getUsername(), user.getPasswordHash(), user.usableAt(clock.instant()), user.getEventId(),
                List.of(new SimpleGrantedAuthority("ROLE_GATE")));
    }
}
