package com.casebrief.model;

/**
 * Encapsulates failure details for documents that failed during extraction or processing.
 */
public class FailedRecord {
    private String filename;
    private String reportId;
    private String failureReason;

    public FailedRecord(String filename, String reportId, String failureReason) {
        this.filename = filename;
        this.reportId = reportId != null ? reportId : "UNKNOWN";
        this.failureReason = failureReason;
    }

    public String getFilename() { return filename; }
    public String getReportId() { return reportId; }
    public String getFailureReason() { return failureReason; }
}
