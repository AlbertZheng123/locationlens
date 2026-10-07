package net.bp4k.locationlens.reviews;

import java.time.Instant;

public record Review(String id, String placeId, String authorId, int rating,
                     String text, Instant createdAt, Instant updatedAt) {
}
