package com.casebrief;

import com.casebrief.service.PDFExtractionService;
import com.casebrief.service.impl.PDFExtractionServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PDFExtractionTest {

    @Test
    public void testExtractPageTexts() throws Exception {
        PDFExtractionService extractionService = new PDFExtractionServiceImpl();
        InputStream is = getClass().getResourceAsStream("/243063858.pdf");
        assertNotNull(is, "Reference PDF 243063858.pdf should be present in test resources");

        List<PDFExtractionService.PageText> pages = extractionService.extractPageTexts(is);
        assertFalse(pages.isEmpty(), "Extracted pages should not be empty");

        for (int i = 0; i < Math.min(5, pages.size()); i++) {
            System.out.println("=== PAGE " + pages.get(i).pageNumber() + " ===");
            System.out.println(pages.get(i).content());
        }
    }
}
