package com.casebrief.service;

import com.casebrief.model.BulkProcessingResult;

import java.io.OutputStream;

/**
 * Service responsible for assembling batch export archives (ZIP of PDF summaries + failure logs).
 */
public interface BatchDownloadService {

    /**
     * Generates a ZIP archive containing structured Summary PDFs for all successful CaseRecords,
     * plus a failures.txt file if any documents failed to process.
     *
     * @param result populated BulkProcessingResult
     * @param outputStream target destination stream
     * @throws Exception if generation fails
     */
    void createSummaryZip(BulkProcessingResult result, OutputStream outputStream) throws Exception;
}
