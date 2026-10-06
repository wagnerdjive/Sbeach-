package mz.co.southbeach.tickets.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mz.co.southbeach.tickets.domain.EventStatus;

import java.time.Instant;

public record EventRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "use lowercase letters, digits and hyphens") String slug,
        @NotBlank @Size(max = 150) String title,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 150) String location,
        @NotNull Instant startsAt,
        Instant endsAt,
        @NotNull EventStatus status
) { }
