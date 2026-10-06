package mz.co.southbeach.tickets.service;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String what) { super(what + " not found."); }
}
