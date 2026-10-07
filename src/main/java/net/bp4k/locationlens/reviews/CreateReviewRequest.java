package net.bp4k.locationlens.reviews;

import jakarta.validation.constraints.*;

public record CreateReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank @Size(max = 2000) String text) {
}
