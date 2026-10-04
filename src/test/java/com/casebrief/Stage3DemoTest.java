package com.casebrief;

import com.casebrief.model.CaseRecord;
import com.casebrief.service.DocumentProcessingService;
import com.casebrief.service.impl.DocumentProcessingServiceImpl;
import com.casebrief.service.impl.FieldExtractionServiceImpl;
import com.casebrief.service.impl.PDFExtractionServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class Stage3DemoTest {

    @Test
    public void testStage3DirectDesktopFileExtraction() throws Exception {
        File desktopPdf = new File("C:\\Users\\monud\\OneDrive\\Desktop\\243063858.pdf");
        InputStream is;
        if (desktopPdf.exists()) {
            System.out.println("Loading reference PDF directly from Desktop: " + desktopPdf.getAbsolutePath());
            is = new FileInputStream(desktopPdf);
        } else {
            System.out.println("Loading reference PDF from test resources...");
            is = getClass().getResourceAsStream("/243063858.pdf");
        }
        assertNotNull(is, "Reference PDF 243063858.pdf must exist");

        DocumentProcessingService processingService = new DocumentProcessingServiceImpl(
                new PDFExtractionServiceImpl(),
                new FieldExtractionServiceImpl()
        );

        CaseRecord record = processingService.processSinglePdf(is, "243063858.pdf");
        assertNotNull(record, "Extracted CaseRecord must not be null");

        System.out.println("\n==========================================================================");
        System.out.println("             CASEBRIEF STAGE 3: STRUCTURED EXTRACTION DISPLAY             ");
        System.out.println("==========================================================================");
        System.out.println("Source PDF        : " + record.getSourceFilename());
        System.out.println("Report ID         : " + record.getReportId());
        System.out.println("Report Date       : " + record.getReportDate());
        System.out.println("Priority Level    : " + record.getPriorityLevel());
        System.out.println("Reporting ESP     : " + record.getReportingEsp());
        System.out.println("Incident Type (A) : " + record.getIncidentType());
        System.out.println("Uploaded Files Qty: " + record.getTotalUploadedFiles());
        System.out.println("Child Victim Info : " + record.getEspReportedChildVictim());

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("SUSPECT INFORMATION:");
        System.out.println("  Name        : " + record.getSuspect().getName());
        System.out.println("  Age         : " + record.getSuspect().getAge());
        System.out.println("  Phone Number: " + record.getSuspect().getPhoneNumber());
        System.out.println("  Screen Name : " + record.getSuspect().getScreenName());
        System.out.println("  Profile URL : " + record.getSuspect().getProfileUrl());

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("RECENT SUSPECT NETWORK INFORMATION (LAST IP IN SECTION A):");
        System.out.println("  Recent IP   : " + record.getRecentSuspectIp());
        System.out.println("  Port        : " + record.getRecentSuspectPort());
        System.out.println("  Timestamp   : " + record.getRecentSuspectTimestamp());

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("RECIPIENT INFORMATION:");
        System.out.println("  Name        : " + record.getRecipient().getName());
        System.out.println("  Age         : " + record.getRecipient().getAge());
        System.out.println("  Phone Number: " + record.getRecipient().getPhoneNumber());
        System.out.println("  Screen Name : " + record.getRecipient().getScreenName());
        System.out.println("  Profile URL : " + record.getRecipient().getProfileUrl());

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("UPLOADED FILES:");
        for (CaseRecord.UploadedFile uf : record.getUploadedFiles()) {
            System.out.println("  Filename: " + uf.getFilename());
            System.out.println("  MD5 Hash: " + uf.getHash());
        }

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("SECTION B CONTENT CLASSIFICATION:");
        System.out.println("  Rating : " + record.getContentRating());
        System.out.println("  Ranking: " + record.getContentRanking());
        System.out.println("  Term   : " + record.getContentTerm());

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("SECTION B IP GEO-LOOKUP MATCH (MATCHED AGAINST RECENT SUSPECT IP):");
        System.out.println("  Matched IP : " + record.getGeoIp());
        System.out.println("  Country    : " + record.getGeoCountry());
        System.out.println("  Region     : " + record.getGeoRegion());
        System.out.println("  City       : " + record.getGeoCity());
        System.out.println("  Postal Code: " + record.getGeoPostalCode());
        System.out.println("  ISP/Org    : " + record.getGeoIspOrg());
        System.out.println("  Type       : " + record.getGeoType());
        System.out.println("  Matched?   : " + record.isGeoFound());

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("DECONFLICTION ADDITIONAL REPORT IDs:");
        System.out.println("  Report IDs : " + record.getAdditionalReportIds());
        System.out.println("==========================================================================\n");

        // Assert core extraction validity
        assertEquals("243063858", record.getReportId().getValue());
        assertEquals("Child Pornography (possession, manufacture, and distribution)", record.getIncidentType().getValue());
        assertNotEquals("Auto-referred International", record.getIncidentType().getValue(), "Incident Type must not match Section C Auto-referred International");
        assertEquals("Lucky Sahu", record.getSuspect().getName().getValue());
        assertEquals("2409:40c4:030c:1181:704f:aea6:d708:e2eb", record.getRecentSuspectIp().getValue());
        assertEquals("Sailendra Spr", record.getRecipient().getName().getValue());
    }
}
