package net.bp4k.locationlens.reviews;

import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/places/{placeId}/reviews")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Review> create(@PathVariable String placeId,
                                        @Valid @RequestBody CreateReviewRequest request,
                                        Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to create a review");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(placeId, principal.getName(), request));
    }
}
