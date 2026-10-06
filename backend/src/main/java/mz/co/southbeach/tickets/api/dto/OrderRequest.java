package mz.co.southbeach.tickets.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderRequest(
        @NotBlank @Size(max = 80) String eventSlug,
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[+0-9()\\s.-]{7,30}$") String phone,
        @Size(max = 120) @Email String email,
        @NotEmpty @Size(max = 10) List<@Valid Item> items
) {
    public record Item(@NotNull Long ticketTypeId, @NotNull @Min(1) @Max(50) Integer quantity) { }
}
