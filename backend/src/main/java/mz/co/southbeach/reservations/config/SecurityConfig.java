package mz.co.southbeach.reservations.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservations").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*", "/api/events/*/poster").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tickets/*", "/api/tickets/qr/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/content", "/api/media/*", "/api/gallery", "/api/menu", "/api/past-events", "/api/past-events/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers("/api/gate/**").hasAnyRole("GATE", "ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** One login service for both kinds of staff: the admin from the environment, then door-staff accounts from the database. */
    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder encoder,
            mz.co.southbeach.gate.GateUserRepository gateUsers,
            java.time.Clock clock,
            @Value("${app.security.admin-username}") String username,
            @Value("${app.security.admin-password}") String password) {
        if (password == null || password.length() < 16) {
            throw new IllegalStateException("Set APP_ADMIN_PASSWORD to a unique password with at least 16 characters.");
        }
        var admin = new InMemoryUserDetailsManager(User.withUsername(username).password(encoder.encode(password)).roles("ADMIN").build());
        var gate = new mz.co.southbeach.gate.GateUserDetailsService(gateUsers, clock);
        return name -> {
            try { return admin.loadUserByUsername(name); }
            catch (org.springframework.security.core.userdetails.UsernameNotFoundException notAdmin) { return gate.loadUserByUsername(name); }
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
