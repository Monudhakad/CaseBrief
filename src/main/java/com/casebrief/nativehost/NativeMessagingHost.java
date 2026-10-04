package com.casebrief.nativehost;

import com.casebrief.CaseBriefApplication;
import com.casebrief.model.BulkProcessingResult;
import com.casebrief.model.CaseRecord;
import com.casebrief.model.ExtractedField;
import com.casebrief.service.BatchDownloadService;
import com.casebrief.service.DocumentProcessingService;
import com.casebrief.service.DocumentProcessingService.BatchProgressListener;
import com.casebrief.service.ExcelExportService;
import com.casebrief.service.SummaryPdfService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/** Native Messaging entry point. Stdout is reserved for Chrome's framed protocol. */
public final class NativeMessagingHost {
    private static final int MAX_FRAME_BYTES = 1024 * 1024;
    private static final int CHUNK_BYTES = 48 * 1024;
    private static final int MAX_CHUNKS_PER_FILE = 50000;
    private static final int MAX_FILES = 1000;
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long MAX_BATCH_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Pattern REQUEST_ID = Pattern.compile("[0-9a-fA-F-]{36}");
    private static final Set<String> ACTIONS = Set.of(
            "PROCESS_SINGLE", "PROCESS_BULK", "PROCESS_ZIP",
            "GENERATE_SUMMARY_PDF", "GENERATE_BATCH_ZIP", "GENERATE_EXCEL");

    private final ObjectMapper mapper;
    private final OutputStream protocolOutput;
    private final DocumentProcessingService documentProcessingService;
    private final SummaryPdfService summaryPdfService;
    private final BatchDownloadService batchDownloadService;
    private final ExcelExportService excelExportService;
    private final Map<String, UploadSession> uploads = new HashMap<>();

    private NativeMessagingHost(ConfigurableApplicationContext context, OutputStream protocolOutput) {
        this.mapper = context.getBean(ObjectMapper.class);
        this.protocolOutput = protocolOutput;
        this.documentProcessingService = context.getBean(DocumentProcessingService.class);
        this.summaryPdfService = context.getBean(SummaryPdfService.class);
        this.batchDownloadService = context.getBean(BatchDownloadService.class);
        this.excelExportService = context.getBean(ExcelExportService.class);
    }

    public static void main(String[] args) throws Exception {
        OutputStream protocolOutput = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out));
        System.setOut(System.err);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(CaseBriefApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run(args)) {
            new NativeMessagingHost(context, protocolOutput).run();
        }
    }

    private void run() throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(System.in))) {
            while (true) {
                byte[] frame = readFrame(input);
                if (frame == null) return;
                String requestId = null;
                try {
                    JsonNode request = mapper.readTree(frame);
                    requestId = text(request, "requestId");
                    handle(request);
                } catch (Exception e) {
                    cleanupUpload(requestId);
                    sendError(requestId, "INVALID_REQUEST", safeMessage(e));
                }
            }
        } finally {
            for (String requestId : List.copyOf(uploads.keySet())) cleanupUpload(requestId);
        }
    }

    private void handle(JsonNode request) throws Exception {
        if (request == null || !request.isObject()) throw new IllegalArgumentException("Message must be a JSON object.");
        String action = requiredText(request, "action");
        String requestId = requiredText(request, "requestId");
        if (!ACTIONS.contains(action)) throw new IllegalArgumentException("Unsupported action.");
        if (!REQUEST_ID.matcher(requestId).matches()) throw new IllegalArgumentException("Invalid request ID.");

        switch (action) {
            case "PROCESS_SINGLE", "PROCESS_BULK", "PROCESS_ZIP" -> {
                if (request.has("files")) beginUpload(request, action, requestId);
                else acceptChunk(request, action, requestId);
            }
            case "GENERATE_SUMMARY_PDF" -> generateSummary(request, requestId);
            case "GENERATE_BATCH_ZIP" -> generateBatchZip(request, requestId);
            case "GENERATE_EXCEL" -> generateExcel(request, requestId);
            default -> throw new IllegalArgumentException("Unsupported action.");
        }
    }

    private void beginUpload(JsonNode request, String action, String requestId) throws IOException {
        if (!uploads.isEmpty()) throw new IllegalArgumentException("Another file-processing request is active.");
        if (uploads.containsKey(requestId)) throw new IllegalArgumentException("Request ID is already active.");
        JsonNode filesNode = request.get("files");
        if (filesNode == null || !filesNode.isArray() || filesNode.isEmpty() || filesNode.size() > MAX_FILES) {
            throw new IllegalArgumentException("File list is empty or exceeds the file-count limit.");
        }
        if ((action.equals("PROCESS_SINGLE") || action.equals("PROCESS_ZIP")) && filesNode.size() != 1) {
            throw new IllegalArgumentException("This action accepts exactly one file.");
        }

        List<FileInput> files = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        long totalBytes = 0;
        for (JsonNode node : filesNode) {
            String filename = requiredText(node, "filename");
            long size = requiredLong(node, "sizeBytes");
            validateFilename(filename, action);
            if (size < 1 || size > MAX_FILE_BYTES) throw new IllegalArgumentException("File size is outside the supported limit.");
            totalBytes = Math.addExact(totalBytes, size);
            files.add(new FileInput(filename, uniqueName(filename, usedNames), size));
        }
        if (totalBytes > MAX_BATCH_BYTES) throw new IllegalArgumentException("Batch size exceeds the supported limit.");

        Path tempDirectory = Files.createTempDirectory("casebrief-native-");
        uploads.put(requestId, new UploadSession(action, files, tempDirectory));
    }

    private void acceptChunk(JsonNode request, String action, String requestId) throws Exception {
        UploadSession session = uploads.get(requestId);
        if (session == null || !session.action.equals(action)) throw new IllegalArgumentException("No matching upload is active.");
        int fileIndex = requiredInt(request, "fileIndex");
        int fileCount = requiredInt(request, "fileCount");
        int chunkIndex = requiredInt(request, "chunkIndex");
        int chunkCount = requiredInt(request, "chunkCount");
        if (fileCount != session.files.size() || fileIndex != session.nextFileIndex ||
                fileIndex < 0 || fileIndex >= session.files.size()) throw new IllegalArgumentException("Unexpected file index.");

        FileInput file = session.files.get(fileIndex);
        if (!file.filename.equals(requiredText(request, "filename"))) throw new IllegalArgumentException("Filename does not match the upload manifest.");
        int expectedChunkCount = Math.toIntExact((file.sizeBytes + CHUNK_BYTES - 1) / CHUNK_BYTES);
        if (chunkCount != expectedChunkCount || chunkCount > MAX_CHUNKS_PER_FILE || chunkIndex != session.nextChunkIndex) {
            throw new IllegalArgumentException("Unexpected chunk sequence.");
        }
        if (session.currentOutput == null) {
            session.currentPath = session.tempDirectory.resolve("input-" + fileIndex + ".bin").normalize();
            if (!session.currentPath.startsWith(session.tempDirectory)) throw new SecurityException("Invalid temporary file path.");
            session.currentOutput = Files.newOutputStream(session.currentPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }

        String encoded = requiredText(request, "data");
        if (encoded.length() > 96 * 1024) throw new IllegalArgumentException("Chunk exceeds the encoded size limit.");
        byte[] bytes = Base64.getDecoder().decode(encoded);
        int expectedLength = (int) Math.min(CHUNK_BYTES, file.sizeBytes - session.currentFileBytes);
        if (bytes.length != expectedLength) throw new IllegalArgumentException("Chunk size does not match the declared file size.");
        session.currentOutput.write(bytes);
        session.currentFileBytes += bytes.length;
        session.currentTotalBytes += bytes.length;
        session.nextChunkIndex++;
        boolean finalChunk = chunkIndex == chunkCount - 1;
        send(requestId, true, Map.of("kind", "ack", "fileIndex", fileIndex, "chunkIndex", chunkIndex), null);

        if (!finalChunk) return;
        session.currentOutput.close();
        session.currentOutput = null;
        if (session.currentFileBytes != file.sizeBytes) throw new IllegalArgumentException("Uploaded byte count does not match the declared file size.");
        session.stagedFiles.add(session.currentPath);
        session.currentPath = null;
        session.currentFileBytes = 0;
        session.nextChunkIndex = 0;
        session.nextFileIndex++;

        if (session.nextFileIndex == session.files.size()) {
            uploads.remove(requestId);
            try {
                processUpload(requestId, session);
            } finally {
                session.closeAndDelete();
            }
        }
    }

    private void processUpload(String requestId, UploadSession session) throws Exception {
        if (session.action.equals("PROCESS_SINGLE")) {
            FileInput file = session.files.getFirst();
            try (InputStream input = Files.newInputStream(session.stagedFiles.getFirst())) {
                sendJson(requestId, documentProcessingService.processSinglePdf(input, file.processingName));
            }
            return;
        }
        if (session.action.equals("PROCESS_ZIP")) {
            try (InputStream input = Files.newInputStream(session.stagedFiles.getFirst())) {
                BulkProcessingResult result = documentProcessingService.processZipStream(input,
                        progressListener(requestId));
                sendJson(requestId, result);
            }
            return;
        }

        Map<String, InputStream> streams = new LinkedHashMap<>();
        List<InputStream> opened = new ArrayList<>();
        try {
            for (int index = 0; index < session.files.size(); index++) {
                InputStream input = Files.newInputStream(session.stagedFiles.get(index));
                opened.add(input);
                streams.put(session.files.get(index).processingName, input);
            }
            BulkProcessingResult result = documentProcessingService.processBulkPdfs(streams, progressListener(requestId));
            sendJson(requestId, result);
        } finally {
            for (InputStream input : opened) {
                try { input.close(); } catch (IOException ignored) { }
            }
        }
    }

    private BatchProgressListener progressListener(String requestId) {
        int[] counts = new int[2];
        return (completed, total, filename, successful) -> {
            if (successful) counts[0]++; else counts[1]++;
            Map<String, Object> progress = new LinkedHashMap<>();
            progress.put("kind", "progress");
            progress.put("completed", completed);
            progress.put("total", total);
            progress.put("filename", filename);
            progress.put("successful", successful);
            progress.put("successfulCount", counts[0]);
            progress.put("failedCount", counts[1]);
            send(requestId, true, progress, null);
        };
    }

    private void generateSummary(JsonNode request, String requestId) throws Exception {
        JsonNode node = request.get("record");
        if (node == null || !node.isObject()) throw new IllegalArgumentException("CaseRecord is required.");
        CaseRecord record = mapper.treeToValue(node, CaseRecord.class);
        if (record.getSuspect() == null) record.setSuspect(new CaseRecord.Suspect());
        if (record.getRecipient() == null) record.setRecipient(new CaseRecord.Recipient());
        if (record.getUploadedFiles() == null) record.setUploadedFiles(new ArrayList<>());
        if (record.getAdditionalReportIds() == null) record.setAdditionalReportIds(ExtractedField.of(new ArrayList<>(), null));
        Path output = Files.createTempFile("casebrief-summary-", ".pdf");
        try {
            try (OutputStream stream = Files.newOutputStream(output)) {
                summaryPdfService.generateSummaryPdf(record, stream);
            }
            sendBinary(requestId, output, "application/pdf", safeReportName(record) + "_Summary_Brief.pdf");
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private void generateBatchZip(JsonNode request, String requestId) throws Exception {
        BulkProcessingResult result = readBatchResult(request);
        Path output = Files.createTempFile("casebrief-summaries-", ".zip");
        try {
            try (OutputStream stream = Files.newOutputStream(output)) {
                batchDownloadService.createSummaryZip(result, stream);
            }
            sendBinary(requestId, output, "application/zip", "CaseBrief_Summaries.zip");
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private void generateExcel(JsonNode request, String requestId) throws Exception {
        BulkProcessingResult result = readBatchResult(request);
        Path output = Files.createTempFile("casebrief-excel-", ".xlsx");
        try {
            try (OutputStream stream = Files.newOutputStream(output)) {
                excelExportService.exportBatchResult(result, stream);
            }
            sendBinary(requestId, output,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "CaseBrief_Batch_Report.xlsx");
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private BulkProcessingResult readBatchResult(JsonNode request) throws IOException {
        JsonNode node = request.get("result");
        if (node == null || !node.isObject()) throw new IllegalArgumentException("BulkProcessingResult is required.");
        BulkProcessingResult result = mapper.treeToValue(node, BulkProcessingResult.class);
        if (result.getSuccessfulRecords() == null) result.setSuccessfulRecords(new ArrayList<>());
        if (result.getFailedRecords() == null) result.setFailedRecords(new ArrayList<>());
        if (result.getSuccessfulRecords().size() > MAX_FILES || result.getFailedRecords().size() > MAX_FILES) {
            throw new IllegalArgumentException("Export record count exceeds the supported limit.");
        }
        return result;
    }

    private void sendJson(String requestId, Object result) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(result);
        int chunkCount = Math.max(1, (bytes.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        for (int index = 0; index < chunkCount; index++) {
            int start = index * CHUNK_BYTES;
            int end = Math.min(start + CHUNK_BYTES, bytes.length);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("kind", "json-chunk");
            data.put("chunkIndex", index);
            data.put("chunkCount", chunkCount);
            data.put("base64", Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(bytes, start, end)));
            send(requestId, true, data, null);
        }
    }

    private void sendBinary(String requestId, Path file, String mimeType, String filename) throws IOException {
        long size = Files.size(file);
        int chunkCount = Math.max(1, Math.toIntExact((size + CHUNK_BYTES - 1) / CHUNK_BYTES));
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[CHUNK_BYTES];
            for (int index = 0; index < chunkCount; index++) {
                int expected = (int) Math.min(CHUNK_BYTES, size - (long) index * CHUNK_BYTES);
                int read = input.readNBytes(buffer, 0, expected);
                if (read != expected) throw new EOFException("Generated file changed while being read.");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("kind", "binary-chunk");
                data.put("chunkIndex", index);
                data.put("chunkCount", chunkCount);
                data.put("mimeType", mimeType);
                data.put("filename", filename);
                data.put("base64", Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buffer, read)));
                send(requestId, true, data, null);
            }
        }
    }

    private void sendError(String requestId, String code, String message) throws IOException {
        send(requestId, false, null, Map.of("code", code, "message", message));
    }

    private void send(String requestId, boolean success, Object data, Object error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", requestId);
        response.put("success", success);
        response.put("data", data);
        response.put("error", error);
        try {
            writeFrame(mapper.writeValueAsBytes(response));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Native Messaging output failed.", e);
        }
    }

    private synchronized void writeFrame(byte[] payload) throws IOException {
        if (payload.length > MAX_FRAME_BYTES) throw new IOException("Native response exceeded the frame limit.");
        protocolOutput.write(payload.length & 0xff);
        protocolOutput.write((payload.length >>> 8) & 0xff);
        protocolOutput.write((payload.length >>> 16) & 0xff);
        protocolOutput.write((payload.length >>> 24) & 0xff);
        protocolOutput.write(payload);
        protocolOutput.flush();
    }

    private byte[] readFrame(DataInputStream input) throws IOException {
        int first = input.read();
        if (first < 0) return null;
        int b1 = input.read();
        int b2 = input.read();
        int b3 = input.read();
        if ((b1 | b2 | b3) < 0) throw new EOFException("Incomplete Native Messaging frame header.");
        int length = first | (b1 << 8) | (b2 << 16) | (b3 << 24);
        if (length < 2 || length > MAX_FRAME_BYTES) throw new IOException("Native Messaging frame size is invalid.");
        byte[] payload = new byte[length];
        input.readFully(payload);
        return payload;
    }

    private void cleanupUpload(String requestId) {
        if (requestId == null) return;
        UploadSession session = uploads.remove(requestId);
        if (session != null) session.closeAndDelete();
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Required text field is missing: " + field);
        }
        return value.textValue();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.canConvertToInt()) throw new IllegalArgumentException("Invalid integer field: " + field);
        return value.intValue();
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.canConvertToLong()) throw new IllegalArgumentException("Invalid size field: " + field);
        return value.longValue();
    }

    private static void validateFilename(String filename, String action) {
        if (filename.length() > 180 || filename.contains("/") || filename.contains("\\") || filename.matches(".*[\\x00-\\x1f].*")) {
            throw new IllegalArgumentException("Filename is invalid.");
        }
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        String extension = action.equals("PROCESS_ZIP") ? ".zip" : ".pdf";
        if (!lower.endsWith(extension)) throw new IllegalArgumentException("Filename extension does not match the action.");
    }

    private static String uniqueName(String filename, Set<String> used) {
        if (used.add(filename)) return filename;
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        int suffix = 1;
        String candidate;
        do { candidate = base + "_" + suffix++ + extension; } while (!used.add(candidate));
        return candidate;
    }

    private static String safeReportName(CaseRecord record) {
        String reportId = record.getReportId() != null && record.getReportId().getValue() != null
                ? record.getReportId().getValue() : "case";
        String cleaned = reportId.replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.isBlank() ? "case" : cleaned;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Request could not be processed.";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record FileInput(String filename, String processingName, long sizeBytes) { }

    private static final class UploadSession {
        private final String action;
        private final List<FileInput> files;
        private final Path tempDirectory;
        private final List<Path> stagedFiles = new ArrayList<>();
        private int nextFileIndex;
        private int nextChunkIndex;
        private long currentFileBytes;
        private long currentTotalBytes;
        private Path currentPath;
        private OutputStream currentOutput;

        private UploadSession(String action, List<FileInput> files, Path tempDirectory) {
            this.action = action;
            this.files = files;
            this.tempDirectory = tempDirectory;
        }

        private void closeAndDelete() {
            if (currentOutput != null) {
                try { currentOutput.close(); } catch (IOException ignored) { }
                currentOutput = null;
            }
            try (var paths = Files.walk(tempDirectory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            } catch (IOException ignored) { }
        }
    }
}
