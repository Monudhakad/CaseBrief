package com.casebrief;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for POST /api/feedback.
 *
 * Tests cover: valid submission, missing message, blank message, invalid category,
 * and oversized message validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class FeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Success cases ───────────────────────────────────────────────────────

    @Test
    public void testSubmitFeedback_ValidFull_Returns200() throws Exception {
        String body = """
            {
              "name": "Alice Analyst",
              "email": "alice@example.com",
              "category": "general",
              "message": "CaseBrief is very helpful for quick case reviews."
            }
            """;

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("received")))
                .andExpect(jsonPath("$.message", containsString("Thank you")));
    }

    @Test
    public void testSubmitFeedback_AnonymousMinimal_Returns200() throws Exception {
        String body = """
            {
              "category": "bug",
              "message": "The ZIP extraction sometimes fails on Windows paths."
            }
            """;

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("received")));
    }

    @Test
    public void testSubmitFeedback_CategoryFeature_Returns200() throws Exception {
        String body = """
            {
              "category": "feature",
              "message": "Please add Excel export from the UI."
            }
            """;

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("received")));
    }

    // ── Validation failure cases ─────────────────────────────────────────────

    @Test
    public void testSubmitFeedback_MissingMessage_Returns400() throws Exception {
        String body = """
            {
              "category": "general"
            }
            """;

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("message is required")));
    }

    @Test
    public void testSubmitFeedback_BlankMessage_Returns400() throws Exception {
        String body = """
            {
              "category": "general",
              "message": "   "
            }
            """;

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("message is required")));
    }

    @Test
    public void testSubmitFeedback_MissingCategory_Returns400() throws Exception {
        String body = """
            {
              "message": "This tool is great!"
            }
            """;

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("category is required")));
    }

    @Test
    public void testSubmitFeedback_InvalidCategory_Returns400() throws Exception {
        String body = """
            {
              "category": "hacking",
              "message": "Testing invalid category."
            }
            """;

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid category")));
    }

    @Test
    public void testSubmitFeedback_OversizedMessage_Returns400() throws Exception {
        String huge = "x".repeat(5001);
        String body = String.format("""
            {
              "category": "other",
              "message": "%s"
            }
            """, huge);

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("exceeds maximum")));
    }
}
