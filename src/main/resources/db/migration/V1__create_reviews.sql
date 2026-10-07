CREATE TABLE reviews (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    place_id VARCHAR(255) NOT NULL,
    author_id VARCHAR(255) NOT NULL,
    rating INTEGER NOT NULL,
    review_text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_reviews_place_author UNIQUE (place_id, author_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ${reviewTableOptions};
