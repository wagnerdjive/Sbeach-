package mz.co.southbeach.reservations.notification;

import mz.co.southbeach.reservations.domain.ReservationStatus;
import mz.co.southbeach.reservations.repository.ReservationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** Sends one reminder the day before each confirmed reservation. */
@Component
@ConditionalOnProperty(name = "app.notifications.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {
    private final ReservationRepository repository;
    private final ReservationNotifier notifier;
    private final Clock clock;

    public ReminderScheduler(ReservationRepository repository, ReservationNotifier notifier, Clock clock) {
        this.repository = repository;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notifications.reminders.cron}", zone = "Africa/Maputo")
    @Transactional
    public int sendDueReminders() {
        var tomorrow = LocalDate.now(clock).plusDays(1);
        int sent = 0;
        for (var reservation : repository.findByStatusAndRequestedDateAndReminderSentAtIsNull(ReservationStatus.CONFIRMED, tomorrow)) {
            if (notifier.sendReminder(reservation)) {
                reservation.markReminderSent(clock.instant());
                sent++;
            }
        }
        return sent;
    }
}
