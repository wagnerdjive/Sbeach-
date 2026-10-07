package mz.co.southbeach.tickets.notification;

import mz.co.southbeach.reservations.notification.SmsGateway;
import mz.co.southbeach.tickets.service.OrderPaid;
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

/** Sends the link to the customer's tickets once the payment is recorded. Failures never undo the payment. */
@Component
public class TicketNotifier {
    private static final Logger log = LoggerFactory.getLogger(TicketNotifier.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectProvider<SmsGateway> sms;
    private final boolean emailEnabled;
    private final String from;
    private final String siteUrl;

    public TicketNotifier(ObjectProvider<JavaMailSender> mailSender, ObjectProvider<SmsGateway> sms,
                          @Value("${app.notifications.email.enabled}") boolean emailEnabled,
                          @Value("${app.notifications.email.from}") String from,
                          @Value("${app.site-url}") String siteUrl) {
        this.mailSender = mailSender;
        this.sms = sms;
        this.emailEnabled = emailEnabled;
        this.from = from;
        this.siteUrl = siteUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaid(OrderPaid event) {
        var order = event.order();
        var text = "South Beach: pagamento recebido para a encomenda " + order.getReference()
                + ". Os seus bilhetes (QR) estão em " + ticketsUrl(siteUrl, order.getAccessToken()) + " — mostre o QR à entrada.";
        if (emailEnabled && order.getEmail() != null && mailSender.getIfAvailable() != null) {
            try {
                var message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(order.getEmail());
                message.setSubject("Os seus bilhetes — South Beach");
                message.setText(text);
                mailSender.getObject().send(message);
            } catch (RuntimeException exception) {
                log.warn("Ticket email for order {} failed: {}", order.getReference(), exception.getClass().getSimpleName());
            }
        }
        var gateway = sms.getIfAvailable();
        if (gateway == null) return;
        try {
            gateway.send(order.getPhone(), text);
        } catch (RuntimeException exception) {
            log.warn("Ticket SMS for order {} failed: {}", order.getReference(), exception.getClass().getSimpleName());
        }
    }

    public static String ticketsUrl(String siteUrl, String accessToken) {
        return siteUrl.replaceAll("/+$", "") + "/ticket.html?t=" + accessToken;
    }
}
