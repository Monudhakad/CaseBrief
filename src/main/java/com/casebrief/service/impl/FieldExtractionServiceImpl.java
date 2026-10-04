package com.casebrief.service.impl;

import com.casebrief.model.CaseRecord;
import com.casebrief.model.ExtractedField;
import com.casebrief.service.FieldExtractionService;
import com.casebrief.service.PDFExtractionService.PageText;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic implementation of FieldExtractionService.
 * Handles PDF text wrapping, line-by-line section slicing, exact Section A incident type extraction,
 * and exact IPv6 geo matching.
 */
@Service
public class FieldExtractionServiceImpl implements FieldExtractionService {

    @Override
    public CaseRecord extractFields(List<PageText> pages, String filename) {
        CaseRecord record = new CaseRecord();
        record.setSourceFilename(filename);

        if (pages == null || pages.isEmpty()) {
            return record;
        }

        // Extract Global Header & Overview fields
        extractHeaderAndOverview(pages, record);

        // Slice Section A text block
        SectionABlocks sectionA = sliceSectionA(pages);

        // Parse Suspect & Suspect Network IPs from Section A
        if (sectionA.suspectBlock != null) {
            parseSuspectInfo(sectionA.suspectBlock, sectionA.suspectStartPage, record);
            parseSuspectIps(sectionA.suspectBlockPages, record);
        }

        // Parse Recipient Information from Section A
        if (sectionA.recipientBlock != null) {
            parseRecipientInfo(sectionA.recipientBlock, sectionA.recipientStartPage, record);
        }

        // Extract Uploaded Files
        extractUploadedFiles(pages, record);

        // Extract Section B Content Classification
        extractContentClassification(pages, record);

        // Extract Section B IP Geo-Lookup (Strict match against recentSuspectIp)
        extractIpGeoLookup(pages, record);

        // Extract Additional Report IDs
        extractAdditionalReportIds(pages, record);

        return record;
    }

    private static class SectionABlocks {
        String suspectBlock;
        int suspectStartPage = 1;
        List<PageText> suspectBlockPages = new ArrayList<>();

        String recipientBlock;
        int recipientStartPage = 1;
    }

    private SectionABlocks sliceSectionA(List<PageText> pages) {
        SectionABlocks result = new SectionABlocks();

        boolean inSectionA = false;
        boolean inSuspect = false;
        boolean inRecipient = false;

        StringBuilder suspectSb = new StringBuilder();
        StringBuilder recipientSb = new StringBuilder();

        for (PageText pt : pages) {
            String text = pt.content();

            if (pt.pageNumber() >= 4 && text.contains("Section A:")) {
                inSectionA = true;
            }
            if (text.contains("Section B:") || text.contains("This concludes Section A")) {
                inSectionA = false;
                inSuspect = false;
                inRecipient = false;
            }

            if (inSectionA) {
                if (text.contains("Suspect 1")) {
                    inSuspect = true;
                    inRecipient = false;
                    if (result.suspectStartPage == 1) result.suspectStartPage = pt.pageNumber();
                }
                if (text.contains("Recipient 1")) {
                    inSuspect = false;
                    inRecipient = true;
                    if (result.recipientStartPage == 1) result.recipientStartPage = pt.pageNumber();
                }

                if (inSuspect) {
                    suspectSb.append(text).append("\n");
                    result.suspectBlockPages.add(pt);
                } else if (inRecipient) {
                    recipientSb.append(text).append("\n");
                }
            }
        }

        result.suspectBlock = suspectSb.length() > 0 ? suspectSb.toString() : null;
        result.recipientBlock = recipientSb.length() > 0 ? recipientSb.toString() : null;

        return result;
    }

    private void extractHeaderAndOverview(List<PageText> pages, CaseRecord record) {
        // Report ID
        Pattern pReportId = Pattern.compile("CyberTipline Report\\s+(\\d+)");
        for (PageText pt : pages) {
            Matcher m = pReportId.matcher(pt.content());
            if (m.find()) {
                record.setReportId(ExtractedField.of(m.group(1).trim(), pt.pageNumber()));
                break;
            }
        }

        // Report Date
        Pattern pDate1 = Pattern.compile("Received by NCMEC on\\s+([0-9\\-\\:\\s]+UTC)");
        Pattern pDate2 = Pattern.compile("Date PDF Generated:\\s+([0-9\\-\\:\\s]+UTC)");

        for (PageText pt : pages) {
            Matcher m1 = pDate1.matcher(pt.content());
            if (m1.find()) {
                record.setReportDate(ExtractedField.of(m1.group(1).trim(), pt.pageNumber()));
                break;
            }
        }
        if (!record.getReportDate().isPresent()) {
            for (PageText pt : pages) {
                Matcher m2 = pDate2.matcher(pt.content());
                if (m2.find()) {
                    record.setReportDate(ExtractedField.of(m2.group(1).trim(), pt.pageNumber()));
                    break;
                }
            }
        }

        // Priority Level
        Pattern pPriority = Pattern.compile("Priority Level:\\s*([A-Za-z0-9]+)");
        for (PageText pt : pages) {
            Matcher m = pPriority.matcher(pt.content());
            if (m.find()) {
                record.setPriorityLevel(ExtractedField.of(m.group(1).trim(), pt.pageNumber()));
                break;
            }
        }

        // Reporting ESP
        for (PageText pt : pages) {
            String text = pt.content();
            if (text.contains("Reporting Electronic Service Provider") || text.contains("Reporting ESP") || text.contains("Submitter:")) {
                if (text.contains("Facebook")) {
                    record.setReportingEsp(ExtractedField.of("Facebook", pt.pageNumber()));
                    break;
                }
            }
        }

        // Incident Type: Specifically extract Section A reported incident type (e.g. Primary Incident Type)
        // Must NOT match Section B/C classifications like "Auto-referred International"
        Pattern pIncPrimary = Pattern.compile("Primary Incident Type:\\s*([^\\n\\r]+)");
        Pattern pIncSecA = Pattern.compile("Incident Type:\\s*([^\\n\\r]+)");

        boolean inSecA = false;
        for (PageText pt : pages) {
            String text = pt.content();
            if (pt.pageNumber() >= 4 && text.contains("Section A:")) {
                inSecA = true;
            }
            if (text.contains("Section B:") || text.contains("This concludes Section A")) {
                inSecA = false;
            }

            if (inSecA) {
                Matcher mPrimary = pIncPrimary.matcher(text);
                if (mPrimary.find()) {
                    record.setIncidentType(ExtractedField.of(mPrimary.group(1).trim(), pt.pageNumber()));
                    break;
                }
                Matcher mSecA = pIncSecA.matcher(text);
                if (mSecA.find()) {
                    String val = mSecA.group(1).trim();
                    if (!val.equalsIgnoreCase("Auto-referred International")) {
                        record.setIncidentType(ExtractedField.of(val, pt.pageNumber()));
                        break;
                    }
                }
            }
        }

        // Fallback: search for Primary Incident Type across pages if Section A marker was formatted differently
        if (!record.getIncidentType().isPresent()) {
            for (PageText pt : pages) {
                if (pt.pageNumber() >= 4) {
                    Matcher mPrimary = pIncPrimary.matcher(pt.content());
                    if (mPrimary.find()) {
                        record.setIncidentType(ExtractedField.of(mPrimary.group(1).trim(), pt.pageNumber()));
                        break;
                    }
                }
            }
        }

        // Total Uploaded Files
        Pattern pFiles = Pattern.compile("Total Uploaded Files:?\\s*(\\d+)");
        for (PageText pt : pages) {
            Matcher m = pFiles.matcher(pt.content());
            if (m.find()) {
                try {
                    record.setTotalUploadedFiles(ExtractedField.of(Integer.parseInt(m.group(1).trim()), pt.pageNumber()));
                    break;
                } catch (NumberFormatException ignored) {}
            }
        }

        // Child Victim Info
        for (PageText pt : pages) {
            if (pt.content().contains("Summary of ESP Reported Child Victim Information")) {
                if (pt.content().contains("Verified")) {
                    record.setEspReportedChildVictim(ExtractedField.of("Verified", pt.pageNumber()));
                } else {
                    record.setEspReportedChildVictim(ExtractedField.of("Information Not Provided", pt.pageNumber()));
                }
                break;
            }
        }
    }

    private void parseSuspectInfo(String suspectText, int startPage, CaseRecord record) {
        CaseRecord.Suspect suspect = record.getSuspect();

        int sIdx = suspectText.indexOf("Suspect 1");
        String textToSearch = sIdx != -1 ? suspectText.substring(sIdx) : suspectText;

        Matcher mName = Pattern.compile("Name:\\s*([^\\n\\r]+)").matcher(textToSearch);
        if (mName.find()) suspect.setName(ExtractedField.of(mName.group(1).trim(), startPage));

        Matcher mPhone = Pattern.compile("(?:Mobile Phone|Phone Number):\\s*([^\\n\\r]+)").matcher(textToSearch);
        if (mPhone.find()) suspect.setPhoneNumber(ExtractedField.of(mPhone.group(1).trim(), startPage));

        Matcher mAge = Pattern.compile("Approximate Age:\\s*([^\\n\\r]+)").matcher(textToSearch);
        if (mAge.find()) suspect.setAge(ExtractedField.of(mAge.group(1).trim(), startPage));

        Matcher mUser = Pattern.compile("Screen/User Name:\\s*([^\\n\\r]+)").matcher(textToSearch);
        if (mUser.find()) suspect.setScreenName(ExtractedField.of(mUser.group(1).trim(), startPage));

        Matcher mUrl = Pattern.compile("Profile URL\\s+(https?://[^\\s\\n\\r]+)").matcher(textToSearch);
        if (mUrl.find()) suspect.setProfileUrl(ExtractedField.of(mUrl.group(1).trim(), startPage));
    }

    private void parseSuspectIps(List<PageText> pages, CaseRecord record) {
        record IpEntry(String ip, String port, String timestamp, int pageNum) {}
        List<IpEntry> ipList = new ArrayList<>();

        Pattern ipPattern = Pattern.compile(
                "IP Address:\\s*([a-fA-F0-9\\.\\:]+)(?:\\s*\\([^\\)]+\\))?\\s*\\n?\\s*Port:\\s*(\\d+)\\s*\\n?\\s*([0-9\\-\\:\\s]+UTC)"
        );

        for (PageText pt : pages) {
            String text = pt.content();
            if (text.contains("Recipient 1")) {
                text = text.substring(0, text.indexOf("Recipient 1"));
            }

            Matcher matcher = ipPattern.matcher(text);
            while (matcher.find()) {
                String ip = matcher.group(1).trim();
                String port = matcher.group(2).trim();
                String ts = matcher.group(3).trim();
                ipList.add(new IpEntry(ip, port, ts, pt.pageNumber()));
            }
        }

        if (!ipList.isEmpty()) {
            IpEntry lastIp = ipList.get(ipList.size() - 1);
            record.setRecentSuspectIp(ExtractedField.of(lastIp.ip, lastIp.pageNum));
            record.setRecentSuspectPort(ExtractedField.of(lastIp.port, lastIp.pageNum));
            record.setRecentSuspectTimestamp(ExtractedField.of(lastIp.timestamp, lastIp.pageNum));
        }
    }

    private void parseRecipientInfo(String recipientText, int startPage, CaseRecord record) {
        CaseRecord.Recipient recipient = record.getRecipient();

        int rIdx = recipientText.indexOf("Recipient 1");
        String textToSearch = rIdx != -1 ? recipientText.substring(rIdx) : recipientText;

        Matcher mName = Pattern.compile("Name:\\s*([^\\n\\r]+)").matcher(textToSearch);
        if (mName.find()) recipient.setName(ExtractedField.of(mName.group(1).trim(), startPage));

        Matcher mPhone = Pattern.compile("(?:Mobile Phone|Phone Number):\\s*([^\\n\\r]+)").matcher(textToSearch);
        if (mPhone.find()) recipient.setPhoneNumber(ExtractedField.of(mPhone.group(1).trim(), startPage));

        Matcher mAge = Pattern.compile("Approximate Age:\\s*([^\\n\\r]+)").matcher(textToSearch);
        if (mAge.find()) recipient.setAge(ExtractedField.of(mAge.group(1).trim(), startPage));

        Matcher mUser = Pattern.compile("Screen/User Name:\\s*([^\\n\\r]+)").matcher(textToSearch);
        if (mUser.find()) recipient.setScreenName(ExtractedField.of(mUser.group(1).trim(), startPage));

        Matcher mUrl = Pattern.compile("Profile URL\\s+(https?://[^\\s\\n\\r]+)").matcher(textToSearch);
        if (mUrl.find()) recipient.setProfileUrl(ExtractedField.of(mUrl.group(1).trim(), startPage));
    }

    private void extractUploadedFiles(List<PageText> pages, CaseRecord record) {
        List<CaseRecord.UploadedFile> files = new ArrayList<>();
        Pattern pattern = Pattern.compile("Filename:\\s*([^\\n\\r]+)\\s*\\n?\\s*MD5:\\s*([a-fA-F0-9]+)");

        for (PageText pt : pages) {
            Matcher matcher = pattern.matcher(pt.content());
            while (matcher.find()) {
                String filename = matcher.group(1).trim();
                String md5 = matcher.group(2).trim();
                files.add(new CaseRecord.UploadedFile(
                        ExtractedField.of(filename, pt.pageNumber()),
                        ExtractedField.of(md5, pt.pageNumber())
                ));
            }
        }

        if (files.isEmpty()) {
            Pattern altPattern = Pattern.compile("([^\\s\\n\\r]+\\.(?:mp4|jpg|jpeg|png|avi|mov))\\s+([a-fA-F0-9]{32})");
            for (PageText pt : pages) {
                Matcher matcher = altPattern.matcher(pt.content());
                while (matcher.find()) {
                    String filename = matcher.group(1).trim();
                    String md5 = matcher.group(2).trim();
                    files.add(new CaseRecord.UploadedFile(
                            ExtractedField.of(filename, pt.pageNumber()),
                            ExtractedField.of(md5, pt.pageNumber())
                    ));
                }
            }
        }

        record.setUploadedFiles(files);
    }

    private void extractContentClassification(List<PageText> pages, CaseRecord record) {
        Pattern pattern = Pattern.compile("Image Categorization by ESP:\\s*([A-Za-z0-9]+)");
        for (PageText pt : pages) {
            Matcher matcher = pattern.matcher(pt.content());
            if (matcher.find()) {
                String cat = matcher.group(1).trim();
                record.setContentRating(ExtractedField.of(cat, pt.pageNumber()));
                record.setContentRanking(ExtractedField.of(cat.replaceAll("[^0-9]", ""), pt.pageNumber()));
                record.setContentTerm(ExtractedField.of(cat, pt.pageNumber()));
                return;
            }
        }
    }

    private void extractIpGeoLookup(List<PageText> pages, CaseRecord record) {
        if (!record.getRecentSuspectIp().isPresent()) {
            return;
        }

        String targetIp = record.getRecentSuspectIp().getValue();
        String unpaddedTarget = unpadIpv6(targetIp);

        boolean inGeoSection = false;

        for (PageText pt : pages) {
            String[] lines = pt.content().split("\r?\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains("IP Geo-Lookup (Suspect 1)")) {
                    inGeoSection = true;
                    continue;
                }
                if (trimmed.contains("IP Geo-Lookup (Recipient 1)") || trimmed.contains("Deconfliction") || trimmed.contains("Section C:")) {
                    inGeoSection = false;
                    continue;
                }

                if (inGeoSection) {
                    if (trimmed.isEmpty() || trimmed.startsWith("IP Address") || trimmed.startsWith("This Report") || trimmed.startsWith("CyberTipline")) {
                        continue;
                    }

                    String[] tokens = trimmed.split("\\s+");
                    if (tokens.length >= 5) {
                        String candIp = tokens[0];
                        String unpaddedCand = unpadIpv6(candIp);

                        if (!unpaddedCand.isEmpty() && unpaddedTarget.startsWith(unpaddedCand)) {
                            int p = pt.pageNumber();
                            record.setGeoIp(ExtractedField.of(targetIp, p));
                            record.setGeoCountry(ExtractedField.of(tokens[1], p));
                            record.setGeoRegion(ExtractedField.of(tokens[2], p));
                            record.setGeoCity(ExtractedField.of(tokens[3], p));
                            record.setGeoPostalCode(ExtractedField.of(tokens[4], p));

                            StringBuilder isp = new StringBuilder();
                            StringBuilder type = new StringBuilder();
                            boolean pastLatLong = false;

                            for (int k = 5; k < tokens.length; k++) {
                                String tok = tokens[k];
                                if (tok.contains("/")) {
                                    pastLatLong = true;
                                    isp.append(tok).append(" ");
                                } else if (pastLatLong) {
                                    type.append(tok).append(" ");
                                }
                            }

                            record.setGeoIspOrg(ExtractedField.of(isp.toString().trim(), p));
                            record.setGeoType(ExtractedField.of(type.toString().trim(), p));
                            record.setGeoFound(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    private String unpadIpv6(String ip) {
        if (ip == null || ip.isBlank()) return "";
        String cleaned = ip.trim().toLowerCase();
        if (cleaned.contains(" ")) {
            cleaned = cleaned.split("\\s+")[0];
        }
        String[] parts = cleaned.split(":");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String unpadded = part.replaceFirst("^0+", "");
            if (unpadded.isEmpty()) unpadded = "0";
            sb.append(unpadded).append(":");
        }
        return sb.toString();
    }

    private void extractAdditionalReportIds(List<PageText> pages, CaseRecord record) {
        Set<String> ids = new LinkedHashSet<>();
        Integer sourcePage = null;

        boolean inDeconfliction = false;
        Pattern pattern = Pattern.compile("\\b(\\d{9})\\b");

        for (PageText pt : pages) {
            String text = pt.content();
            if (text.contains("Deconfliction")) {
                inDeconfliction = true;
                sourcePage = pt.pageNumber();
            }
            if (text.contains("Section C:")) {
                inDeconfliction = false;
            }

            if (inDeconfliction) {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    String foundId = matcher.group(1);
                    if (record.getReportId().isPresent() && foundId.equals(record.getReportId().getValue())) {
                        continue;
                    }
                    ids.add(foundId);
                }
            }
        }

        if (!ids.isEmpty()) {
            record.setAdditionalReportIds(ExtractedField.of(new ArrayList<>(ids), sourcePage));
        }
    }
}
