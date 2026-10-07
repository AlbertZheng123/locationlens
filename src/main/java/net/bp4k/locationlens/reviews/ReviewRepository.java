package net.bp4k.locationlens.reviews;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRepository {
    private final JdbcTemplate jdbc;

    public ReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Review review) {
        jdbc.update("""
                INSERT INTO reviews (id, place_id, author_id, rating, review_text, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, review.id(), review.placeId(), review.authorId(), review.rating(),
                review.text(), Timestamp.from(review.createdAt()), Timestamp.from(review.updatedAt()));
    }
}
