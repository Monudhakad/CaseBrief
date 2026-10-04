package com.casebrief;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.MediaType;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private byte[] validPdfBytes;

    @BeforeEach
    public void setUp() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/243063858.pdf")) {
            assertNotNull(is, "Reference PDF /243063858.pdf must exist");
            validPdfBytes = is.readAllBytes();
        }
    }

    // --- SINGLE ENDPOINT TESTS ---

    @Test
    public void testProcessSinglePdf_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "243063858.pdf", "application/pdf", validPdfBytes
        );

        mockMvc.perform(multipart("/api/cases/single").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceFilename", is("243063858.pdf")))
                .andExpect(jsonPath("$.reportId.value", is("243063858")))
                .andExpect(jsonPath("$.priorityLevel.value", is("E")))
                .andExpect(jsonPath("$.suspect.name.value", is("Lucky Sahu")))
                .andExpect(jsonPath("$.recentSuspectIp.value", is("2409:40c4:030c:1181:704f:aea6:d708:e2eb")))
                .andExpect(jsonPath("$.geoCity.value", is("Bhopal")));
    }

    @Test
    public void testProcessSinglePdf_EmptyFile_BadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/cases/single").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("empty or missing")));
    }

    @Test
    public void testProcessSinglePdf_UnsupportedFileType_BadRequest() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile("file", "document.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/cases/single").file(txtFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Unsupported file type")));
    }

    // --- BULK ENDPOINT TESTS ---

    @Test
    public void testProcessBulkPdfs_Success() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "case1.pdf", "application/pdf", validPdfBytes);
        MockMultipartFile file2 = new MockMultipartFile("files", "case2.pdf", "application/pdf", validPdfBytes);

        mockMvc.perform(multipart("/api/cases/bulk").file(file1).file(file2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments", is(2)))
                .andExpect(jsonPath("$.successfulDocuments", is(2)))
                .andExpect(jsonPath("$.failedDocuments", is(0)))
                .andExpect(jsonPath("$.successfulRecords", hasSize(2)));
    }

    @Test
    public void testProcessBulkPdfs_EmptyFiles_BadRequest() throws Exception {
        mockMvc.perform(multipart("/api/cases/bulk"))
                .andExpect(status().isBadRequest());
    }

    // --- ZIP ENDPOINT TESTS ---

    @Test
    public void testProcessZip_Success() throws Exception {
        byte[] zipBytes = createZipArchive(Map.of(
                "report1.pdf", validPdfBytes,
                "report2.pdf", validPdfBytes
        ));

        MockMultipartFile zipFile = new MockMultipartFile("file", "cases.zip", "application/zip", zipBytes);

        mockMvc.perform(multipart("/api/cases/zip").file(zipFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments", is(2)))
                .andExpect(jsonPath("$.successfulDocuments", is(2)))
                .andExpect(jsonPath("$.failedDocuments", is(0)));
    }

    @Test
    public void testProcessZip_NonZipFile_BadRequest() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile("file", "cases.pdf", "application/pdf", validPdfBytes);

        mockMvc.perform(multipart("/api/cases/zip").file(pdfFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Unsupported file type")));
    }

    @Test
    public void testProcessZip_EmptyFile_BadRequest() throws Exception {
        MockMultipartFile emptyZip = new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0]);

        mockMvc.perform(multipart("/api/cases/zip").file(emptyZip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("empty or missing")));
    }

    // --- SUMMARY PDF DOWNLOAD TESTS ---

    @Test
    public void testDownloadSummaryPdf_Success() throws Exception {
        String caseJson = """
            {
                "reportId": {"value": "243063858", "sourcePage": 1},
                "reportDate": {"value": "05-28-2026 16:33:23 UTC", "sourcePage": 1},
                "priorityLevel": {"value": "E", "sourcePage": 1},
                "reportingEsp": {"value": "Facebook", "sourcePage": 4},
                "incidentType": {"value": "Child Pornography", "sourcePage": 4},
                "suspect": {
                    "name": {"value": "Lucky Sahu", "sourcePage": 4}
                },
                "geoCity": {"value": "Bhopal", "sourcePage": 10},
                "geoFound": true
            }
            """;

        byte[] pdfBytes = mockMvc.perform(post("/api/cases/summary-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(caseJson))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"243063858_Summary_Brief.pdf\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertTrue(pdfBytes.length > 0, "Generated summary PDF bytes should not be empty");
    }

    @Test
    public void testDownloadSummaryPdf_DefaultFilenameWhenReportIdMissing() throws Exception {
        String caseJson = "{}";

        byte[] pdfBytes = mockMvc.perform(post("/api/cases/summary-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(caseJson))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"case_Summary_Brief.pdf\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertTrue(pdfBytes.length > 0, "Generated summary PDF bytes should not be empty");
    }

    // --- BATCH SUMMARY ZIP & EXCEL TESTS ---

    @Test
    public void testDownloadBatchSummaryZip_WithSuccessAndFailures() throws Exception {
        String batchJson = """
            {
                "totalDocuments": 2,
                "successfulDocuments": 1,
                "failedDocuments": 1,
                "successfulRecords": [
                    {
                        "reportId": {"value": "243063858", "sourcePage": 1},
                        "reportDate": {"value": "05-28-2026 16:33:23 UTC", "sourcePage": 1},
                        "priorityLevel": {"value": "E", "sourcePage": 1},
                        "suspect": {"name": {"value": "Lucky Sahu", "sourcePage": 4}}
                    }
                ],
                "failedRecords": [
                    {
                        "filename": "corrupt_file.pdf",
                        "reportId": "UNKNOWN",
                        "failureReason": "Invalid PDF header signature"
                    }
                ]
            }
            """;

        byte[] zipBytes = mockMvc.perform(post("/api/cases/batch-summary-zip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"CaseBrief_Summaries.zip\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertTrue(zipBytes.length > 0, "Generated batch ZIP must not be empty");

        // Inspect ZIP entries
        java.util.List<String> entryNames = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }

        assertTrue(entryNames.contains("001_Report_243063858_Summary_Brief.pdf"), "ZIP must contain numbered PDF summary");
        assertTrue(entryNames.contains("failures.txt"), "ZIP must contain failures.txt when failures are present");
    }

    @Test
    public void testDownloadExcel_WithSuccessAndFailures() throws Exception {
        String batchJson = """
            {
                "totalDocuments": 2,
                "successfulDocuments": 1,
                "failedDocuments": 1,
                "successfulRecords": [
                    {
                        "reportId": {"value": "243063858", "sourcePage": 1},
                        "reportDate": {"value": "05-28-2026 16:33:23 UTC", "sourcePage": 1},
                        "priorityLevel": {"value": "E", "sourcePage": 1},
                        "suspect": {"name": {"value": "Lucky Sahu", "sourcePage": 4}}
                    }
                ],
                "failedRecords": [
                    {
                        "filename": "corrupt_file.pdf",
                        "reportId": "UNKNOWN",
                        "failureReason": "Invalid PDF header signature"
                    }
                ]
            }
            """;

        byte[] excelBytes = mockMvc.perform(post("/api/cases/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"CaseBrief_Batch_Report.xlsx\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertTrue(excelBytes.length > 0, "Generated Excel report must not be empty");

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excelBytes))) {
            assertEquals(2, wb.getNumberOfSheets(), "Workbook should have 2 sheets when failures exist");
            assertEquals("Case Brief Summaries", wb.getSheetAt(0).getSheetName());
            assertEquals("Failed Documents", wb.getSheetAt(1).getSheetName());
        }
    }

    private byte[] createZipArchive(Map<String, byte[]> entries) throws Exception {
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
