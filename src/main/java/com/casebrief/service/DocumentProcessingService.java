package com.casebrief.service;

import com.casebrief.model.BulkProcessingResult;
import com.casebrief.model.CaseRecord;

import java.io.InputStream;
import java.util.Map;

/**
 * Main orchestrator for single, bulk, and ZIP document processing workflows.
 */
public interface DocumentProcessingService {

    /**
     * Processes a single PDF input stream into a structured CaseRecord.
     *
     * @param inputStream PDF input stream
     * @param filename source PDF filename
     * @return populated CaseRecord
     * @throws Exception if processing fails completely
     */
    CaseRecord processSinglePdf(InputStream inputStream, String filename) throws Exception;

    /**
     * Processes multiple PDF files independently, ensuring individual failures do not stop the batch.
     *
     * @param namedPdfStreams map of filename to PDF input stream
     * @return BulkProcessingResult containing metrics, successful records, and failed records.
     */
    BulkProcessingResult processBulkPdfs(Map<String, InputStream> namedPdfStreams);

    /**
     * Processes a batch and reports each completed document after its outcome is recorded.
     */
    BulkProcessingResult processBulkPdfs(Map<String, InputStream> namedPdfStreams,
                                         BatchProgressListener progressListener);

    /**
     * Extracts and processes a ZIP archive containing PDF reports.
     * Safely ignores non-PDF files, handles duplicate filenames, and isolates failures.
     *
     * @param zipInputStream input stream of the ZIP archive
     * @return BulkProcessingResult containing metrics, successful records, and failed records.
     */
    BulkProcessingResult processZipStream(InputStream zipInputStream);

    /**
     * Processes a ZIP archive and reports each contained PDF after its outcome is recorded.
     */
    BulkProcessingResult processZipStream(InputStream zipInputStream,
                                          BatchProgressListener progressListener);

    @FunctionalInterface
    interface BatchProgressListener {
        void onDocumentProcessed(int completed, int total, String filename, boolean successful);
    }
}
