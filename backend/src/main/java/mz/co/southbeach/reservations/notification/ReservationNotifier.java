package mz.co.southbeach.reservations.notification;

import mz.co.southbeach.reservations.domain.Reservation;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import mz.co.southbeach.reservations.service.ReservationStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends customer messages after the status change is committed; a failure never affects the reservation. */
@Component
public class ReservationNotifier {
    private static final Logger log = LoggerFactory.getLogger(ReservationNotifier.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectProvider<SmsGateway> sms;
    private final boolean emailEnabled;
    private final String from;

    public ReservationNotifier(ObjectProvider<JavaMailSender> mailSender, ObjectProvider<SmsGateway> sms,
                               @Value("${app.notifications.email.enabled}") boolean emailEnabled,
                               @Value("${app.notifications.email.from}") String from) {
        this.mailSender = mailSender;
        this.sms = sms;
        this.emailEnabled = emailEnabled;
        this.from = from;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ReservationStatusChanged event) {
        var reservation = event.reservation();
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            send(reservation, "Reserva confirmada — South Beach", ReservationMessages.confirmed(reservation));
        } else if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            send(reservation, "Reserva cancelada — South Beach", ReservationMessages.cancelled(reservation));
        }
    }

    /** Returns true when at least one channel accepted the reminder. */
    public boolean sendReminder(Reservation reservation) {
        return send(reservation, "Lembrete da sua reserva — South Beach", ReservationMessages.reminder(reservation));
    }

    private boolean send(Reservation reservation, String subject, String text) {
        boolean sent = false;
        if (emailEnabled && reservation.getEmail() != null && mailSender.getIfAvailable() != null) {
            try {
                var message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(reservation.getEmail());
                message.setSubject(subject);
                message.setText(text);
                mailSender.getObject().send(message);
                sent = true;
            } catch (RuntimeException exception) {
                log.warn("Email for reservation {} failed: {}", reservation.getReference(), exception.getClass().getSimpleName());
            }
        }
        var gateway = sms.getIfAvailable();
        if (gateway == null) {
            log.info("SMS gateway not configured; message for reservation {} was not sent by SMS.", reservation.getReference());
            return sent;
        }
        try {
            gateway.send(reservation.getPhone(), text);
            sent = true;
        } catch (RuntimeException exception) {
            log.warn("SMS for reservation {} failed: {}", reservation.getReference(), exception.getClass().getSimpleName());
        }
        return sent;
    }
}
