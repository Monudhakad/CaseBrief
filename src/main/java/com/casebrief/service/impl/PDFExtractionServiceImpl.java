package com.casebrief.service.impl;

import com.casebrief.service.PDFExtractionService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of PDFExtractionService using Apache PDFBox 3.x.
 * Extracts page-aware raw text without OCR.
 */
@Service
public class PDFExtractionServiceImpl implements PDFExtractionService {

    @Override
    public List<PageText> extractPageTexts(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            throw new IllegalArgumentException("PDF input stream cannot be null");
        }

        byte[] bytes = inputStream.readAllBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("PDF file is empty (0 bytes)");
        }

        List<PageText> pageTexts = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(bytes)) {
            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                throw new IllegalArgumentException("PDF document has no pages");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageContent = stripper.getText(document);
                pageTexts.add(new PageText(pageNum, pageContent != null ? pageContent : ""));
            }
        }

        return pageTexts;
    }
}
