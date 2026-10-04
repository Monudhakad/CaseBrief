package com.casebrief.service;

import com.casebrief.model.CaseRecord;
import java.io.OutputStream;

/**
 * Responsible for generating compact, structured 1-2 page summary PDFs from CaseRecord.
 */
public interface SummaryPdfService {

    /**
     * Renders a CaseRecord into a structured summary PDF written to the output stream.
     *
     * @param record populated CaseRecord
     * @param outputStream target destination stream
     * @throws Exception if generation fails
     */
    void generateSummaryPdf(CaseRecord record, OutputStream outputStream) throws Exception;
}
