package com.casebrief.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Central structured data model representing an extracted Case Brief.
 */
public class CaseRecord {

    // Source PDF Metadata
    private String sourceFilename;

    // Header & Report Overview
    private ExtractedField<String> reportId = ExtractedField.unknown();
    private ExtractedField<String> reportDate = ExtractedField.unknown();
    private ExtractedField<String> priorityLevel = ExtractedField.unknown();
    private ExtractedField<String> espReportedChildVictim = ExtractedField.unknown();
    private ExtractedField<Integer> totalUploadedFiles = ExtractedField.unknown();
    private ExtractedField<String> reportingEsp = ExtractedField.unknown();
    private ExtractedField<String> incidentType = ExtractedField.unknown();

    // Suspect
    private Suspect suspect = new Suspect();

    // Recent Suspect Network Information (LAST IP entry in Section A)
    private ExtractedField<String> recentSuspectIp = ExtractedField.unknown();
    private ExtractedField<String> recentSuspectPort = ExtractedField.unknown();
    private ExtractedField<String> recentSuspectTimestamp = ExtractedField.unknown();

    // Recipient
    private Recipient recipient = new Recipient();

    // Uploaded Files
    private List<UploadedFile> uploadedFiles = new ArrayList<>();

    // Section B Content Classification
    private ExtractedField<String> contentRating = ExtractedField.unknown();
    private ExtractedField<String> contentRanking = ExtractedField.unknown();
    private ExtractedField<String> contentTerm = ExtractedField.unknown();

    // Section B Matched IP Geo-Lookup (Strict match against recentSuspectIp)
    private ExtractedField<String> geoIp = ExtractedField.unknown();
    private ExtractedField<String> geoCountry = ExtractedField.unknown();
    private ExtractedField<String> geoRegion = ExtractedField.unknown();
    private ExtractedField<String> geoCity = ExtractedField.unknown();
    private ExtractedField<String> geoPostalCode = ExtractedField.unknown();
    private ExtractedField<String> geoIspOrg = ExtractedField.unknown();
    private ExtractedField<String> geoType = ExtractedField.unknown();
    private boolean geoFound = false;

    // Deconfliction
    private ExtractedField<List<String>> additionalReportIds = ExtractedField.of(new ArrayList<>(), null);

    public CaseRecord() {
    }

    // Nested Suspect class
    public static class Suspect {
        private ExtractedField<String> name = ExtractedField.unknown();
        private ExtractedField<String> age = ExtractedField.unknown();
        private ExtractedField<String> phoneNumber = ExtractedField.unknown();
        private ExtractedField<String> screenName = ExtractedField.unknown();
        private ExtractedField<String> profileUrl = ExtractedField.unknown();

        public ExtractedField<String> getName() { return name; }
        public void setName(ExtractedField<String> name) { this.name = name; }

        public ExtractedField<String> getAge() { return age; }
        public void setAge(ExtractedField<String> age) { this.age = age; }

        public ExtractedField<String> getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(ExtractedField<String> phoneNumber) { this.phoneNumber = phoneNumber; }

        public ExtractedField<String> getScreenName() { return screenName; }
        public void setScreenName(ExtractedField<String> screenName) { this.screenName = screenName; }

        public ExtractedField<String> getProfileUrl() { return profileUrl; }
        public void setProfileUrl(ExtractedField<String> profileUrl) { this.profileUrl = profileUrl; }
    }

    // Nested Recipient class
    public static class Recipient {
        private ExtractedField<String> name = ExtractedField.unknown();
        private ExtractedField<String> age = ExtractedField.unknown();
        private ExtractedField<String> phoneNumber = ExtractedField.unknown();
        private ExtractedField<String> screenName = ExtractedField.unknown();
        private ExtractedField<String> profileUrl = ExtractedField.unknown();

        public ExtractedField<String> getName() { return name; }
        public void setName(ExtractedField<String> name) { this.name = name; }

        public ExtractedField<String> getAge() { return age; }
        public void setAge(ExtractedField<String> age) { this.age = age; }

        public ExtractedField<String> getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(ExtractedField<String> phoneNumber) { this.phoneNumber = phoneNumber; }

        public ExtractedField<String> getScreenName() { return screenName; }
        public void setScreenName(ExtractedField<String> screenName) { this.screenName = screenName; }

        public ExtractedField<String> getProfileUrl() { return profileUrl; }
        public void setProfileUrl(ExtractedField<String> profileUrl) { this.profileUrl = profileUrl; }
    }

    // Nested UploadedFile class
    public static class UploadedFile {
        private ExtractedField<String> filename = ExtractedField.unknown();
        private ExtractedField<String> hash = ExtractedField.unknown();

        public UploadedFile() {}

        public UploadedFile(ExtractedField<String> filename, ExtractedField<String> hash) {
            this.filename = filename;
            this.hash = hash;
        }

        public ExtractedField<String> getFilename() { return filename; }
        public void setFilename(ExtractedField<String> filename) { this.filename = filename; }

        public ExtractedField<String> getHash() { return hash; }
        public void setHash(ExtractedField<String> hash) { this.hash = hash; }
    }

    // Getters and Setters
    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }

    public ExtractedField<String> getReportId() { return reportId; }
    public void setReportId(ExtractedField<String> reportId) { this.reportId = reportId; }

    public ExtractedField<String> getReportDate() { return reportDate; }
    public void setReportDate(ExtractedField<String> reportDate) { this.reportDate = reportDate; }

    public ExtractedField<String> getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(ExtractedField<String> priorityLevel) { this.priorityLevel = priorityLevel; }

    public ExtractedField<String> getEspReportedChildVictim() { return espReportedChildVictim; }
    public void setEspReportedChildVictim(ExtractedField<String> espReportedChildVictim) { this.espReportedChildVictim = espReportedChildVictim; }

    public ExtractedField<Integer> getTotalUploadedFiles() { return totalUploadedFiles; }
    public void setTotalUploadedFiles(ExtractedField<Integer> totalUploadedFiles) { this.totalUploadedFiles = totalUploadedFiles; }

    public ExtractedField<String> getReportingEsp() { return reportingEsp; }
    public void setReportingEsp(ExtractedField<String> reportingEsp) { this.reportingEsp = reportingEsp; }

    public ExtractedField<String> getIncidentType() { return incidentType; }
    public void setIncidentType(ExtractedField<String> incidentType) { this.incidentType = incidentType; }

    public Suspect getSuspect() { return suspect; }
    public void setSuspect(Suspect suspect) { this.suspect = suspect; }

    public ExtractedField<String> getRecentSuspectIp() { return recentSuspectIp; }
    public void setRecentSuspectIp(ExtractedField<String> recentSuspectIp) { this.recentSuspectIp = recentSuspectIp; }

    public ExtractedField<String> getRecentSuspectPort() { return recentSuspectPort; }
    public void setRecentSuspectPort(ExtractedField<String> recentSuspectPort) { this.recentSuspectPort = recentSuspectPort; }

    public ExtractedField<String> getRecentSuspectTimestamp() { return recentSuspectTimestamp; }
    public void setRecentSuspectTimestamp(ExtractedField<String> recentSuspectTimestamp) { this.recentSuspectTimestamp = recentSuspectTimestamp; }

    public Recipient getRecipient() { return recipient; }
    public void setRecipient(Recipient recipient) { this.recipient = recipient; }

    public List<UploadedFile> getUploadedFiles() { return uploadedFiles; }
    public void setUploadedFiles(List<UploadedFile> uploadedFiles) { this.uploadedFiles = uploadedFiles; }

    public ExtractedField<String> getContentRating() { return contentRating; }
    public void setContentRating(ExtractedField<String> contentRating) { this.contentRating = contentRating; }

    public ExtractedField<String> getContentRanking() { return contentRanking; }
    public void setContentRanking(ExtractedField<String> contentRanking) { this.contentRanking = contentRanking; }

    public ExtractedField<String> getContentTerm() { return contentTerm; }
    public void setContentTerm(ExtractedField<String> contentTerm) { this.contentTerm = contentTerm; }

    public ExtractedField<String> getGeoIp() { return geoIp; }
    public void setGeoIp(ExtractedField<String> geoIp) { this.geoIp = geoIp; }

    public ExtractedField<String> getGeoCountry() { return geoCountry; }
    public void setGeoCountry(ExtractedField<String> geoCountry) { this.geoCountry = geoCountry; }

    public ExtractedField<String> getGeoRegion() { return geoRegion; }
    public void setGeoRegion(ExtractedField<String> geoRegion) { this.geoRegion = geoRegion; }

    public ExtractedField<String> getGeoCity() { return geoCity; }
    public void setGeoCity(ExtractedField<String> geoCity) { this.geoCity = geoCity; }

    public ExtractedField<String> getGeoPostalCode() { return geoPostalCode; }
    public void setGeoPostalCode(ExtractedField<String> geoPostalCode) { this.geoPostalCode = geoPostalCode; }

    public ExtractedField<String> getGeoIspOrg() { return geoIspOrg; }
    public void setGeoIspOrg(ExtractedField<String> geoIspOrg) { this.geoIspOrg = geoIspOrg; }

    public ExtractedField<String> getGeoType() { return geoType; }
    public void setGeoType(ExtractedField<String> geoType) { this.geoType = geoType; }

    public boolean isGeoFound() { return geoFound; }
    public void setGeoFound(boolean geoFound) { this.geoFound = geoFound; }

    public ExtractedField<List<String>> getAdditionalReportIds() { return additionalReportIds; }
    public void setAdditionalReportIds(ExtractedField<List<String>> additionalReportIds) { this.additionalReportIds = additionalReportIds; }
}
