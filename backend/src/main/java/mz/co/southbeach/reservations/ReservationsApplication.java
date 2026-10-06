package mz.co.southbeach.reservations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = "mz.co.southbeach")
@EntityScan("mz.co.southbeach")
@EnableJpaRepositories("mz.co.southbeach")
public class ReservationsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReservationsApplication.class, args);
    }
}
