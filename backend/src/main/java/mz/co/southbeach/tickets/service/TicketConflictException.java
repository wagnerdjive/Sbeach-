package mz.co.southbeach.tickets.service;

/** The current state forbids the request, for example sold-out stock (409). */
public class TicketConflictException extends IllegalStateException {
    public TicketConflictException(String message) { super(message); }
}
