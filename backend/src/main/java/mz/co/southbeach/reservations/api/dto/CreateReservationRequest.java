package mz.co.southbeach.reservations.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mz.co.southbeach.reservations.domain.ReservationVenue;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateReservationRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[+0-9()\\s.-]{7,30}$") String phone,
        @Size(max = 120) @Email String email,
        @NotNull LocalDate requestedDate,
        @NotNull LocalTime requestedTime,
        @NotNull @Min(1) @Max(100) Integer partySize,
        @NotNull ReservationVenue venue,
        @Size(max = 100) String occasion,
        @Size(max = 1000) String notes
) { }
