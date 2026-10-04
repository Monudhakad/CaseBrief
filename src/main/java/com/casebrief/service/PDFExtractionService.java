package com.casebrief.service;

import java.io.InputStream;
import java.util.List;

/**
 * Responsible strictly for PDF text loading and page-aware text extraction.
 */
public interface PDFExtractionService {

    /**
     * PageText holds 1-indexed page number and extracted text content.
     */
    record PageText(int pageNumber, String content) {}

    /**
     * Extracts text page by page from the provided PDF stream.
     *
     * @param inputStream PDF stream
     * @return List of PageText items representing page number and text.
     * @throws Exception if stream reading or PDF parsing fails.
     */
    List<PageText> extractPageTexts(InputStream inputStream) throws Exception;
}
