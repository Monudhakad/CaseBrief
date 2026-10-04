package com.casebrief.controller;

import com.casebrief.model.FeedbackRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

/**
 * REST Controller for user feedback submissions.
 *
 * Feedback is validated server-side and saved as newline-delimited JSON
 * to a controlled local directory. Filesystem paths are never exposed to the client.
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    private static final Set<String> VALID_CATEGORIES = Set.of("bug", "feature", "general", "other");

    @Value("${casebrief.feedback.storage-dir:${user.home}/.casebrief/feedback}")
    private String storageDirPath;

    private final ObjectMapper objectMapper;

    public FeedbackController() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * POST /api/feedback
     * Accepts feedback JSON body: { name?, email?, category, message }
     */
    @PostMapping
    public ResponseEntity<?> submitFeedback(@RequestBody Map<String, String> payload) {
        // --- Validate message (required, non-blank) ---
        String message = payload.getOrDefault("message", "").trim();
        if (message.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Feedback message is required and cannot be empty."));
        }
        if (message.length() > 5000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Feedback message exceeds maximum length of 5000 characters."));
        }

        // --- Validate category (required) ---
        String category = payload.getOrDefault("category", "").trim().toLowerCase();
        if (category.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Feedback category is required."));
        }
        if (!VALID_CATEGORIES.contains(category)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid category. Must be one of: bug, feature, general, other."));
        }

        // --- Optional fields ---
        String name = sanitize(payload.getOrDefault("name", ""), 100);
        String email = sanitize(payload.getOrDefault("email", ""), 254);

        // --- Build record ---
        FeedbackRecord record = new FeedbackRecord(
                name.isEmpty() ? null : name,
                email.isEmpty() ? null : email,
                category,
                message,
                Instant.now()
        );

        // --- Persist ---
        try {
            persist(record);
        } catch (IOException e) {
            log.error("Failed to save feedback to disk", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Feedback received but could not be saved. Please try again later."));
        }

        log.info("Feedback received: category={} name={}", category, name.isEmpty() ? "anonymous" : name);
        return ResponseEntity.ok(Map.of("status", "received", "message", "Thank you for your feedback!"));
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private void persist(FeedbackRecord record) throws IOException {
        Path dir = Paths.get(storageDirPath);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        // One JSONL file per month: feedback-2026-09.jsonl
        String monthKey = record.getTimestamp().toString().substring(0, 7); // "2026-09"
        Path file = dir.resolve("feedback-" + monthKey + ".jsonl");
        String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
        Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String sanitize(String input, int maxLen) {
        if (input == null) return "";
        // strip HTML/script tags simply
        String stripped = input.replaceAll("<[^>]*>", "").trim();
        return stripped.length() > maxLen ? stripped.substring(0, maxLen) : stripped;
    }
}
