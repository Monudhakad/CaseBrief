const NATIVE_HOST = 'com.casebrief.native';
const FILE_ACTIONS = new Set(['PROCESS_SINGLE', 'PROCESS_BULK', 'PROCESS_ZIP']);
const OUTPUT_ACTIONS = new Set(['GENERATE_SUMMARY_PDF', 'GENERATE_BATCH_ZIP', 'GENERATE_EXCEL']);
const MAX_FILES = 100;
const MAX_FILE_BYTES = 2 * 1024 * 1024 * 1024;
const MAX_BATCH_BYTES = 2 * 1024 * 1024 * 1024;
const MAX_CHUNK_BASE64 = 96 * 1024;

chrome.runtime.onConnect.addListener(extensionPort => {
  if (extensionPort.name !== 'casebrief-native-job') return;

  let nativePort = null;
  let activeRequestId = null;
  let activeAction = null;
  let activeFiles = [];

  function sendError(requestId, code, message) {
    extensionPort.postMessage({
      requestId: requestId || null,
      success: false,
      data: null,
      error: { code, message }
    });
  }

  function connectHost(requestId, action) {
    activeRequestId = requestId;
    activeAction = action;
    if (nativePort) return true;
    try {
      nativePort = chrome.runtime.connectNative(NATIVE_HOST);
    } catch {
      sendError(requestId, 'NATIVE_HOST_UNAVAILABLE', 'CaseBrief Native Host is not installed or could not be started.');
      return false;
    }

    nativePort.onMessage.addListener(message => {
      if (!message || message.requestId !== activeRequestId || typeof message.success !== 'boolean') return;
      extensionPort.postMessage(message);
      if (isTerminalResponse(message)) {
        activeRequestId = null;
        activeAction = null;
        activeFiles = [];
      }
    });

    nativePort.onDisconnect.addListener(() => {
      const error = chrome.runtime.lastError;
      if (activeRequestId) {
        sendError(activeRequestId, 'NATIVE_HOST_DISCONNECTED', error?.message || 'The CaseBrief Native Host disconnected.');
      }
      nativePort = null;
      activeRequestId = null;
      activeAction = null;
    });
    return true;
  }

  extensionPort.onMessage.addListener(message => {
    if (!message || typeof message !== 'object') {
      sendError(null, 'INVALID_MESSAGE', 'Invalid extension message.');
      return;
    }

    if (message.kind === 'start') {
      if (activeRequestId) {
        sendError(message.requestId, 'BUSY', 'A CaseBrief request is already active.');
        return;
      }
      if (!FILE_ACTIONS.has(message.action) || !validRequestId(message.requestId)) {
        sendError(message.requestId, 'INVALID_REQUEST', 'Unsupported processing request.');
        return;
      }
      const files = message.files;
      if (!Array.isArray(files) || files.length < 1 || files.length > MAX_FILES) {
        sendError(message.requestId, 'INVALID_FILES', `Select between 1 and ${MAX_FILES} files.`);
        return;
      }
      if ((message.action === 'PROCESS_SINGLE' || message.action === 'PROCESS_ZIP') && files.length !== 1) {
        sendError(message.requestId, 'INVALID_FILES', 'This action accepts exactly one file.');
        return;
      }
      const totalBytes = files.reduce((sum, file) => sum + (Number.isSafeInteger(file?.sizeBytes) ? file.sizeBytes : 0), 0);
      if (files.some(file => !validFileMetadata(file, message.action)) || totalBytes > MAX_BATCH_BYTES) {
        sendError(message.requestId, 'INVALID_FILES', 'The selected files exceed the supported type or size limits.');
        return;
      }
      if (!connectHost(message.requestId, message.action)) return;
      activeFiles = files.map(file => ({ filename: file.filename, sizeBytes: file.sizeBytes }));
      nativePort.postMessage({
        action: message.action,
        requestId: message.requestId,
        fileCount: activeFiles.length,
        files: activeFiles
      });
      extensionPort.postMessage({ requestId: activeRequestId, success: true, data: { kind: 'ready' }, error: null });
      return;
    }

    if (message.kind === 'chunk') {
      if (!nativePort || message.requestId !== activeRequestId || !FILE_ACTIONS.has(activeAction)) {
        sendError(message.requestId, 'NO_ACTIVE_REQUEST', 'No matching file-processing request is active.');
        return;
      }
      if (!Number.isSafeInteger(message.fileIndex) || !Number.isSafeInteger(message.chunkIndex) ||
          !Number.isSafeInteger(message.chunkCount) || message.chunkIndex < 0 ||
          message.chunkIndex >= message.chunkCount || message.chunkCount > 50000 ||
          message.fileIndex < 0 || message.fileIndex >= activeFiles.length || message.fileCount !== activeFiles.length ||
          message.filename !== activeFiles[message.fileIndex]?.filename ||
          typeof message.data !== 'string' || message.data.length > MAX_CHUNK_BASE64 ||
          !/^[A-Za-z0-9+/]*={0,2}$/.test(message.data)) {
        sendError(message.requestId, 'INVALID_CHUNK', 'Invalid file chunk.');
        nativePort.disconnect();
        return;
      }
      nativePort.postMessage({
        action: activeAction,
        requestId: activeRequestId,
        fileIndex: message.fileIndex,
        fileCount: activeFiles.length,
        chunkIndex: message.chunkIndex,
        chunkCount: message.chunkCount,
        filename: activeFiles[message.fileIndex].filename,
        data: message.data
      });
      return;
    }

    if (message.kind === 'command') {
      if (activeRequestId) {
        sendError(message.requestId, 'BUSY', 'A CaseBrief request is already active.');
        return;
      }
      if (!OUTPUT_ACTIONS.has(message.action) || !validRequestId(message.requestId)) {
        sendError(message.requestId, 'INVALID_REQUEST', 'Unsupported export request.');
        return;
      }
      const payloadKey = message.action === 'GENERATE_SUMMARY_PDF' ? 'record' : 'result';
      if (!message[payloadKey] || typeof message[payloadKey] !== 'object') {
        sendError(message.requestId, 'INVALID_PAYLOAD', 'The export data is missing or invalid.');
        return;
      }
      const payloadBytes = new TextEncoder().encode(JSON.stringify(message[payloadKey])).length;
      if (payloadBytes > 900000) {
        sendError(message.requestId, 'PAYLOAD_TOO_LARGE', 'Export data exceeds the Native Messaging limit.');
        return;
      }
      if (!connectHost(message.requestId, message.action)) return;
      nativePort.postMessage({
        action: message.action,
        requestId: message.requestId,
        [payloadKey]: message[payloadKey]
      });
      extensionPort.postMessage({ requestId: message.requestId, success: true, data: { kind: 'ready' }, error: null });
      return;
    }

    sendError(message.requestId, 'INVALID_MESSAGE', 'Unsupported extension message type.');
  });

  extensionPort.onDisconnect.addListener(() => {
    if (nativePort) nativePort.disconnect();
    nativePort = null;
    activeRequestId = null;
    activeAction = null;
  });
});

function isTerminalResponse(message) {
  if (!message.success) return true;
  const data = message.data;
  if (!data || (data.kind !== 'json-chunk' && data.kind !== 'binary-chunk')) return false;
  return data.chunkIndex === data.chunkCount - 1;
}

function validRequestId(value) {
  return typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value);
}

function validFileMetadata(file, action) {
  if (!file || typeof file.filename !== 'string' || file.filename.length < 1 || file.filename.length > 180 ||
      !Number.isSafeInteger(file.sizeBytes) || file.sizeBytes < 1 || file.sizeBytes > MAX_FILE_BYTES) return false;
  const lowerName = file.filename.toLowerCase();
  const expectedExtension = action === 'PROCESS_ZIP' ? '.zip' : '.pdf';
  return lowerName.endsWith(expectedExtension) && !/[\\/:\u0000-\u001f]/.test(file.filename);
}
