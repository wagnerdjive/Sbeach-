package mz.co.southbeach.reservations.notification;

import mz.co.southbeach.reservations.domain.Reservation;
import mz.co.southbeach.reservations.domain.ReservationVenue;

import java.time.format.DateTimeFormatter;

/** Customer-facing text (Portuguese). Kept free of notes and contact data. */
public final class ReservationMessages {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ReservationMessages() { }

    public static String venue(ReservationVenue venue) {
        return switch (venue) {
            case RESTAURANT -> "Restaurante";
            case BEACH_BAR -> "Beach Bar";
            case SPORTS_BAR -> "Sports Bar";
            case NO_PREFERENCE -> "South Beach";
        };
    }

    public static String when(Reservation r) {
        return r.getRequestedDate().format(DATE) + " às " + r.getRequestedTime().format(TIME)
                + " (" + r.getPartySize() + " pessoa" + (r.getPartySize() == 1 ? "" : "s") + ", " + venue(r.getVenue()) + ")";
    }

    public static String confirmed(Reservation r) {
        return "South Beach: reserva " + r.getReference() + " confirmada para " + when(r) + ". Até breve!";
    }

    public static String cancelled(Reservation r) {
        return "South Beach: a reserva " + r.getReference() + " (" + r.getRequestedDate().format(DATE)
                + ") foi cancelada. Para voltar a reservar ligue (+258) 82 325 5120.";
    }

    public static String reminder(Reservation r) {
        return "South Beach: lembrete da sua reserva " + r.getReference() + " amanhã, " + when(r)
                + ". Para alterar ou cancelar ligue (+258) 82 325 5120.";
    }
}
