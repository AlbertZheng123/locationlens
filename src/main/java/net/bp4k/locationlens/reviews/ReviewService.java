package net.bp4k.locationlens.reviews;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {
    private final ReviewRepository repository;

    public ReviewService(ReviewRepository repository) {
        this.repository = repository;
    }

    public Review create(String placeId, String authorId, CreateReviewRequest request) {
        if (placeId.isBlank() || placeId.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Place ID must contain 1 to 255 characters");
        }
        if (authorId == null || authorId.isBlank() || authorId.length() > 255) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid authenticated user is required");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Review review = new Review(UUID.randomUUID().toString(), placeId, authorId,
                request.rating(), request.text().strip(), now, now);
        try {
            repository.insert(review);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this place");
        }
        return review;
    }
}
