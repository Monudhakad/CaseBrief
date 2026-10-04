package com.casebrief;

import com.casebrief.model.CaseRecord;
import com.casebrief.service.DocumentProcessingService;
import com.casebrief.service.impl.DocumentProcessingServiceImpl;
import com.casebrief.service.impl.FieldExtractionServiceImpl;
import com.casebrief.service.impl.PDFExtractionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class SinglePdfPipelineTest {

    private DocumentProcessingService documentProcessingService;

    @BeforeEach
    public void setUp() {
        documentProcessingService = new DocumentProcessingServiceImpl(
                new PDFExtractionServiceImpl(),
                new FieldExtractionServiceImpl()
        );
    }

    @Test
    public void testProcessSinglePdf_ReferenceReport() throws Exception {
        InputStream is = getClass().getResourceAsStream("/243063858.pdf");
        assertNotNull(is, "Reference PDF /243063858.pdf must exist in test resources");

        CaseRecord record = documentProcessingService.processSinglePdf(is, "243063858.pdf");
        assertNotNull(record, "Processed CaseRecord must not be null");

        System.out.println("==================================================");
        System.out.println("CASEBRIEF SINGLE PDF EXTRACTION RESULT");
        System.out.println("==================================================");
        System.out.println("Source Filename: " + record.getSourceFilename());
        System.out.println("Report ID: " + record.getReportId());
        System.out.println("Report Date: " + record.getReportDate());
        System.out.println("Priority Level: " + record.getPriorityLevel());
        System.out.println("Reporting ESP: " + record.getReportingEsp());
        System.out.println("Incident Type (Section A): " + record.getIncidentType());
        System.out.println("Total Uploaded Files: " + record.getTotalUploadedFiles());
        System.out.println("ESP Reported Child Victim: " + record.getEspReportedChildVictim());

        System.out.println("\nSUSPECT:");
        System.out.println("Name: " + record.getSuspect().getName());
        System.out.println("Age: " + record.getSuspect().getAge());
        System.out.println("Phone: " + record.getSuspect().getPhoneNumber());
        System.out.println("Username: " + record.getSuspect().getScreenName());
        System.out.println("Profile URL: " + record.getSuspect().getProfileUrl());

        System.out.println("\nRECENT SUSPECT NETWORK INFO (LAST Section A IP):");
        System.out.println("Recent Suspect IP: " + record.getRecentSuspectIp());
        System.out.println("Recent Suspect Port: " + record.getRecentSuspectPort());
        System.out.println("Recent Suspect Timestamp: " + record.getRecentSuspectTimestamp());

        System.out.println("\nRECIPIENT:");
        System.out.println("Name: " + record.getRecipient().getName());
        System.out.println("Age: " + record.getRecipient().getAge());
        System.out.println("Phone: " + record.getRecipient().getPhoneNumber());
        System.out.println("Username: " + record.getRecipient().getScreenName());
        System.out.println("Profile URL: " + record.getRecipient().getProfileUrl());

        System.out.println("\nUPLOADED FILES:");
        for (CaseRecord.UploadedFile file : record.getUploadedFiles()) {
            System.out.println("Filename: " + file.getFilename() + " | MD5: " + file.getHash());
        }

        System.out.println("\nSECTION B CONTENT CLASSIFICATION:");
        System.out.println("Content Rating: " + record.getContentRating());
        System.out.println("Content Ranking: " + record.getContentRanking());
        System.out.println("Term: " + record.getContentTerm());

        System.out.println("\nSECTION B IP GEO-LOOKUP (EXACT MATCH):");
        System.out.println("Matched IP: " + record.getGeoIp());
        System.out.println("Country: " + record.getGeoCountry());
        System.out.println("Region: " + record.getGeoRegion());
        System.out.println("City: " + record.getGeoCity());
        System.out.println("Postal Code: " + record.getGeoPostalCode());
        System.out.println("ISP/Org: " + record.getGeoIspOrg());
        System.out.println("Type: " + record.getGeoType());
        System.out.println("Geo Found: " + record.isGeoFound());

        System.out.println("\nADDITIONAL REPORT IDs:");
        System.out.println(record.getAdditionalReportIds());
        System.out.println("==================================================");

        // Required Verifications
        assertEquals("243063858", record.getReportId().getValue(), "Report ID mismatch");
        assertEquals("E", record.getPriorityLevel().getValue(), "Priority Level mismatch");
        assertEquals("Facebook", record.getReportingEsp().getValue(), "Reporting ESP mismatch");
        // Incident Type Assertions (Section A)
        assertEquals("Child Pornography (possession, manufacture, and distribution)", record.getIncidentType().getValue(), "Incident Type mismatch");
        assertNotEquals("Auto-referred International", record.getIncidentType().getValue(), "Regression: Incident type must NOT be Section C Auto-referred International");

        assertEquals("Lucky Sahu", record.getSuspect().getName().getValue(), "Suspect Name mismatch");
        assertEquals("2409:40c4:030c:1181:704f:aea6:d708:e2eb", record.getRecentSuspectIp().getValue(), "Recent Suspect IP mismatch");
        assertEquals("37558", record.getRecentSuspectPort().getValue(), "Recent Suspect Port mismatch");
        assertEquals("Bhopal", record.getGeoCity().getValue(), "Geo City mismatch");
        assertEquals("462016", record.getGeoPostalCode().getValue(), "Geo Postal Code mismatch");
        assertEquals("Sailendra Spr", record.getRecipient().getName().getValue(), "Recipient Name mismatch");

        assertFalse(record.getUploadedFiles().isEmpty(), "Uploaded files list must not be empty");
        assertEquals("07d6a993a1bd339f01c749dd777c02e2", record.getUploadedFiles().get(0).getHash().getValue(), "Uploaded File MD5 mismatch");
    }
}
