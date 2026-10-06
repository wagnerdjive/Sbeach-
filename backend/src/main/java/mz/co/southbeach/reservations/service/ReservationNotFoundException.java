package mz.co.southbeach.reservations.service;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String reference) {
        super("Reservation " + reference + " was not found.");
    }
}
