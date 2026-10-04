package com.casebrief;

import com.casebrief.model.CaseRecord;
import com.casebrief.service.DocumentProcessingService;
import com.casebrief.service.SummaryPdfService;
import com.casebrief.service.impl.DocumentProcessingServiceImpl;
import com.casebrief.service.impl.FieldExtractionServiceImpl;
import com.casebrief.service.impl.PDFExtractionServiceImpl;
import com.casebrief.service.impl.SummaryPdfServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class SummaryPdfServiceTest {

    private DocumentProcessingService documentProcessingService;
    private SummaryPdfService summaryPdfService;

    @BeforeEach
    public void setUp() {
        documentProcessingService = new DocumentProcessingServiceImpl(
                new PDFExtractionServiceImpl(),
                new FieldExtractionServiceImpl()
        );
        summaryPdfService = new SummaryPdfServiceImpl();
    }

    @Test
    public void testGenerateSummaryPdf_Stage5Verification() throws Exception {
        InputStream is = getClass().getResourceAsStream("/243063858.pdf");
        assertNotNull(is, "Reference PDF /243063858.pdf must exist");

        // 1. Process original PDF into CaseRecord
        CaseRecord record = documentProcessingService.processSinglePdf(is, "243063858.pdf");
        assertNotNull(record, "Processed CaseRecord must not be null");

        // 2. Generate summary PDF into byte stream
        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        summaryPdfService.generateSummaryPdf(record, pdfOutputStream);

        byte[] generatedPdfBytes = pdfOutputStream.toByteArray();
        assertTrue(generatedPdfBytes.length > 0, "Generated PDF stream must not be empty");

        // Also save to target directory for physical inspection
        File targetPdf = new File("target/243063858_Summary_Brief.pdf");
        try (FileOutputStream fos = new FileOutputStream(targetPdf)) {
            fos.write(generatedPdfBytes);
        }
        System.out.println("Generated Summary PDF saved to: " + targetPdf.getAbsolutePath());

        // 3. Inspect generated summary PDF using PDFBox
        try (PDDocument summaryDoc = Loader.loadPDF(generatedPdfBytes)) {
            int pageCount = summaryDoc.getNumberOfPages();
            System.out.println("Generated Summary PDF Page Count: " + pageCount);

            // Assert page count is 1-2 pages
            assertTrue(pageCount >= 1 && pageCount <= 2, "Generated summary PDF must be 1 to 2 pages long, but was: " + pageCount);

            PDFTextStripper stripper = new PDFTextStripper();
            String rawSummaryText = stripper.getText(summaryDoc);
            String normalizedSummaryText = rawSummaryText.replaceAll("\\s+", " ");
            String compactIpText = rawSummaryText.replaceAll("[\\s\\:]+", "");

            System.out.println("\n==================================================");
            System.out.println("GENERATED SUMMARY PDF EXTRACTED TEXT:");
            System.out.println("==================================================");
            System.out.println(rawSummaryText);
            System.out.println("==================================================");

            // Assertions on generated PDF content
            assertTrue(normalizedSummaryText.contains("Child Pornography (possession, manufacture, and distribution)"),
                    "Generated PDF must contain Section A Primary Incident Type");
            assertTrue(compactIpText.contains("240940c4030c1181704faea6d708e2eb"),
                    "Generated PDF must contain Recent Suspect IP");
            assertTrue(normalizedSummaryText.contains("Bhopal"),
                    "Generated PDF must contain City Bhopal");
            assertTrue(normalizedSummaryText.contains("462016"),
                    "Generated PDF must contain Postal Code 462016");
            assertTrue(normalizedSummaryText.contains("Lucky Sahu"),
                    "Generated PDF must contain Suspect Name");
            assertTrue(normalizedSummaryText.contains("Sailendra Spr"),
                    "Generated PDF must contain Recipient Name");
            assertTrue(normalizedSummaryText.contains("07d6a993a1bd339f01c749dd777c02e2"),
                    "Generated PDF must contain Uploaded File MD5");
        }
    }
}
