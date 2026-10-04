package com.casebrief.service.impl;

import com.casebrief.model.CaseRecord;
import com.casebrief.model.ExtractedField;
import com.casebrief.service.SummaryPdfService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;

/**
 * Generates a compact, professional 1-2 page Case Brief PDF from a CaseRecord.
 * Uses OpenPDF (LibrePDF) to render structured layouts, cards, and red lookup highlights.
 */
@Service
public class SummaryPdfServiceImpl implements SummaryPdfService {

    // Palette Tokens
    private static final Color COLOR_PRIMARY_NAVY = new Color(15, 42, 74);
    private static final Color COLOR_TEXT_DARK = new Color(33, 37, 41);
    private static final Color COLOR_HIGHLIGHT_RED = new Color(211, 47, 47);
    private static final Color COLOR_BG_LIGHT = new Color(245, 247, 250);
    private static final Color COLOR_BORDER = new Color(222, 226, 230);

    // Typography
    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, COLOR_PRIMARY_NAVY);
    private static final Font FONT_SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(108, 117, 125));
    private static final Font FONT_SECTION_HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARY_NAVY);
    private static final Font FONT_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, COLOR_TEXT_DARK);
    private static final Font FONT_VALUE = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, COLOR_TEXT_DARK);
    private static final Font FONT_VALUE_RED = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, COLOR_HIGHLIGHT_RED);
    private static final Font FONT_FOOTER = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.ITALIC, new Color(108, 117, 125));

    @Override
    public void generateSummaryPdf(CaseRecord record, OutputStream outputStream) throws Exception {
        if (record == null) {
            throw new IllegalArgumentException("CaseRecord cannot be null for PDF generation");
        }
        if (outputStream == null) {
            throw new IllegalArgumentException("Target OutputStream cannot be null");
        }

        Document document = new Document(PageSize.A4, 25, 25, 25, 25);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        // 1. Header Section
        addHeaderSection(document, record);

        // 2. Case Overview Grid
        addOverviewSection(document, record);

        // 3. Suspect & Recent Network Information
        addSuspectSection(document, record);

        // 4. Recipient Information
        addRecipientSection(document, record);

        // 5. Uploaded Files
        addUploadedFilesSection(document, record);

        // 6. Content Classification
        addContentClassificationSection(document, record);

        // 7. IP Geo-Location (with Red City & Postal Code Highlights)
        addGeoLookupSection(document, record);

        // 8. Additional Report IDs (Deconfliction)
        addAdditionalReportIdsSection(document, record);

        // 9. Footer Notice
        addFooterNotice(document);

        document.close();
    }

    private void addHeaderSection(Document document, CaseRecord record) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);

        String reportIdStr = formatField(record.getReportId());
        String reportDateStr = formatField(record.getReportDate());

        Paragraph pTitle = new Paragraph("CyberTipline Case Brief — Report #" + reportIdStr, FONT_TITLE);
        pTitle.setAlignment(Element.ALIGN_LEFT);

        Paragraph pDate = new Paragraph("Report Date: " + reportDateStr + "  |  Source File: " + (record.getSourceFilename() != null ? record.getSourceFilename() : "N/A"), FONT_SUBTITLE);
        pDate.setAlignment(Element.ALIGN_LEFT);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        cell.addElement(pTitle);
        cell.addElement(pDate);
        headerTable.addCell(cell);

        document.add(headerTable);

        // Underline separator
        LineSeparator line = new LineSeparator(1.5f, 100, COLOR_PRIMARY_NAVY, Element.ALIGN_CENTER, -1);
        document.add(line);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 2)));
    }

    private void addOverviewSection(Document document, CaseRecord record) throws DocumentException {
        PdfPTable table = createSectionTable("REPORT OVERVIEW");

        addTableRow(table, "Priority Level:", formatField(record.getPriorityLevel()), "Reporting ESP:", formatField(record.getReportingEsp()));
        // Full width row for Incident Type
        table.addCell(createLabelCell("Incident Type (Section A):"));
        PdfPCell incCell = createCell(formatField(record.getIncidentType()));
        incCell.setColspan(3);
        table.addCell(incCell);

        addTableRow(table, "Total Uploaded Files:", formatField(record.getTotalUploadedFiles()), "Child Victim Info:", formatField(record.getEspReportedChildVictim()));

        document.add(table);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 3)));
    }

    private void addSuspectSection(Document document, CaseRecord record) throws DocumentException {
        PdfPTable table = createSectionTable("SUSPECT & RECENT NETWORK INFORMATION");
        CaseRecord.Suspect s = record.getSuspect();

        addTableRow(table, "Suspect Name:", formatField(s.getName()), "Approximate Age:", formatField(s.getAge()));
        addTableRow(table, "Phone Number:", formatField(s.getPhoneNumber()), "Screen/User Name:", formatField(s.getScreenName()));

        // Profile URL full width
        table.addCell(createLabelCell("Profile URL:"));
        PdfPCell urlCell = createCell(formatField(s.getProfileUrl()));
        urlCell.setColspan(3);
        table.addCell(urlCell);

        // Recent Network Info Subheader
        PdfPCell subHeaderCell = new PdfPCell(new Phrase("Recent Network Information (Section A Last Entry)", FONT_LABEL));
        subHeaderCell.setColspan(4);
        subHeaderCell.setBackgroundColor(new Color(235, 240, 248));
        subHeaderCell.setPadding(3);
        table.addCell(subHeaderCell);

        // Full width row for Recent Suspect IP
        table.addCell(createLabelCell("Recent Suspect IP:"));
        PdfPCell ipCell = createCell(formatField(record.getRecentSuspectIp()));
        ipCell.setColspan(3);
        table.addCell(ipCell);

        addTableRow(table, "Port:", formatField(record.getRecentSuspectPort()), "Timestamp:", formatField(record.getRecentSuspectTimestamp()));

        document.add(table);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 3)));
    }

    private void addRecipientSection(Document document, CaseRecord record) throws DocumentException {
        PdfPTable table = createSectionTable("RECIPIENT INFORMATION");
        CaseRecord.Recipient r = record.getRecipient();

        addTableRow(table, "Recipient Name:", formatField(r.getName()), "Approximate Age:", formatField(r.getAge()));
        addTableRow(table, "Phone Number:", formatField(r.getPhoneNumber()), "Screen/User Name:", formatField(r.getScreenName()));

        table.addCell(createLabelCell("Profile URL:"));
        PdfPCell urlCell = createCell(formatField(r.getProfileUrl()));
        urlCell.setColspan(3);
        table.addCell(urlCell);

        document.add(table);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 3)));
    }

    private void addUploadedFilesSection(Document document, CaseRecord record) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{60, 40});

        PdfPCell titleCell = new PdfPCell(new Phrase("UPLOADED FILES", FONT_SECTION_HEADER));
        titleCell.setColspan(2);
        titleCell.setBackgroundColor(COLOR_BG_LIGHT);
        titleCell.setPadding(4);
        table.addCell(titleCell);

        table.addCell(createHeaderCell("Filename"));
        table.addCell(createHeaderCell("MD5 Hash"));

        if (record.getUploadedFiles().isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No uploaded files recorded", FONT_VALUE));
            emptyCell.setColspan(2);
            emptyCell.setPadding(4);
            table.addCell(emptyCell);
        } else {
            for (CaseRecord.UploadedFile uf : record.getUploadedFiles()) {
                table.addCell(createCell(formatField(uf.getFilename())));
                table.addCell(createCell(formatField(uf.getHash())));
            }
        }

        document.add(table);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 3)));
    }

    private void addContentClassificationSection(Document document, CaseRecord record) throws DocumentException {
        PdfPTable table = createSectionTable("CONTENT CLASSIFICATION");

        addTableRow(table, "Content Rating:", formatField(record.getContentRating()), "Content Ranking:", formatField(record.getContentRanking()));
        addTableRow(table, "Term:", formatField(record.getContentTerm()), "", "");

        document.add(table);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 3)));
    }

    private void addGeoLookupSection(Document document, CaseRecord record) throws DocumentException {
        PdfPTable table = createSectionTable("IP GEO-LOCATION (SUSPECT MATCH)");

        if (!record.isGeoFound()) {
            PdfPCell notFoundCell = new PdfPCell(new Phrase("Geo-location data unavailable for recent suspect IP", FONT_VALUE));
            notFoundCell.setColspan(4);
            notFoundCell.setPadding(5);
            table.addCell(notFoundCell);
        } else {
            // Full width row for Matched IP
            table.addCell(createLabelCell("Matched IP:"));
            PdfPCell ipCell = createCell(formatField(record.getGeoIp()));
            ipCell.setColspan(3);
            table.addCell(ipCell);

            addTableRow(table, "Country:", formatField(record.getGeoCountry()), "Region:", formatField(record.getGeoRegion()));

            // RED HIGHLIGHTED FIELDS: City & Postal Code
            table.addCell(createLabelCell("CITY (HIGHLIGHTED):"));
            table.addCell(createCellWithFont(formatField(record.getGeoCity()), FONT_VALUE_RED));

            table.addCell(createLabelCell("POSTAL CODE (HIGHLIGHTED):"));
            table.addCell(createCellWithFont(formatField(record.getGeoPostalCode()), FONT_VALUE_RED));

            addTableRow(table, "ISP / Organization:", formatField(record.getGeoIspOrg()), "Type:", formatField(record.getGeoType()));
        }

        document.add(table);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 3)));
    }

    private void addAdditionalReportIdsSection(Document document, CaseRecord record) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);

        PdfPCell titleCell = new PdfPCell(new Phrase("ADDITIONAL REPORT IDs (DECONFLICTIATION)", FONT_SECTION_HEADER));
        titleCell.setBackgroundColor(COLOR_BG_LIGHT);
        titleCell.setPadding(4);
        table.addCell(titleCell);

        String idsStr = "None";
        if (record.getAdditionalReportIds().isPresent() && !record.getAdditionalReportIds().getValue().isEmpty()) {
            idsStr = String.join(", ", record.getAdditionalReportIds().getValue());
        }

        PdfPCell contentCell = new PdfPCell(new Phrase(idsStr, FONT_VALUE));
        contentCell.setPadding(4);
        table.addCell(contentCell);

        document.add(table);
        document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 3)));
    }

    private void addFooterNotice(Document document) throws DocumentException {
        Paragraph pFooter = new Paragraph("Generated by CaseBrief — For official investigative review only. Confidential document.", FONT_FOOTER);
        pFooter.setAlignment(Element.ALIGN_CENTER);
        document.add(pFooter);
    }

    // Helper Methods for Table Styling
    private PdfPTable createSectionTable(String title) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{22, 28, 22, 28});

        PdfPCell titleCell = new PdfPCell(new Phrase(title, FONT_SECTION_HEADER));
        titleCell.setColspan(4);
        titleCell.setBackgroundColor(COLOR_BG_LIGHT);
        titleCell.setPadding(4);
        table.addCell(titleCell);

        return table;
    }

    private void addTableRow(PdfPTable table, String lbl1, String val1, String lbl2, String val2) {
        table.addCell(createLabelCell(lbl1));
        table.addCell(createCell(val1));
        table.addCell(createLabelCell(lbl2));
        table.addCell(createCell(val2));
    }

    private PdfPCell createLabelCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_LABEL));
        cell.setBackgroundColor(new Color(250, 252, 255));
        cell.setPadding(3);
        cell.setBorderColor(COLOR_BORDER);
        return cell;
    }

    private PdfPCell createCell(String text) {
        return createCellWithFont(text, FONT_VALUE);
    }

    private PdfPCell createCellWithFont(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(3);
        cell.setBorderColor(COLOR_BORDER);
        return cell;
    }

    private PdfPCell createHeaderCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_LABEL));
        cell.setBackgroundColor(new Color(235, 240, 248));
        cell.setPadding(3);
        cell.setBorderColor(COLOR_BORDER);
        return cell;
    }

    private <T> String formatField(ExtractedField<T> field) {
        if (field == null || !field.isPresent()) {
            return "N/A";
        }
        return field.getValue().toString();
    }
}
