package net.bp4k.locationlens.reviews;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewCreationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearReviews() {
        jdbc.update("DELETE FROM reviews");
    }

    @Test
    void createsAndPersistsReviewWithAuthenticatedAuthor() throws Exception {
        mvc.perform(post("/api/places/google-place-123/reviews").with(user("alice")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"text\":\"  Great place!  \",\"authorId\":\"mallory\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.placeId").value("google-place-123"))
                .andExpect(jsonPath("$.authorId").value("alice"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.text").value("Great place!"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        assertThat(jdbc.queryForMap("SELECT * FROM reviews"))
                .containsEntry("AUTHOR_ID", "alice").containsEntry("REVIEW_TEXT", "Great place!");
    }

    @Test
    void rejectsDuplicateButAllowsDifferentAuthorsAndPlaces() throws Exception {
        submit("place-one", "alice", 201);
        submit("place-one", "alice", 409);
        submit("place-one", "bob", 201);
        submit("place-two", "alice", 201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class)).isEqualTo(3);
    }

    @Test
    void validatesRatingAndText() throws Exception {
        String[] invalid = {
                "{\"rating\":0,\"text\":\"Good\"}",
                "{\"rating\":6,\"text\":\"Good\"}",
                "{\"text\":\"Good\"}",
                "{\"rating\":3,\"text\":\"   \"}",
                "{\"rating\":3}",
                "{\"rating\":3,\"text\":\"" + "x".repeat(2001) + "\"}"
        };
        for (String body : invalid) {
            mvc.perform(post("/api/places/place-one/reviews").with(user("alice")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class)).isZero();
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(post("/api/places/place-one/reviews").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"text\":\"Good\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/places/place-one/reviews").with(user("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"text\":\"Good\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class)).isZero();
    }

    @Test
    void rejectsInvalidPlaceId() throws Exception {
        mvc.perform(post("/api/places/{placeId}/reviews", "x".repeat(256))
                .with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"text\":\"Good\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentSubmissionsCannotCreateDuplicates() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> create = () -> {
                start.await();
                return mvc.perform(post("/api/places/concurrent-place/reviews")
                        .with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"text\":\"Good\"}"))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class)).isEqualTo(1);
    }

    private void submit(String place, String author, int expectedStatus) throws Exception {
        mvc.perform(post("/api/places/{placeId}/reviews", place).with(user(author)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"text\":\"Nice place\"}"))
                .andExpect(status().is(expectedStatus));
    }
}
