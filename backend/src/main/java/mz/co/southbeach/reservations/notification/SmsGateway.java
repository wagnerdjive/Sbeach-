package mz.co.southbeach.reservations.notification;

/** Implement this and register it as a bean to send real SMS; without one, SMS is skipped. */
public interface SmsGateway {
    void send(String phone, String text);

    /** Whether {@link #sendDocument} really delivers files (WhatsApp does; plain SMS does not). */
    default boolean supportsDocuments() { return false; }

    /** Sends a PDF (or other file) with a short caption. Channels that cannot carry files simply ignore it. */
    default void sendDocument(String phone, String filename, String caption, byte[] content) { }
}
