package com.casebrief.service;

import com.casebrief.model.CaseRecord;
import com.casebrief.service.PDFExtractionService.PageText;

import java.util.List;

/**
 * Responsible for parsing raw page-aware texts into structured CaseRecord objects.
 */
public interface FieldExtractionService {

    /**
     * Extracts required CyberTipline fields deterministically from extracted page text list.
     *
     * @param pages list of page-aware text items
     * @param filename source PDF filename
     * @return populated CaseRecord
     */
    CaseRecord extractFields(List<PageText> pages, String filename);
}
