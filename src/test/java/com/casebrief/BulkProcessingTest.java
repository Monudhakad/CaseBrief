package com.casebrief;

import com.casebrief.model.BulkProcessingResult;
import com.casebrief.model.CaseRecord;
import com.casebrief.model.FailedRecord;
import com.casebrief.service.DocumentProcessingService;
import com.casebrief.service.ExcelExportService;
import com.casebrief.service.impl.DocumentProcessingServiceImpl;
import com.casebrief.service.impl.ExcelExportServiceImpl;
import com.casebrief.service.impl.FieldExtractionServiceImpl;
import com.casebrief.service.impl.PDFExtractionServiceImpl;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class BulkProcessingTest {

    private DocumentProcessingService documentProcessingService;
    private ExcelExportService excelExportService;
    private byte[] validPdfBytes;

    @BeforeEach
    public void setUp() throws Exception {
        documentProcessingService = new DocumentProcessingServiceImpl(
                new PDFExtractionServiceImpl(),
                new FieldExtractionServiceImpl()
        );
        excelExportService = new ExcelExportServiceImpl();

        try (InputStream is = getClass().getResourceAsStream("/243063858.pdf")) {
            assertNotNull(is, "Reference PDF /243063858.pdf must exist");
            validPdfBytes = is.readAllBytes();
        }
    }

    @Test
    public void testMultipleValidPdfs() {
        Map<String, InputStream> streams = new LinkedHashMap<>();
        streams.put("case1.pdf", new ByteArrayInputStream(validPdfBytes));
        streams.put("case2.pdf", new ByteArrayInputStream(validPdfBytes));

        BulkProcessingResult result = documentProcessingService.processBulkPdfs(streams);

        assertEquals(2, result.getTotalDocuments());
        assertEquals(2, result.getSuccessfulDocuments());
        assertEquals(0, result.getFailedDocuments());
        assertEquals(2, result.getSuccessfulRecords().size());
        assertTrue(result.getFailedRecords().isEmpty());
    }

    @Test
    public void testValidAndMalformedPdfBatch() {
        Map<String, InputStream> streams = new LinkedHashMap<>();
        streams.put("valid.pdf", new ByteArrayInputStream(validPdfBytes));
        streams.put("corrupted.pdf", new ByteArrayInputStream("INVALID_NOT_A_PDF_CONTENT".getBytes(StandardCharsets.UTF_8)));

        BulkProcessingResult result = documentProcessingService.processBulkPdfs(streams);

        assertEquals(2, result.getTotalDocuments());
        assertEquals(1, result.getSuccessfulDocuments());
        assertEquals(1, result.getFailedDocuments());
        assertEquals(1, result.getSuccessfulRecords().size());
        assertEquals(1, result.getFailedRecords().size());

        FailedRecord failed = result.getFailedRecords().get(0);
        assertEquals("corrupted.pdf", failed.getFilename());
        assertNotNull(failed.getFailureReason());
    }

    @Test
    public void testBatchProgressReportsActualPerDocumentOutcomes() {
        Map<String, InputStream> streams = new LinkedHashMap<>();
        streams.put("valid.pdf", new ByteArrayInputStream(validPdfBytes));
        streams.put("broken.pdf", new ByteArrayInputStream("NOT_A_PDF".getBytes(StandardCharsets.UTF_8)));
        List<String> progress = new ArrayList<>();

        BulkProcessingResult result = documentProcessingService.processBulkPdfs(streams,
                (completed, total, filename, successful) -> progress.add(
                        completed + "/" + total + " " + filename + " " + successful));

        assertEquals(2, result.getTotalDocuments());
        assertEquals(List.of("1/2 valid.pdf true", "2/2 broken.pdf false"), progress);
        assertEquals(1, result.getSuccessfulDocuments());
        assertEquals(1, result.getFailedDocuments());
    }

    @Test
    public void testZipWithMultiplePdfs() throws Exception {
        byte[] zipBytes = createZipArchive(Map.of(
                "report1.pdf", validPdfBytes,
                "report2.pdf", validPdfBytes
        ));

        BulkProcessingResult result = documentProcessingService.processZipStream(new ByteArrayInputStream(zipBytes));

        assertEquals(2, result.getTotalDocuments());
        assertEquals(2, result.getSuccessfulDocuments());
        assertEquals(0, result.getFailedDocuments());
    }

    @Test
    public void testZipWithValidAndMalformedPdf() throws Exception {
        byte[] zipBytes = createZipArchive(Map.of(
                "valid.pdf", validPdfBytes,
                "broken.pdf", "CORRUPTED_BYTES".getBytes(StandardCharsets.UTF_8)
        ));

        BulkProcessingResult result = documentProcessingService.processZipStream(new ByteArrayInputStream(zipBytes));

        assertEquals(2, result.getTotalDocuments());
        assertEquals(1, result.getSuccessfulDocuments());
        assertEquals(1, result.getFailedDocuments());
        assertEquals("broken.pdf", result.getFailedRecords().get(0).getFilename());
    }

    @Test
    public void testZipProgressReportsEachContainedPdf() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("valid.pdf", validPdfBytes);
        entries.put("broken.pdf", "NOT_A_PDF".getBytes(StandardCharsets.UTF_8));
        List<String> progress = new ArrayList<>();

        BulkProcessingResult result = documentProcessingService.processZipStream(
                new ByteArrayInputStream(createZipArchive(entries)),
                (completed, total, filename, successful) -> progress.add(
                        completed + "/" + total + " " + filename + " " + successful));

        assertEquals(2, result.getTotalDocuments());
        assertEquals(List.of("1/2 valid.pdf true", "2/2 broken.pdf false"), progress);
        assertEquals(1, result.getSuccessfulDocuments());
        assertEquals(1, result.getFailedDocuments());
    }

    @Test
    public void testEmptyZip() throws Exception {
        byte[] emptyZipBytes = createZipArchive(Collections.emptyMap());

        BulkProcessingResult result = documentProcessingService.processZipStream(new ByteArrayInputStream(emptyZipBytes));

        assertEquals(0, result.getTotalDocuments());
        assertEquals(0, result.getSuccessfulDocuments());
        assertEquals(0, result.getFailedDocuments());
    }

    @Test
    public void testZipWithNonPdfFilesIgnoredSafely() throws Exception {
        byte[] zipBytes = createZipArchive(Map.of(
                "valid.pdf", validPdfBytes,
                "readme.txt", "This is plain text".getBytes(StandardCharsets.UTF_8),
                "image.png", new byte[]{0, 1, 2, 3}
        ));

        BulkProcessingResult result = documentProcessingService.processZipStream(new ByteArrayInputStream(zipBytes));

        assertEquals(1, result.getTotalDocuments(), "Non-PDF files must be safely ignored");
        assertEquals(1, result.getSuccessfulDocuments());
    }

    @Test
    public void testZipDuplicateFilenamesHandledSafely() throws Exception {
        byte[] zipBytes = createZipArchive(Map.of(
                "folder1/243063858.pdf", validPdfBytes,
                "folder2/243063858.pdf", validPdfBytes
        ));

        BulkProcessingResult result = documentProcessingService.processZipStream(new ByteArrayInputStream(zipBytes));

        assertEquals(2, result.getTotalDocuments(), "Both duplicate files must be extracted into batch");
        assertEquals(2, result.getSuccessfulDocuments());

        Set<String> filenames = new HashSet<>();
        for (CaseRecord r : result.getSuccessfulRecords()) {
            filenames.add(r.getSourceFilename());
        }
        assertEquals(2, filenames.size(), "Filenames must be unique (e.g. 243063858.pdf, 243063858_1.pdf)");
    }

    @Test
    public void testSuccessfulExcelExport() throws Exception {
        Map<String, InputStream> streams = Map.of("report.pdf", new ByteArrayInputStream(validPdfBytes));
        BulkProcessingResult result = documentProcessingService.processBulkPdfs(streams);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        excelExportService.exportSuccessfulRecords(result.getSuccessfulRecords(), out);

        byte[] excelBytes = out.toByteArray();
        assertTrue(excelBytes.length > 0, "Excel output stream must not be empty");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheet("Case Brief Summaries");
            assertNotNull(sheet, "Excel sheet 'Case Brief Summaries' must exist");
            assertEquals(2, sheet.getPhysicalNumberOfRows(), "Row count must be 2 (Header + 1 Data Row)");

            Row headerRow = sheet.getRow(0);
            assertEquals("Tipline No.", headerRow.getCell(0).getStringCellValue());
            assertEquals("Recent Suspect IP", headerRow.getCell(2).getStringCellValue());

            Row dataRow = sheet.getRow(1);
            assertEquals("243063858", dataRow.getCell(0).getStringCellValue());
            assertEquals("E", dataRow.getCell(4).getStringCellValue());       // Priority is now col 4
            assertEquals("Lucky Sahu", dataRow.getCell(1).getStringCellValue()); // Suspect Name is now col 1
        }
    }

    @Test
    public void testFailureExcelExport() throws Exception {
        List<FailedRecord> failedRecords = List.of(
                new FailedRecord("corrupted.pdf", "243063858", "PDF Extraction Failed: Corrupted header"),
                new FailedRecord("empty.pdf", "UNKNOWN", "PDF File contains no readable pages")
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        excelExportService.exportFailureReport(failedRecords, out);

        byte[] excelBytes = out.toByteArray();
        assertTrue(excelBytes.length > 0, "Failure Excel stream must not be empty");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheet("Failed Documents");
            assertNotNull(sheet, "Excel sheet 'Failed Documents' must exist");
            assertEquals(3, sheet.getPhysicalNumberOfRows(), "Row count must be 3 (Header + 2 Failure Rows)");

            Row headerRow = sheet.getRow(0);
            assertEquals("Filename", headerRow.getCell(0).getStringCellValue());
            assertEquals("Failure Reason", headerRow.getCell(2).getStringCellValue());

            Row dataRow1 = sheet.getRow(1);
            assertEquals("corrupted.pdf", dataRow1.getCell(0).getStringCellValue());
            assertTrue(dataRow1.getCell(2).getStringCellValue().contains("Corrupted header"));
        }
    }

    private byte[] createZipArchive(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
