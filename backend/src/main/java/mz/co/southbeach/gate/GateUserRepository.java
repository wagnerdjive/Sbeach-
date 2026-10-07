package mz.co.southbeach.gate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GateUserRepository extends JpaRepository<GateUser, Long> {
    Optional<GateUser> findByUsername(String username);
    List<GateUser> findAllByOrderByCreatedAtDescIdDesc();
    boolean existsByUsername(String username);
}
