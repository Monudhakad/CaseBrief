package com.casebrief.service.impl;

import com.casebrief.model.BulkProcessingResult;
import com.casebrief.model.CaseRecord;
import com.casebrief.model.FailedRecord;
import com.casebrief.service.DocumentProcessingService;
import com.casebrief.service.FieldExtractionService;
import com.casebrief.service.PDFExtractionService;
import com.casebrief.service.PDFExtractionService.PageText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Single, Bulk, and ZIP Document Processing Pipeline Implementation.
 */
@Service
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final PDFExtractionService pdfExtractionService;
    private final FieldExtractionService fieldExtractionService;

    @Autowired
    public DocumentProcessingServiceImpl(PDFExtractionService pdfExtractionService,
                                         FieldExtractionService fieldExtractionService) {
        this.pdfExtractionService = pdfExtractionService;
        this.fieldExtractionService = fieldExtractionService;
    }

    @Override
    public CaseRecord processSinglePdf(InputStream inputStream, String filename) throws Exception {
        if (inputStream == null) {
            throw new IllegalArgumentException("PDF input stream is null for file: " + filename);
        }

        List<PageText> pages;
        try {
            pages = pdfExtractionService.extractPageTexts(inputStream);
        } catch (Exception e) {
            throw new Exception("PDF Extraction Failed: " + e.getMessage(), e);
        }

        if (pages == null || pages.isEmpty()) {
            throw new Exception("PDF File [" + filename + "] contains no readable pages/text");
        }

        CaseRecord record;
        try {
            record = fieldExtractionService.extractFields(pages, filename);
        } catch (Exception e) {
            throw new Exception("Field Parsing Failed: " + e.getMessage(), e);
        }

        return record;
    }

    @Override
    public BulkProcessingResult processBulkPdfs(Map<String, InputStream> namedPdfStreams) {
        return processBulkPdfs(namedPdfStreams, null);
    }

    @Override
    public BulkProcessingResult processBulkPdfs(Map<String, InputStream> namedPdfStreams,
                                                BatchProgressListener progressListener) {
        long startTime = System.currentTimeMillis();
        BulkProcessingResult result = new BulkProcessingResult();
        BatchProgressListener listener = progressListener != null
                ? progressListener
                : (completed, total, filename, successful) -> { };

        if (namedPdfStreams == null || namedPdfStreams.isEmpty()) {
            result.setTotalProcessingTimeMs(System.currentTimeMillis() - startTime);
            return result;
        }

        result.setTotalDocuments(namedPdfStreams.size());

        for (Map.Entry<String, InputStream> entry : namedPdfStreams.entrySet()) {
            String filename = entry.getKey();
            InputStream stream = entry.getValue();
            boolean successful = false;

            try {
                CaseRecord record = processSinglePdf(stream, filename);
                result.getSuccessfulRecords().add(record);
                result.setSuccessfulDocuments(result.getSuccessfulDocuments() + 1);
                successful = true;
            } catch (Exception e) {
                String reportId = extractPossibleReportId(filename);
                result.getFailedRecords().add(new FailedRecord(filename, reportId, e.getMessage()));
                result.setFailedDocuments(result.getFailedDocuments() + 1);
            }

            int completed = result.getSuccessfulDocuments() + result.getFailedDocuments();
            listener.onDocumentProcessed(completed, result.getTotalDocuments(), filename, successful);
        }

        result.setTotalProcessingTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    @Override
    public BulkProcessingResult processZipStream(InputStream zipInputStream) {
        return processZipStream(zipInputStream, null);
    }

    @Override
    public BulkProcessingResult processZipStream(InputStream zipInputStream,
                                                 BatchProgressListener progressListener) {
        long startTime = System.currentTimeMillis();
        Map<String, InputStream> pdfStreams = new LinkedHashMap<>();

        if (zipInputStream == null) {
            BulkProcessingResult emptyRes = new BulkProcessingResult();
            emptyRes.setTotalProcessingTimeMs(System.currentTimeMillis() - startTime);
            return emptyRes;
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("casebrief_zip_");

            try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String entryName = entry.getName();
                    if (entryName.startsWith("__MACOSX") || entryName.endsWith(".DS_Store")) {
                        continue;
                    }
                    if (!entryName.toLowerCase().endsWith(".pdf")) {
                        continue; // Safely ignore non-PDF files
                    }

                    // Zip Slip Path Traversal Guard
                    Path targetPath = tempDir.resolve(entryName).normalize();
                    if (!targetPath.startsWith(tempDir)) {
                        throw new SecurityException("Zip entry attempted path traversal outside target directory: " + entryName);
                    }

                    byte[] bytes = zis.readAllBytes();
                    String simpleFilename = targetPath.getFileName().toString();
                    String uniqueFilename = resolveUniqueFilename(pdfStreams, simpleFilename);

                    pdfStreams.put(uniqueFilename, new ByteArrayInputStream(bytes));
                }
            }
        } catch (Exception e) {
            BulkProcessingResult errorRes = new BulkProcessingResult();
            errorRes.setTotalDocuments(1);
            errorRes.setFailedDocuments(1);
            errorRes.getFailedRecords().add(new FailedRecord("ZIP Archive", "UNKNOWN", "ZIP Extraction Error: " + e.getMessage()));
            errorRes.setTotalProcessingTimeMs(System.currentTimeMillis() - startTime);
            return errorRes;
        } finally {
            if (tempDir != null) {
                deleteDirRecursively(tempDir.toFile());
            }
        }

        BulkProcessingResult result = processBulkPdfs(pdfStreams, progressListener);
        result.setTotalProcessingTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private String resolveUniqueFilename(Map<String, InputStream> existing, String filename) {
        if (!existing.containsKey(filename)) {
            return filename;
        }
        int dotIdx = filename.lastIndexOf('.');
        String base = dotIdx != -1 ? filename.substring(0, dotIdx) : filename;
        String ext = dotIdx != -1 ? filename.substring(dotIdx) : "";

        int count = 1;
        String candidate;
        do {
            candidate = base + "_" + count + ext;
            count++;
        } while (existing.containsKey(candidate));

        return candidate;
    }

    private String extractPossibleReportId(String filename) {
        if (filename == null) return "UNKNOWN";
        Matcher m = Pattern.compile("\\b(\\d{9})\\b").matcher(filename);
        if (m.find()) {
            return m.group(1);
        }
        return "UNKNOWN";
    }

    private void deleteDirRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteDirRecursively(child);
            }
        }
        file.delete();
    }
}
