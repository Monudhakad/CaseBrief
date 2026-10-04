package com.casebrief.service.impl;

import com.casebrief.model.BulkProcessingResult;
import com.casebrief.model.CaseRecord;
import com.casebrief.model.ExtractedField;
import com.casebrief.model.FailedRecord;
import com.casebrief.service.ExcelExportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.List;

/**
 * Apache POI implementation of ExcelExportService.
 * Exports successful CaseRecords and failure report logs into Excel workbooks.
 */
@Service
public class ExcelExportServiceImpl implements ExcelExportService {

    @Override
    public void exportSuccessfulRecords(List<CaseRecord> records, OutputStream outputStream) throws Exception {
        if (records == null) {
            throw new IllegalArgumentException("Records list cannot be null");
        }
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            createSuccessfulRecordsSheet(workbook, records);
            workbook.write(outputStream);
        }
    }

    @Override
    public void exportFailureReport(List<FailedRecord> failedRecords, OutputStream outputStream) throws Exception {
        if (failedRecords == null) {
            throw new IllegalArgumentException("Failed records list cannot be null");
        }
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            createFailureReportSheet(workbook, failedRecords);
            workbook.write(outputStream);
        }
    }

    @Override
    public void exportBatchResult(BulkProcessingResult result, OutputStream outputStream) throws Exception {
        if (result == null) {
            throw new IllegalArgumentException("BulkProcessingResult cannot be null");
        }
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            List<CaseRecord> successful = result.getSuccessfulRecords() != null ? result.getSuccessfulRecords() : List.of();
            createSuccessfulRecordsSheet(workbook, successful);

            if (result.getFailedRecords() != null && !result.getFailedRecords().isEmpty()) {
                createFailureReportSheet(workbook, result.getFailedRecords());
            }

            workbook.write(outputStream);
        }
    }

    private void createSuccessfulRecordsSheet(Workbook workbook, List<CaseRecord> records) {
        Sheet sheet = workbook.createSheet("Case Brief Summaries");

        // Header Style — dark blue background, white bold text
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        // Geo City data style — bold red font for instant visibility
        CellStyle geoCityStyle = workbook.createCellStyle();
        Font geoCityFont = workbook.createFont();
        geoCityFont.setBold(true);
        geoCityFont.setColor(IndexedColors.RED.getIndex());
        geoCityStyle.setFont(geoCityFont);

        // Columns Definition (16 columns)
        String[] headers = {
                "Tipline No.",       // col 0 — Report ID
                "Suspect Name",      // col 1
                "Recent Suspect IP", // col 2
                "Geo City",          // col 3 — bold red
                "Priority",          // col 4
                "Reporting ESP",     // col 5
                "Incident Type",     // col 6
                "Suspect Age",       // col 7
                "Suspect Phone",     // col 8
                "Suspect Username",  // col 9
                "Recipient Name",    // col 10
                "Recipient Age",     // col 11
                "Recipient Phone",   // col 12
                "Recipient Username",// col 13
                "Content Rating",    // col 14
                "Geo Postal Code"    // col 15
        };

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data Rows
        int rowIdx = 1;
        for (CaseRecord record : records) {
            Row row = sheet.createRow(rowIdx++);
            int colIdx = 0;

            CaseRecord.Suspect s = record.getSuspect();
            CaseRecord.Recipient r = record.getRecipient();

            // col 0: Tipline No. (Report ID)
            row.createCell(colIdx++).setCellValue(formatField(record.getReportId()));

            // col 1: Suspect Name
            row.createCell(colIdx++).setCellValue(s != null ? formatField(s.getName()) : "N/A");

            // col 2: Recent Suspect IP
            row.createCell(colIdx++).setCellValue(formatField(record.getRecentSuspectIp()));

            // col 3: Geo City — bold red
            Cell cityCell = row.createCell(colIdx++);
            cityCell.setCellValue(formatField(record.getGeoCity()));
            cityCell.setCellStyle(geoCityStyle);

            // col 4: Priority
            row.createCell(colIdx++).setCellValue(formatField(record.getPriorityLevel()));

            // col 5: Reporting ESP
            row.createCell(colIdx++).setCellValue(formatField(record.getReportingEsp()));

            // col 6: Incident Type
            row.createCell(colIdx++).setCellValue(formatField(record.getIncidentType()));

            // col 7–9: Suspect details
            row.createCell(colIdx++).setCellValue(s != null ? formatField(s.getAge()) : "N/A");
            row.createCell(colIdx++).setCellValue(s != null ? formatField(s.getPhoneNumber()) : "N/A");
            row.createCell(colIdx++).setCellValue(s != null ? formatField(s.getScreenName()) : "N/A");

            // col 10–13: Recipient details
            row.createCell(colIdx++).setCellValue(r != null ? formatField(r.getName()) : "N/A");
            row.createCell(colIdx++).setCellValue(r != null ? formatField(r.getAge()) : "N/A");
            row.createCell(colIdx++).setCellValue(r != null ? formatField(r.getPhoneNumber()) : "N/A");
            row.createCell(colIdx++).setCellValue(r != null ? formatField(r.getScreenName()) : "N/A");

            // col 14: Content Rating
            row.createCell(colIdx++).setCellValue(formatField(record.getContentRating()));

            // col 15: Geo Postal Code
            row.createCell(colIdx++).setCellValue(formatField(record.getGeoPostalCode()));
        }

        // Auto-size columns for readability
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createFailureReportSheet(Workbook workbook, List<FailedRecord> failedRecords) {
        Sheet sheet = workbook.createSheet("Failed Documents");

        // Header Style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        String[] headers = {"Filename", "Report ID", "Failure Reason"};

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (FailedRecord failed : failedRecords) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(failed.getFilename() != null ? failed.getFilename() : "UNKNOWN");
            row.createCell(1).setCellValue(failed.getReportId() != null ? failed.getReportId() : "UNKNOWN");
            row.createCell(2).setCellValue(failed.getFailureReason() != null ? failed.getFailureReason() : "Unknown Error");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private <T> String formatField(ExtractedField<T> field) {
        if (field == null || !field.isPresent()) {
            return "N/A";
        }
        return field.getValue().toString();
    }
}
