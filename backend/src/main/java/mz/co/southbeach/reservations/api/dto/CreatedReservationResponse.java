package mz.co.southbeach.reservations.api.dto;

import mz.co.southbeach.reservations.domain.ReservationStatus;

import java.time.Instant;

public record CreatedReservationResponse(String reference, ReservationStatus status, Instant createdAt) { }
