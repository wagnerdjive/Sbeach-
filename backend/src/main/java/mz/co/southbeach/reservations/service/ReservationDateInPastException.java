package mz.co.southbeach.reservations.service;

public class ReservationDateInPastException extends RuntimeException {
    public ReservationDateInPastException() {
        super("requestedDate must be today or a future date in Africa/Maputo.");
    }
}
