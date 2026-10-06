package mz.co.southbeach.tickets.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** {@code priceMinor} is in centavos of MZN (1500.00 MZN = 150000). */
public record TicketTypeRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @NotNull @Min(0) Long priceMinor,
        @NotNull @Min(1) @Max(100000) Integer capacity,
        Instant saleStartsAt,
        Instant saleEndsAt,
        @NotNull @Min(1) @Max(50) Integer maxPerOrder
) { }
