package com.casebrief.service;

import com.casebrief.model.CaseRecord;
import com.casebrief.model.FailedRecord;

import java.io.OutputStream;
import java.util.List;

/**
 * Responsible for exporting batch processing results into Excel workbooks.
 */
public interface ExcelExportService {

    /**
     * Writes successful CaseRecords into a structured Excel workbook.
     */
    void exportSuccessfulRecords(List<CaseRecord> records, OutputStream outputStream) throws Exception;

    /**
     * Writes failed document metadata and reasons into a failure report Excel workbook.
     */
    void exportFailureReport(List<FailedRecord> failedRecords, OutputStream outputStream) throws Exception;

    /**
     * Writes both successful CaseRecords and any failed records into a unified Excel workbook.
     */
    void exportBatchResult(com.casebrief.model.BulkProcessingResult result, OutputStream outputStream) throws Exception;
}
