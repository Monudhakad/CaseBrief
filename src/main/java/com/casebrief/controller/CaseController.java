package com.casebrief.controller;

import com.casebrief.model.BulkProcessingResult;
import com.casebrief.model.CaseRecord;
import com.casebrief.model.ExtractedField;
import com.casebrief.service.BatchDownloadService;
import com.casebrief.service.DocumentProcessingService;
import com.casebrief.service.ExcelExportService;
import com.casebrief.service.SummaryPdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST Controller exposing single, bulk, and ZIP PDF case summarization endpoints,
 * as well as generated Summary PDF, Batch Summary ZIP, and Excel exports.
 */
@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final DocumentProcessingService documentProcessingService;
    private final SummaryPdfService summaryPdfService;
    private final BatchDownloadService batchDownloadService;
    private final ExcelExportService excelExportService;

    public CaseController(DocumentProcessingService documentProcessingService) {
        this(documentProcessingService, null, null, null);
    }

    public CaseController(DocumentProcessingService documentProcessingService, SummaryPdfService summaryPdfService) {
        this(documentProcessingService, summaryPdfService, null, null);
    }

    @Autowired
    public CaseController(DocumentProcessingService documentProcessingService,
                          SummaryPdfService summaryPdfService,
                          BatchDownloadService batchDownloadService,
                          ExcelExportService excelExportService) {
        this.documentProcessingService = documentProcessingService;
        this.summaryPdfService = summaryPdfService;
        this.batchDownloadService = batchDownloadService;
        this.excelExportService = excelExportService;
    }

    /**
     * POST /api/cases/single - Process one PDF file.
     */
    @PostMapping("/single")
    public ResponseEntity<?> processSinglePdf(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded PDF file is empty or missing"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file type. Please upload a valid PDF file."));
        }

        try (InputStream is = file.getInputStream()) {
            CaseRecord record = documentProcessingService.processSinglePdf(is, filename);
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Failed to process PDF [" + filename + "]: " + e.getMessage()));
        }
    }

    /**
     * POST /api/cases/bulk - Process multiple PDF files directly.
     */
    @PostMapping("/bulk")
    public ResponseEntity<?> processBulkPdfs(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "No files provided for bulk processing"));
        }

        Map<String, InputStream> namedStreams = new LinkedHashMap<>();

        try {
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file.pdf";
                namedStreams.put(filename, file.getInputStream());
            }

            if (namedStreams.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "All uploaded files in batch were empty"));
            }

            BulkProcessingResult result = documentProcessingService.processBulkPdfs(namedStreams);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Bulk processing encountered an internal error: " + e.getMessage()));
        }
    }

    /**
     * POST /api/cases/zip - Process a ZIP archive containing PDF files.
     */
    @PostMapping("/zip")
    public ResponseEntity<?> processZip(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded ZIP file is empty or missing"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file type. Please upload a ZIP archive (.zip)."));
        }

        try (InputStream is = file.getInputStream()) {
            BulkProcessingResult result = documentProcessingService.processZipStream(is);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "ZIP processing encountered an internal error: " + e.getMessage()));
        }
    }

    /**
     * POST /api/cases/summary-pdf - Generate and download Summary PDF for a CaseRecord.
     */
    @PostMapping(value = "/summary-pdf", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> downloadSummaryPdf(@RequestBody CaseRecord record) {
        if (record == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Case record is required"));
        }

        if (summaryPdfService == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Summary PDF service is not configured"));
        }

        try {
            if (record.getSuspect() == null) record.setSuspect(new CaseRecord.Suspect());
            if (record.getRecipient() == null) record.setRecipient(new CaseRecord.Recipient());
            if (record.getUploadedFiles() == null) record.setUploadedFiles(new ArrayList<>());
            if (record.getAdditionalReportIds() == null) record.setAdditionalReportIds(ExtractedField.of(new ArrayList<>(), null));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            summaryPdfService.generateSummaryPdf(record, baos);
            byte[] pdfBytes = baos.toByteArray();

            String reportId = (record.getReportId() != null && record.getReportId().isPresent() && record.getReportId().getValue() != null)
                    ? record.getReportId().getValue().replaceAll("[^a-zA-Z0-9_-]", "")
                    : "case";
            if (reportId.isBlank()) {
                reportId = "case";
            }
            String filename = reportId + "_Summary_Brief.pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate summary PDF: " + e.getMessage()));
        }
    }

    /**
     * POST /api/cases/batch-summary-zip - Generate and download a ZIP containing all successful Summary PDFs.
     */
    @PostMapping(value = "/batch-summary-zip", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/zip")
    public ResponseEntity<?> downloadBatchSummaryZip(@RequestBody BulkProcessingResult result) {
        if (result == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bulk processing result is required"));
        }

        if (batchDownloadService == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Batch download service is not configured"));
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            batchDownloadService.createSummaryZip(result, baos);
            byte[] zipBytes = baos.toByteArray();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"CaseBrief_Summaries.zip\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .contentLength(zipBytes.length)
                    .body(zipBytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate batch summary ZIP: " + e.getMessage()));
        }
    }

    /**
     * POST /api/cases/excel - Generate and download an Excel workbook from the BulkProcessingResult.
     */
    @PostMapping(value = "/excel", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<?> downloadExcel(@RequestBody BulkProcessingResult result) {
        if (result == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bulk processing result is required"));
        }

        if (excelExportService == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Excel export service is not configured"));
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            excelExportService.exportBatchResult(result, baos);
            byte[] excelBytes = baos.toByteArray();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"CaseBrief_Batch_Report.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(excelBytes.length)
                    .body(excelBytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate Excel report: " + e.getMessage()));
        }
    }
}
