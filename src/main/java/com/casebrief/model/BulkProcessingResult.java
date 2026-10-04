package com.casebrief.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary outcome container for bulk PDF processing jobs.
 */
public class BulkProcessingResult {
    private int totalDocuments;
    private int successfulDocuments;
    private int failedDocuments;
    private long totalProcessingTimeMs;

    private List<CaseRecord> successfulRecords = new ArrayList<>();
    private List<FailedRecord> failedRecords = new ArrayList<>();

    public BulkProcessingResult() {}

    public int getTotalDocuments() { return totalDocuments; }
    public void setTotalDocuments(int totalDocuments) { this.totalDocuments = totalDocuments; }

    public int getSuccessfulDocuments() { return successfulDocuments; }
    public void setSuccessfulDocuments(int successfulDocuments) { this.successfulDocuments = successfulDocuments; }

    public int getFailedDocuments() { return failedDocuments; }
    public void setFailedDocuments(int failedDocuments) { this.failedDocuments = failedDocuments; }

    public long getTotalProcessingTimeMs() { return totalProcessingTimeMs; }
    public void setTotalProcessingTimeMs(long totalProcessingTimeMs) { this.totalProcessingTimeMs = totalProcessingTimeMs; }

    public List<CaseRecord> getSuccessfulRecords() { return successfulRecords; }
    public void setSuccessfulRecords(List<CaseRecord> successfulRecords) { this.successfulRecords = successfulRecords; }

    public List<FailedRecord> getFailedRecords() { return failedRecords; }
    public void setFailedRecords(List<FailedRecord> failedRecords) { this.failedRecords = failedRecords; }
}
