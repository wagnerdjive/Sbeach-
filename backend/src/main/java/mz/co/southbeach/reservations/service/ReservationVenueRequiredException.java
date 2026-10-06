package mz.co.southbeach.reservations.service;

public class ReservationVenueRequiredException extends RuntimeException {
    public ReservationVenueRequiredException() {
        super("Choose a specific venue to confirm a reservation with no venue preference.");
    }
}
