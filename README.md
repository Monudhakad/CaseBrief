# CaseBrief

CaseBrief is a Java Spring Boot application with a Chrome Extension and Native Messaging Host for processing structured PDF case reports.

It reduces manual effort by extracting predefined fields from PDF reports, generating compact Case Brief PDFs, and exporting structured information to Excel.

## Features

- Single PDF processing
- Bulk PDF processing
- Structured field extraction
- Compact Case Brief PDF generation
- Excel export
- Chrome Extension interface
- Chrome Native Messaging integration
- Feedback functionality
- Automated tests

## Architecture

```text
Chrome Extension
       |
       v
Native Messaging Host
       |
       v
Spring Boot Backend
       |
       +--> PDF Extraction
       |
       +--> Field Extraction
       |
       v
    CaseRecord
       |
       +--> Summary PDF
       |
       +--> Excel Export

Technology Stack
- Java
- Spring Boot
- Maven
- Apache PDFBox
- OpenPDF
- Apache POI
- Chrome Extension
- Chrome Native Messaging
- PowerShell
- Inno Setup
- JUnit / Spring Boot Test

Project Structure
CaseBrief/
├── casebrief-extension/
│   ├── assets/
│   ├── installer/
│   ├── native-host/
│   ├── popup/
│   ├── scripts/
│   ├── manifest.json
│   └── service-worker.js
│
├── src/
│   ├── main/
│   │   ├── java/com/casebrief/
│   │   │   ├── controller/
│   │   │   ├── model/
│   │   │   ├── nativehost/
│   │   │   └── service/
│   │   └── resources/
│   │
│   └── test/
│
├── pom.xml
├── EXTENSION_SETUP.md
├── .gitignore
└── README.md

Running the Backend

Make sure Java and Maven are installed.
mvn clean test

Run the application:
mvn spring-boot:run

The backend runs on:
http://localhost:8080

Testing

Run the complete test suite:
mvn test

Chrome Extension

The Chrome Extension communicates with the local Java application through Chrome Native Messaging.
Installation and packaging instructions are available in:
EXTENSION_SETUP.md

Release scripts are located in:
casebrief-extension/scripts/

Privacy and Data

Real investigation or tipline documents should never be committed to this repository.
Generated files, build output, release packages, and local case data are excluded through .gitignore.
This project should only be used with sensitive investigation data when appropriate authorization and organizational security controls are in place.

Roadmap
- Improved document handling
- Additional structured extraction rules
- Improved deployment workflow
- Additional reporting formats
- Further document-processing automation

Author
Developed as a cybersecurity internship project.
