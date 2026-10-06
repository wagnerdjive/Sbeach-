package mz.co.southbeach.reservations.service;

public class ReservationCapacityExceededException extends RuntimeException {
    public ReservationCapacityExceededException(int remaining) {
        super("Not enough capacity for this slot. Remaining seats: " + Math.max(remaining, 0) + ".");
    }
}
