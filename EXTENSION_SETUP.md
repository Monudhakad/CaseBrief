# CaseBrief Chrome Extension

The extension lives in `casebrief-extension/`. It keeps the existing Java extraction and export services as the single source of truth. The extension does not send documents to a cloud service or external AI API.

## Current Architecture

The Spring Boot application exposes the existing REST API through `CaseController` and delegates work to `DocumentProcessingService`, `SummaryPdfService`, `BatchDownloadService`, and `ExcelExportService`. The native host starts the same Spring beans without starting an HTTP server and calls those services directly.

The extension contains a Manifest V3 popup and service worker. In native mode, the service worker connects to `com.casebrief.native`; the host frames messages over stdin/stdout and returns JSON or generated files to the popup. Development-server mode is separate and explicitly selected in the popup.

## Development Setup

Prerequisites for developers are Chrome, Java 21 or newer, Maven, and PowerShell. End users of a packaged native host do not need Maven or a separate Java installation.

1. Start the existing backend when using development-server mode:

   ```powershell
   $env:CASEBRIEF_EXTENSION_DEV_ORIGIN = "chrome-extension://kgblcnakcckleijbjknldipphmkficma"
   mvn spring-boot:run
   ```

2. In Chrome, open `chrome://extensions`, turn on Developer mode, and choose **Load unpacked**. Select the `casebrief-extension` folder.
3. In the CaseBrief popup, select **Development server**. Chrome asks for the optional localhost permission; grant it for `localhost:8080`.
4. Use **Single PDF**, **Bulk PDFs**, or **ZIP**. The development mode calls the existing `/api/cases/*` endpoints directly.

CORS is disabled by default. It is enabled only when `CASEBRIEF_EXTENSION_DEV_ORIGIN` is a single valid `chrome-extension://<32-character-id>` origin. No wildcard origin or external host permission is used.

To package a development extension zip:

```powershell
.\casebrief-extension\scripts\Package-Extension.ps1
```

The zip is written to `dist\casebrief-extension.zip`.

## Native Messaging Contract

All requests use one of these exact action names:

- `PROCESS_SINGLE`
- `PROCESS_BULK`
- `PROCESS_ZIP`
- `GENERATE_SUMMARY_PDF`
- `GENERATE_BATCH_ZIP`
- `GENERATE_EXCEL`

Every request and response includes `requestId`. The response envelope is always:

```json
{
  "requestId": "uuid",
  "success": true,
  "data": {},
  "error": null
}
```

File actions start with a manifest frame and then send base64 chunks. The action remains the same for every chunk. Bulk frames include `fileIndex` and `fileCount`; all file frames include `filename`, `chunkIndex`, `chunkCount`, and `data`.

```json
{
  "action": "PROCESS_BULK",
  "requestId": "uuid",
  "fileIndex": 0,
  "fileCount": 2,
  "filename": "report.pdf",
  "chunkIndex": 0,
  "chunkCount": 1,
  "data": "JVBERi0x..."
}
```

The service worker sends chunks sequentially and waits for an acknowledgement. Chunks are 48 KiB before base64 encoding to stay below Native Messaging frame limits. The host verifies declared file size, count, order, extension, and request ID, writes only to a private temporary directory, and removes the directory after completion. Supported limits are 200 MiB per file, 500 MiB per batch, and 100 files per bulk request.

During bulk/ZIP processing, the host emits progress envelopes only after the Java service records each document’s outcome. The popup displays the actual `completed / total` count. Final `PROCESS_BULK` and `PROCESS_ZIP` data is the existing `BulkProcessingResult`, including both `successfulRecords` and `failedRecords`. Failures remain isolated by the existing Java service.

`GENERATE_SUMMARY_PDF` accepts a `record`; `GENERATE_BATCH_ZIP` and `GENERATE_EXCEL` accept a `result`. Generated binary data is returned as ordered `binary-chunk` envelopes and assembled by the popup into a Blob. Processing results are returned as ordered `json-chunk` envelopes. Errors use `success: false`, `data: null`, and an `{code, message}` error object.

The native host exposes only the six listed operations. It does not accept commands, class names, filesystem paths, or arbitrary Java method names from the extension. Document contents are not written to logs.

## Native Host Packaging

For a developer build, create the Windows app image with the JDK’s `jpackage`:

```powershell
.\casebrief-extension\scripts\Package-NativeHost.ps1
```

This runs the Maven package build and writes an app image, including a bundled Java runtime, to `target\native-host-dist\CaseBriefNativeHost`. The launcher starts `NativeMessagingHost` through Spring Boot’s `PropertiesLauncher` with `WebApplicationType.NONE`; it does not open port 8080.

Install Inno Setup on the release/build machine and compile `casebrief-extension\installer\CaseBrief.iss` to produce a per-user Windows installer. It installs the app image under the user’s Local AppData, writes a Native Messaging manifest containing the resolved executable path, and registers `com.casebrief.native` under:

```text
HKCU\Software\Google\Chrome\NativeMessagingHosts\com.casebrief.native
```

The installer does not need administrator access. `Install-NativeHost.ps1` is available for developer/manual registration; normal users should use the installer. The native manifest uses `type: "stdio"` and one exact extension origin.

Chrome controls extension installation and signing. Production distribution should publish the signed extension through the Chrome Web Store and ship the native-host installer alongside it. The example `manifest.json` key and extension ID in this repository are development-only; the generated private key was not retained. Before release, replace the manifest key with the public key belonging to the release signing key, update `ExtensionId` in `CaseBrief.iss` and the native-host manifest, then rebuild both artifacts. Never use `*` in `allowed_origins`.

## Final Investigator Workflow

1. Install the CaseBrief extension from its approved Chrome distribution channel.
2. Run the CaseBrief Native Host installer once. It registers the host and bundles the Java runtime.
3. Open the CaseBrief toolbar popup and leave **Native host** selected.
4. Choose a PDF, several PDFs, or a ZIP archive and process it. Files remain on the device; no local Spring server is required.
5. Review each success or failure and download individual Summary PDFs, the successful batch Summary ZIP, the Excel workbook, or the full JSON status.

The existing web application, controllers, feedback endpoint, services, and data models remain available and unchanged except for the opt-in progress-listener overload on bulk/ZIP orchestration. Existing routes and behavior are preserved.
