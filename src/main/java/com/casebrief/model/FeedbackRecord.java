package com.casebrief.model;

import java.time.Instant;

/**
 * Feedback submission model — stored locally, never returned to client beyond confirmation.
 */
public class FeedbackRecord {

    private String name;       // Optional
    private String email;      // Optional
    private String category;   // Required: "bug" | "feature" | "general" | "other"
    private String message;    // Required, non-empty
    private Instant timestamp;

    public FeedbackRecord() {}

    public FeedbackRecord(String name, String email, String category, String message, Instant timestamp) {
        this.name = name;
        this.email = email;
        this.category = category;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
