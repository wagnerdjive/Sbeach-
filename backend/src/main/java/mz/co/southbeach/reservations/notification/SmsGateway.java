package mz.co.southbeach.reservations.notification;

/** Implement this and register it as a bean to send real SMS; without one, SMS is skipped. */
public interface SmsGateway {
    void send(String phone, String text);
}
