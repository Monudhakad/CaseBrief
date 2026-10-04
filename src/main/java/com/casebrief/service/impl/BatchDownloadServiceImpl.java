package com.casebrief.service.impl;

import com.casebrief.model.BulkProcessingResult;
import com.casebrief.model.CaseRecord;
import com.casebrief.model.FailedRecord;
import com.casebrief.service.BatchDownloadService;
import com.casebrief.service.SummaryPdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Implementation of BatchDownloadService.
 * Compiles successful CaseRecord PDFs and an optional failures.txt log into a ZIP archive.
 */
@Service
public class BatchDownloadServiceImpl implements BatchDownloadService {

    private final SummaryPdfService summaryPdfService;

    @Autowired
    public BatchDownloadServiceImpl(SummaryPdfService summaryPdfService) {
        this.summaryPdfService = summaryPdfService;
    }

    @Override
    public void createSummaryZip(BulkProcessingResult result, OutputStream outputStream) throws Exception {
        if (result == null) {
            throw new IllegalArgumentException("BulkProcessingResult cannot be null");
        }
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }

        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            List<CaseRecord> records = result.getSuccessfulRecords();
            if (records != null) {
                int index = 1;
                for (CaseRecord record : records) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    summaryPdfService.generateSummaryPdf(record, baos);

                    String reportId = (record.getReportId() != null && record.getReportId().isPresent() && record.getReportId().getValue() != null)
                            ? record.getReportId().getValue().replaceAll("[^a-zA-Z0-9_-]", "")
                            : "UNKNOWN";
                    if (reportId.isBlank()) {
                        reportId = "UNKNOWN";
                    }

                    String entryName = String.format("%03d_Report_%s_Summary_Brief.pdf", index++, reportId);
                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    zos.write(baos.toByteArray());
                    zos.closeEntry();
                }
            }

            List<FailedRecord> failed = result.getFailedRecords();
            if (failed != null && !failed.isEmpty()) {
                ZipEntry failEntry = new ZipEntry("failures.txt");
                zos.putNextEntry(failEntry);

                StringBuilder sb = new StringBuilder();
                sb.append("CaseBrief Batch Processing Failure Report\n");
                sb.append("=========================================\n\n");
                sb.append("Total Failed Files: ").append(failed.size()).append("\n\n");

                for (FailedRecord f : failed) {
                    sb.append(String.format("File: %s | Report ID: %s | Error: %s\n",
                            f.getFilename() != null ? f.getFilename() : "UNKNOWN",
                            f.getReportId() != null ? f.getReportId() : "UNKNOWN",
                            f.getFailureReason() != null ? f.getFailureReason() : "Unknown Error"));
                }

                zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            zos.finish();
        }
    }
}
