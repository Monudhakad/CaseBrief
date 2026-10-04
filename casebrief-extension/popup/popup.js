'use strict';

const DEV_ORIGINS = ['http://localhost:8080/*', 'http://127.0.0.1:8080/*'];
const DEV_BASE = 'http://localhost:8080';
const CHUNK_BYTES = 48 * 1024;
const MAX_FILE_BYTES = 2 * 1024 * 1024 * 1024;
const MAX_BATCH_BYTES = 2 * 1024 * 1024 * 1024;

const elements = {
  mode: document.getElementById('transportMode'),
  theme: document.getElementById('themeToggle'),
  tabs: [...document.querySelectorAll('.tab')],
  drop: document.getElementById('dropZone'),
  dropTitle: document.getElementById('dropTitle'),
  dropDescription: document.getElementById('dropDescription'),
  fileInput: document.getElementById('fileInput'),
  browse: document.getElementById('browseButton'),
  selected: document.getElementById('selectedFiles'),
  process: document.getElementById('processButton'),
  clear: document.getElementById('clearButton'),
  status: document.getElementById('status'),
  progress: document.getElementById('progress'),
  progressLabel: document.getElementById('progressLabel'),
  progressBar: document.querySelector('.progress-bar'),
  results: document.getElementById('results')
};

const state = { mode: 'single', files: [], transport: 'native', nativePort: null, jobs: new Map(), lastResult: null, progressOrder: [] };

initialize();

async function initialize() {
  const saved = await chrome.storage.local.get({ transport: 'native', theme: 'dark' });
  state.transport = saved.transport;
  elements.mode.value = state.transport;
  applyTheme(saved.theme);
  updateModeUI();

  elements.mode.addEventListener('change', changeTransport);
  elements.theme.addEventListener('click', toggleTheme);
  elements.tabs.forEach(tab => tab.addEventListener('click', () => setMode(tab.dataset.mode)));
  elements.browse.addEventListener('click', event => {
    event.stopPropagation();
    configureFileInput();
    elements.fileInput.click();
  });
  elements.drop.addEventListener('click', event => {
    if (event.target.closest('button')) return;
    configureFileInput();
    elements.fileInput.click();
  });
  elements.fileInput.addEventListener('change', () => {
    addFiles([...elements.fileInput.files]);
    elements.fileInput.value = '';
  });
  elements.drop.addEventListener('dragover', event => {
    event.preventDefault();
    elements.drop.classList.add('dragging');
    elements.dropTitle.textContent = 'Drop files here';
  });
  elements.drop.addEventListener('dragleave', event => {
    if (event.relatedTarget && elements.drop.contains(event.relatedTarget)) return;
    resetDropHeading();
  });
  elements.drop.addEventListener('drop', event => {
    event.preventDefault();
    resetDropHeading();
    addFiles([...event.dataTransfer.files]);
  });
  elements.process.addEventListener('click', processFiles);
  elements.clear.addEventListener('click', clearFiles);
}

async function changeTransport() {
  const requested = elements.mode.value;
  if (requested === 'development') {
    const granted = await chrome.permissions.request({ origins: DEV_ORIGINS });
    if (!granted) {
      elements.mode.value = state.transport;
      showStatus('Local backend access was not granted.', 'warning');
      return;
    }
  }
  state.transport = requested;
  await chrome.storage.local.set({ transport: requested });
  updateModeUI();
}

function updateModeUI() {
  const development = state.transport === 'development';
  document.body.classList.toggle('development-mode', development);
}

async function toggleTheme() {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  applyTheme(next);
  await chrome.storage.local.set({ theme: next });
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  elements.theme.textContent = theme === 'dark' ? '☀' : '☾';
}

function setMode(mode) {
  state.mode = mode;
  elements.tabs.forEach(tab => {
    const active = tab.dataset.mode === mode;
    tab.classList.toggle('active', active);
    tab.setAttribute('aria-selected', String(active));
  });
  elements.fileInput.accept = mode === 'zip' ? '.zip,application/zip' : '.pdf,application/pdf';
  elements.fileInput.multiple = mode === 'bulk';
  elements.dropTitle.textContent = mode === 'single' ? 'Drop a PDF here' : mode === 'bulk' ? 'Drop multiple PDFs here' : 'Drop a ZIP archive here';
  elements.dropDescription.textContent = mode === 'zip' ? 'PDFs inside are processed independently' : mode === 'bulk' ? 'Each document is processed independently' : 'or browse to choose one report';
  elements.process.textContent = mode === 'single' ? 'Process PDF' : mode === 'bulk' ? 'Process files' : 'Process ZIP';
  elements.process.disabled = state.files.length === 0;
  clearFiles(false);
}

function configureFileInput() {
  elements.fileInput.accept = state.mode === 'zip' ? '.zip,application/zip' : '.pdf,application/pdf';
  elements.fileInput.multiple = state.mode === 'bulk';
}

function addFiles(files) {
  const accepted = state.mode === 'bulk' ? files.filter(file => isPdf(file.name)) : files.slice(0, 1);
  state.files = state.mode === 'bulk'
    ? [...state.files, ...accepted.filter(file => !state.files.some(existing => existing.name === file.name))]
    : accepted;
  elements.drop.classList.remove('success', 'failure', 'partial');
  elements.process.disabled = state.files.length === 0;
  renderSelectedFiles();
  elements.results.replaceChildren();
}

function renderSelectedFiles() {
  elements.selected.replaceChildren();
  state.files.forEach((file, index) => {
    const row = document.createElement('div');
    row.className = 'file-row';
    const name = document.createElement('span');
    name.className = 'file-name';
    name.textContent = file.name;
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'file-remove';
    remove.setAttribute('aria-label', `Remove ${file.name}`);
    remove.textContent = '×';
    remove.addEventListener('click', () => {
      state.files.splice(index, 1);
      renderSelectedFiles();
      elements.process.disabled = state.files.length === 0;
      elements.drop.classList.remove('success', 'failure', 'partial');
      resetDropHeading();
    });
    row.append(name, remove);
    elements.selected.append(row);
  });
}

function resetDropHeading() {
  elements.drop.classList.remove('dragging');
  elements.dropTitle.textContent = state.mode === 'single' ? 'Drop a PDF here' : state.mode === 'bulk' ? 'Drop multiple PDFs here' : 'Drop a ZIP archive here';
}

function clearFiles(clearStatus = true) {
  state.files = [];
  state.lastResult = null;
  elements.fileInput.value = '';
  elements.process.disabled = true;
  elements.selected.replaceChildren();
  elements.results.replaceChildren();
  elements.drop.classList.remove('success', 'failure', 'partial', 'processing', 'dragging');
  resetDropHeading();
  elements.progress.hidden = true;
  if (clearStatus) elements.status.hidden = true;
}

async function processFiles() {
  const validationError = validateSelectedFiles();
  if (validationError) {
    showStatus(validationError, 'error');
    return;
  }
  setBusy(true);
  state.progressOrder = [];
  elements.results.replaceChildren();
  elements.status.hidden = true;
  elements.drop.classList.remove('success', 'failure', 'partial');

  try {
    const result = state.transport === 'development'
      ? await processWithDevelopmentServer()
      : await processWithNativeHost();
    state.lastResult = result;
    elements.drop.classList.remove('processing');
    const successful = state.mode === 'single' ? 1 : (result.successfulRecords || []).length;
    const failed = state.mode === 'single' ? 0 : (result.failedRecords || []).length;
    elements.drop.classList.add(failed ? (successful ? 'partial' : 'failure') : 'success');
    elements.dropTitle.textContent = failed ? (successful ? 'Batch complete with failures' : 'Processing failed') : 'Processing complete';
    renderResult(result);
  } catch (error) {
    elements.drop.classList.remove('processing');
    elements.drop.classList.add('failure');
    elements.dropTitle.textContent = 'Processing failed';
    showStatus(error.message || 'Processing failed.', 'error');
  } finally {
    setBusy(false);
  }
}

function validateSelectedFiles() {
  if (!state.files.length) return 'Choose a file first.';
  if (state.mode === 'single' && !isPdf(state.files[0].name)) return 'Choose a PDF file.';
  if (state.mode === 'bulk' && state.files.some(file => !isPdf(file.name))) return 'Bulk processing accepts PDF files only.';
  if (state.mode === 'zip' && !state.files[0].name.toLowerCase().endsWith('.zip')) return 'Choose a ZIP archive.';
  if (state.files.some(file => file.size <= 0 || file.size > MAX_FILE_BYTES)) return 'Each file must be between 1 byte and 2 GB.';
  if (state.files.reduce((total, file) => total + file.size, 0) > MAX_BATCH_BYTES) return 'The selected batch exceeds 2 GB.';
  return null;
}

function isPdf(filename) { return filename.toLowerCase().endsWith('.pdf'); }

function setBusy(busy) {
  elements.process.disabled = busy || state.files.length === 0;
  elements.clear.disabled = busy;
  elements.process.innerHTML = busy ? '<span class="spinner" aria-hidden="true"></span> Processing…' : state.mode === 'single' ? 'Process PDF' : state.mode === 'bulk' ? 'Process files' : 'Process ZIP';
  elements.progress.hidden = !busy;
  elements.drop.classList.toggle('processing', busy);
  if (busy) {
    elements.progressBar.classList.add('indeterminate');
    elements.progressBar.style.width = '30%';
    elements.progressLabel.textContent = state.mode === 'bulk' ? `Preparing ${state.files.length} PDFs…` : state.mode === 'zip' ? 'Preparing ZIP…' : 'Reading PDF…';
    elements.dropTitle.textContent = 'Analyzing document…';
  } else {
    elements.progressBar.classList.remove('indeterminate');
    elements.progressBar.style.width = '0%';
  }
}

async function processWithDevelopmentServer() {
  const form = new FormData();
  const endpoint = state.mode === 'single' ? '/api/cases/single' : state.mode === 'bulk' ? '/api/cases/bulk' : '/api/cases/zip';
  if (state.mode === 'bulk') state.files.forEach(file => form.append('files', file));
  else form.append('file', state.files[0]);
  elements.progressLabel.textContent = `Sending ${state.files.length} file${state.files.length === 1 ? '' : 's'} to the local backend…`;
  const response = await fetch(DEV_BASE + endpoint, { method: 'POST', body: form, credentials: 'omit' });
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || 'Local backend request failed.');
  return body;
}

async function processWithNativeHost() {
  const files = state.files;
  const action = state.mode === 'single' ? 'PROCESS_SINGLE' : state.mode === 'bulk' ? 'PROCESS_BULK' : 'PROCESS_ZIP';
  const requestId = crypto.randomUUID();
  const job = createNativeJob(requestId, 'json');
  const port = getNativePort();
  port.postMessage({
    kind: 'start', action, requestId,
    files: files.map(file => ({ filename: file.name, sizeBytes: file.size }))
  });
  await job.ready;

  for (let fileIndex = 0; fileIndex < files.length; fileIndex++) {
    const file = files[fileIndex];
    const chunkCount = Math.ceil(file.size / CHUNK_BYTES);
    for (let chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
      const start = chunkIndex * CHUNK_BYTES;
      const bytes = new Uint8Array(await file.slice(start, Math.min(start + CHUNK_BYTES, file.size)).arrayBuffer());
      const base64 = bytesToBase64(bytes);
      const ack = job.waitForAck(fileIndex, chunkIndex);
      port.postMessage({
        kind: 'chunk', requestId, fileIndex, fileCount: files.length,
        filename: file.name, chunkIndex, chunkCount, data: base64
      });
      await ack;
    }
  }
  return job.result;
}

let nativePort = null;
const nativeJobs = new Map();

function getNativePort() {
  if (nativePort) return nativePort;
  nativePort = chrome.runtime.connect({ name: 'casebrief-native-job' });
  nativePort.onMessage.addListener(handleNativeMessage);
  nativePort.onDisconnect.addListener(() => {
    const message = chrome.runtime.lastError?.message || 'Native Messaging connection closed.';
    nativeJobs.forEach(job => job.fail(new Error(message)));
    nativeJobs.clear();
    nativePort = null;
  });
  return nativePort;
}

function createNativeJob(requestId, expectedKind) {
  let readyResolve;
  let readyReject;
  let resultResolve;
  let resultReject;
  const ackResolvers = new Map();
  const responseChunks = [];
  let expectedChunkCount = 0;
  const job = {
    requestId,
    expectedKind,
    ready: new Promise((resolve, reject) => { readyResolve = resolve; readyReject = reject; }),
    result: new Promise((resolve, reject) => { resultResolve = resolve; resultReject = reject; }),
    readyResolve,
    readyReject,
    resultResolve,
    resultReject,
    waitForAck(fileIndex, chunkIndex) {
      const key = `${fileIndex}:${chunkIndex}`;
      return new Promise((resolve, reject) => ackResolvers.set(key, { resolve, reject }));
    },
    fail(error) {
      readyReject?.(error);
      resultReject?.(error);
      ackResolvers.forEach(waiter => waiter.reject(error));
    }
  };
  job.readyResolve = readyResolve;
  job.readyReject = readyReject;
  job.resultResolve = resultResolve;
  job.resultReject = resultReject;
  job.ackResolvers = ackResolvers;
  job.responseChunks = responseChunks;
  job.setExpectedChunkCount = count => { expectedChunkCount = count; };
  job.getExpectedChunkCount = () => expectedChunkCount;
  job.ready.catch(() => { });
  job.result.catch(() => { });
  nativeJobs.set(requestId, job);
  return job;
}

function handleNativeMessage(message) {
  const job = nativeJobs.get(message?.requestId);
  if (!job) return;
  if (message.success !== true) {
    const error = new Error(message.error?.message || 'Native host request failed.');
    job.fail(error);
    nativeJobs.delete(message.requestId);
    return;
  }
  const data = message.data;
  if (data?.kind === 'ready') {
    job.readyResolve();
  } else if (data?.kind === 'ack') {
    const waiter = job.ackResolvers.get(`${data.fileIndex}:${data.chunkIndex}`);
    waiter?.resolve();
    job.ackResolvers.delete(`${data.fileIndex}:${data.chunkIndex}`);
  } else if (data?.kind === 'progress') {
    state.progressOrder.push({ filename: data.filename, successful: data.successful });
    elements.progressBar.classList.remove('indeterminate');
    const completed = String(data.completed).padStart(2, '0');
    const total = String(data.total).padStart(2, '0');
    elements.progressLabel.textContent = `Processing ${completed} / ${total}`;
    elements.progressBar.style.width = `${data.total ? (100 * data.completed / data.total) : 0}%`;
  } else if (data?.kind === 'json-chunk' || data?.kind === 'binary-chunk') {
    if (job.expectedKind !== data.kind.replace('-chunk', '')) return;
    job.setExpectedChunkCount(data.chunkCount);
    job.responseChunks[data.chunkIndex] = base64ToBytes(data.base64);
    if (job.responseChunks.filter(Boolean).length === job.getExpectedChunkCount()) {
      const bytes = concatenateBytes(job.responseChunks);
      if (job.expectedKind === 'json') {
        job.resultResolve(JSON.parse(new TextDecoder().decode(bytes)));
      } else {
        job.resultResolve(new Blob([bytes], { type: data.mimeType || 'application/octet-stream' }));
      }
      nativeJobs.delete(message.requestId);
    }
  }
}

function bytesToBase64(bytes) {
  let binary = '';
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, Math.min(offset + 0x8000, bytes.length)));
  }
  return btoa(binary);
}

function base64ToBytes(base64) {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function concatenateBytes(chunks) {
  const length = chunks.reduce((total, chunk) => total + chunk.length, 0);
  const result = new Uint8Array(length);
  let offset = 0;
  chunks.forEach(chunk => { result.set(chunk, offset); offset += chunk.length; });
  return result;
}

function renderResult(result) {
  elements.results.replaceChildren();
  if (state.mode === 'single') {
    renderRecord(result, true);
    return;
  }
  const successful = result.successfulRecords || [];
  const failed = result.failedRecords || [];
  const summary = document.createElement('div');
  summary.className = 'result-summary';
  summary.append(countBadge(`${successful.length + failed.length} processed`));
  if (successful.length) summary.append(countBadge(`${successful.length} successful`, 'success'));
  if (failed.length) summary.append(countBadge(`${failed.length} failed`, 'failure'));
  elements.results.append(summary);

  const resultByFilename = new Map();
  successful.forEach(record => resultByFilename.set(record.sourceFilename, { record }));
  failed.forEach(failure => resultByFilename.set(failure.filename, { failure }));
  const displayed = new Set();
  state.progressOrder.forEach(event => {
    const entry = resultByFilename.get(event.filename);
    if (entry && !displayed.has(entry)) {
      displayed.add(entry);
      entry.record ? renderRecord(entry.record, false) : renderFailure(entry.failure);
    }
  });
  state.files.forEach(file => {
    const entry = resultByFilename.get(file.name);
    if (entry) {
      displayed.add(entry);
      entry.record ? renderRecord(entry.record, false) : renderFailure(entry.failure);
    }
  });
  successful.forEach(record => {
    const entry = resultByFilename.get(record.sourceFilename);
    if (!displayed.has(entry)) renderRecord(record, false);
  });
  failed.forEach(failure => {
    const entry = resultByFilename.get(failure.filename);
    if (!displayed.has(entry)) renderFailure(failure);
  });
  renderBatchActions(result);
}

function countBadge(label, status = '') {
  const badge = document.createElement('span');
  badge.className = `count ${status}`;
  badge.textContent = label;
  return badge;
}

function renderRecord(record, single) {
  const card = document.createElement('article');
  card.className = 'record';
  const head = document.createElement('div');
  head.className = 'record-head';
  const info = document.createElement('div');
  info.style.minWidth = '0';
  const filename = document.createElement('div');
  filename.className = 'record-name';
  filename.textContent = record.sourceFilename || 'document.pdf';
  const reportId = document.createElement('div');
  reportId.className = 'record-meta';
  reportId.textContent = `Report #${fieldValue(record.reportId) || 'Unknown'}`;
  info.append(filename, reportId);
  head.append(info);
  card.append(head);

  const fields = document.createElement('div');
  fields.className = 'record-detail';
  fields.textContent = `Suspect: ${fieldValue(record.suspect?.name) || 'N/A'} · Recent IP: ${fieldValue(record.recentSuspectIp) || 'N/A'} · Geo: ${fieldValue(record.geoCity) || 'N/A'}`;
  card.append(fields);

  const actionBar = document.createElement('div');
  actionBar.className = 'result-actions';
  const pdfButton = makeButton('Download Summary PDF', () => generateDownload('GENERATE_SUMMARY_PDF', record, null));
  actionBar.append(pdfButton);
  card.append(actionBar);
  elements.results.append(card);
}

function renderFailure(failure) {
  const card = document.createElement('article');
  card.className = 'record failed';
  const head = document.createElement('div');
  head.className = 'record-head';
  const info = document.createElement('div');
  info.style.minWidth = '0';
  const name = document.createElement('div');
  name.className = 'record-name';
  name.textContent = `✕ ${failure.filename || 'Unknown file'}`;
  const reason = document.createElement('div');
  reason.className = 'record-detail error';
  reason.textContent = failure.failureReason || 'Processing failed.';
  info.append(name, reason);
  head.append(info);
  card.append(head);
  elements.results.append(card);
}

function renderBatchActions(result) {
  const actions = document.createElement('div');
  actions.className = 'result-actions';
  if ((result.successfulRecords || []).length) {
    actions.append(makeButton('Download All Summaries', () => generateDownload('GENERATE_BATCH_ZIP', null, result)));
    actions.append(makeButton('Download Excel', () => generateDownload('GENERATE_EXCEL', null, result)));
  }
  actions.append(makeButton('Download Status JSON', () => downloadBlob(
    new Blob([JSON.stringify(result, null, 2)], { type: 'application/json' }), 'casebrief-status.json')));
  elements.results.append(actions);
}

function makeButton(label, onClick) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'button button-secondary';
  button.textContent = label;
  button.addEventListener('click', onClick);
  return button;
}

function fieldValue(field) {
  return field && field.value != null ? String(field.value) : '';
}

async function generateDownload(action, record, result) {
  try {
    const blob = state.transport === 'development'
      ? await generateWithDevelopmentServer(action, record, result)
      : await generateWithNativeHost(action, record, result);
    const filename = action === 'GENERATE_SUMMARY_PDF'
      ? `${fieldValue(record?.reportId) || 'case'}_Summary_Brief.pdf`
      : action === 'GENERATE_BATCH_ZIP' ? 'CaseBrief_Summaries.zip' : 'CaseBrief_Batch_Report.xlsx';
    downloadBlob(blob, filename);
  } catch (error) {
    showStatus(error.message || 'The file could not be generated.', 'error');
  }
}

async function generateWithNativeHost(action, record, result) {
  const requestId = crypto.randomUUID();
  const job = createNativeJob(requestId, 'binary');
  getNativePort().postMessage({ kind: 'command', action, requestId, ...(record ? { record } : { result }) });
  await job.ready;
  return job.result;
}

async function generateWithDevelopmentServer(action, record, result) {
  const endpoint = action === 'GENERATE_SUMMARY_PDF' ? '/api/cases/summary-pdf' : action === 'GENERATE_BATCH_ZIP' ? '/api/cases/batch-summary-zip' : '/api/cases/excel';
  const payload = record || result;
  const response = await fetch(DEV_BASE + endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    credentials: 'omit'
  });
  if (!response.ok) throw new Error(`Export failed with HTTP ${response.status}.`);
  return response.blob();
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}

function showStatus(message, kind) {
  elements.status.textContent = message;
  elements.status.className = `status ${kind}`;
  elements.status.hidden = false;
}
