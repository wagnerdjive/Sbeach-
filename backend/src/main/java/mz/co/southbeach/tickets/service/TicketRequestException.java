package mz.co.southbeach.tickets.service;

/** The request is well-formed but breaks a ticketing rule (400 with the message). */
public class TicketRequestException extends RuntimeException {
    public TicketRequestException(String message) { super(message); }
}
