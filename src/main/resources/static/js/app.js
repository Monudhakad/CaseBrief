/* ═══════════════════════════════════════════════
   CaseBrief · app.js
   Single-page application logic
   ═══════════════════════════════════════════════ */
'use strict';

// ── Theme ────────────────────────────────────────
const THEME_KEY = 'cb_theme';
const themeToggle = document.getElementById('themeToggle');
const themeIcon = document.getElementById('themeIcon');

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  themeIcon.textContent = theme === 'dark' ? '☀️' : '🌙';
  localStorage.setItem(THEME_KEY, theme);
}
themeToggle.addEventListener('click', () => {
  const cur = document.documentElement.getAttribute('data-theme') || 'dark';
  applyTheme(cur === 'dark' ? 'light' : 'dark');
});
applyTheme(localStorage.getItem(THEME_KEY) || 'dark');

// ── Motion and scroll state ────────────────────────
const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
const revealObserver = !motionPreference.matches && 'IntersectionObserver' in window
  ? new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          revealObserver.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -20px 0px' })
  : null;

function revealWhenVisible(element) {
  if (!element) return;
  if (revealObserver) {
    element.classList.add('reveal-ready');
    revealObserver.observe(element);
  } else {
    element.classList.add('is-visible');
  }
}

document.querySelectorAll('.hero, .upload-panel, footer').forEach(revealWhenVisible);

const navbar = document.querySelector('.navbar');
function updateNavbarScrollState() {
  navbar.classList.toggle('is-scrolled', window.scrollY > 8);
}
updateNavbarScrollState();
window.addEventListener('scroll', updateNavbarScrollState, { passive: true });

// ── Tab switching ─────────────────────────────────
const tabBtns = document.querySelectorAll('.tab-btn');
const tabPanels = document.querySelectorAll('.tab-content');

tabBtns.forEach(btn => {
  btn.addEventListener('click', () => {
    const target = btn.dataset.tab;
    tabBtns.forEach(b => b.classList.remove('active'));
    tabPanels.forEach(p => p.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(`tab-${target}`).classList.add('active');
    clearResults(target);
  });
});

// ── Drag-and-drop + file input ────────────────────
const zones = {
  single: setupZone('single', false),
  bulk: setupZone('bulk', true),
  zip: setupZone('zip', false),
};

function setupZone(id, multiple) {
  const zone = document.getElementById(`drop-${id}`);
  const input = document.getElementById(`input-${id}`);
  const chips = document.getElementById(`chips-${id}`);
  const pickBtn = document.getElementById(`pick-${id}`);
  const heading = zone.querySelector('h3');
  const defaultHeading = heading?.textContent || '';
  zone.dataset.defaultHeading = defaultHeading;
  let files = [];

  function resetHeading() {
    if (heading) heading.textContent = defaultHeading;
  }

  pickBtn.addEventListener('click', (e) => { e.stopPropagation(); input.click(); });
  zone.addEventListener('click', () => input.click());

  input.addEventListener('change', () => {
    addFiles(Array.from(input.files));
    input.value = '';
  });

  zone.addEventListener('dragover', e => {
    e.preventDefault();
    zone.classList.add('drag-over');
    if (heading) heading.textContent = 'Drop files here';
  });
  zone.addEventListener('dragleave', e => {
    if (e.relatedTarget && zone.contains(e.relatedTarget)) return;
    zone.classList.remove('drag-over');
    resetHeading();
  });
  zone.addEventListener('drop', e => {
    e.preventDefault();
    zone.classList.remove('drag-over');
    resetHeading();
    addFiles(Array.from(e.dataTransfer.files));
  });

  function addFiles(newFiles) {
    zone.classList.remove('is-success', 'is-failure', 'is-partial');
    resetHeading();
    if (!multiple) { files = [newFiles[0]].filter(Boolean); }
    else {
      for (const f of newFiles) {
        if (!files.find(x => x.name === f.name)) files.push(f);
      }
    }
    renderChips();
  }

  function removeFile(name) {
    files = files.filter(f => f.name !== name);
    markUploadState(id, null);
    renderChips();
  }

  function renderChips() {
    chips.innerHTML = '';
    files.forEach(f => {
      const div = document.createElement('div');
      div.className = 'file-chip';
      div.innerHTML = `
        <span class="chip-name" title="${esc(f.name)}">${esc(f.name)}</span>
        <button class="chip-remove" title="Remove" aria-label="Remove ${esc(f.name)}">✕</button>`;
      div.querySelector('.chip-remove').addEventListener('click', (e) => {
        e.stopPropagation();
        removeFile(f.name);
      });
      chips.appendChild(div);
    });
  }

  function getFiles() { return files; }
  function clear() { files = []; resetHeading(); renderChips(); }

  return { getFiles, clear };
}

// ── Clear ─────────────────────────────────────────
document.getElementById('clear-single').addEventListener('click', () => clearTab('single'));
document.getElementById('clear-bulk').addEventListener('click', () => clearTab('bulk'));
document.getElementById('clear-zip').addEventListener('click', () => clearTab('zip'));

function clearTab(id) {
  zones[id].clear();
  markUploadState(id, null);
  clearResults(id);
  hideAlert(id);
}
function clearResults(id) {
  const el = document.getElementById(`results-${id}`);
  if (el) el.innerHTML = '';
}

// ── Process buttons ───────────────────────────────
document.getElementById('process-single').addEventListener('click', () => processSingle());
document.getElementById('process-bulk').addEventListener('click', () => processBulk());
document.getElementById('process-zip').addEventListener('click', () => processZip());

// Single PDF
async function processSingle() {
  const files = zones.single.getFiles();
  if (!files.length) { showAlert('single', 'error', 'Please select a PDF file first.'); return; }
  const file = files[0];
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    showAlert('single', 'error', 'Only PDF files are accepted.'); return;
  }

  const fd = new FormData();
  fd.append('file', file);
  setLoading('single', true);
  hideAlert('single');
  clearResults('single');

  try {
    const res = await fetch('/api/cases/single', { method: 'POST', body: fd });
    const data = await res.json();
    if (!res.ok) { showAlert('single', 'error', data.error || 'Processing failed.'); return; }
    renderSingleResult('single', data, file.name);
  } catch (e) {
    showAlert('single', 'error', 'Network error — is the server running?');
  } finally {
    setLoading('single', false);
  }
}

// Bulk PDFs
async function processBulk() {
  const files = zones.bulk.getFiles();
  if (!files.length) { showAlert('bulk', 'error', 'Please select at least one PDF file.'); return; }
  const invalid = files.filter(f => !f.name.toLowerCase().endsWith('.pdf'));
  if (invalid.length) {
    showAlert('bulk', 'error', `Non-PDF files found: ${invalid.map(f => f.name).join(', ')}`); return;
  }

  const fd = new FormData();
  files.forEach(f => fd.append('files', f));
  setLoading('bulk', true);
  hideAlert('bulk');
  clearResults('bulk');

  try {
    const res = await fetch('/api/cases/bulk', { method: 'POST', body: fd });
    const data = await res.json();
    if (!res.ok) { showAlert('bulk', 'error', data.error || 'Processing failed.'); return; }
    renderBulkResult('bulk', data);
  } catch (e) {
    showAlert('bulk', 'error', 'Network error — is the server running?');
  } finally {
    setLoading('bulk', false);
  }
}

// ZIP
async function processZip() {
  const files = zones.zip.getFiles();
  if (!files.length) { showAlert('zip', 'error', 'Please select a ZIP file.'); return; }
  const file = files[0];
  if (!file.name.toLowerCase().endsWith('.zip')) {
    showAlert('zip', 'error', 'Only ZIP archives are accepted.'); return;
  }

  const fd = new FormData();
  fd.append('file', file);
  setLoading('zip', true);
  hideAlert('zip');
  clearResults('zip');

  try {
    const res = await fetch('/api/cases/zip', { method: 'POST', body: fd });
    const data = await res.json();
    if (!res.ok) { showAlert('zip', 'error', data.error || 'Processing failed.'); return; }
    renderBulkResult('zip', data);
  } catch (e) {
    showAlert('zip', 'error', 'Network error — is the server running?');
  } finally {
    setLoading('zip', false);
  }
}

// ── Loading state ─────────────────────────────────
function setLoading(tabId, loading) {
  const btn = document.getElementById(`process-${tabId}`);
  const prog = document.getElementById(`progress-${tabId}`);
  const zone = document.getElementById(`drop-${tabId}`);
  const label = prog?.querySelector('.progress-label');
  btn.disabled = loading;
  btn.innerHTML = loading
    ? `<span class="spinner"></span> Processing…`
    : (tabId === 'single' ? '⚡ Process PDF' : tabId === 'bulk' ? '⚡ Process Files' : '⚡ Process ZIP');

  if (!prog) return;
  prog.classList.toggle('visible', loading);
  if (loading) {
    zone.classList.remove('is-success', 'is-failure', 'is-partial');
    zone.classList.add('is-processing');
    const heading = zone.querySelector('h3');
    if (heading) heading.textContent = 'Analyzing document…';
    if (label) {
      const count = zones.bulk.getFiles().length;
      label.textContent = tabId === 'single'
        ? 'Reading PDF…'
        : tabId === 'bulk'
          ? `Processing ${count} PDF${count === 1 ? '' : 's'}…`
          : 'Processing PDFs in ZIP…';
    }
  } else {
    zone.classList.remove('is-processing');
  }
}

function markUploadState(tabId, state) {
  const zone = document.getElementById(`drop-${tabId}`);
  const heading = zone.querySelector('h3');
  zone.classList.remove('is-success', 'is-failure', 'is-partial');
  if (state) zone.classList.add(`is-${state}`);
  if (heading) {
    heading.textContent = state === 'success'
      ? 'Processing complete'
      : state === 'partial'
        ? 'Batch complete with failures'
        : state === 'failure'
          ? 'Processing failed'
          : zone.dataset.defaultHeading;
  }
}

// ── Alert ─────────────────────────────────────────
function showAlert(tabId, type, msg) {
  const el = document.getElementById(`alert-${tabId}`);
  if (!el) return;
  el.className = `alert-banner ${type}`;
  el.innerHTML = `<span>${type === 'error' ? '⚠️' : '✅'}</span><span>${esc(msg)}</span>`;
  el.classList.remove('hidden');
  if (type === 'error') markUploadState(tabId, 'failure');
}
function hideAlert(tabId) {
  const el = document.getElementById(`alert-${tabId}`);
  if (el) el.classList.add('hidden');
}

// ── In-Memory Case Records Store (avoids quote/bracket HTML-attribute escaping bugs) ──
const caseRecordStore = new Map();
let recordIdCounter = 0;

function storeRecord(record) {
  const id = 'rec_' + (++recordIdCounter);
  caseRecordStore.set(id, record);
  return id;
}

function wireDownloadButtons(container) {
  container.querySelectorAll('.btn-download, .btn-download-summary').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const recId = btn.dataset.recId;
      const record = caseRecordStore.get(recId);
      if (record) {
        downloadSummaryPdf(record, safeFileStem(record), btn);
      }
    });
  });

  container.querySelectorAll('.btn-download-zip').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const batchId = btn.dataset.batchId;
      const result = caseRecordStore.get(batchId);
      if (result) {
        downloadBatchZip(result, 'CaseBrief_Summaries', btn);
      }
    });
  });

  container.querySelectorAll('.btn-download-excel').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const batchId = btn.dataset.batchId;
      const result = caseRecordStore.get(batchId);
      if (result) {
        downloadBatchExcel(result, 'CaseBrief_Batch_Report', btn);
      }
    });
  });

  container.querySelectorAll('.btn-download-status').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const batchId = btn.dataset.batchId;
      const result = caseRecordStore.get(batchId);
      if (result) {
        downloadBulkJson(JSON.stringify(result, null, 2));
      }
    });
  });
}

// ── Render single result ──────────────────────────
function renderSingleResult(tabId, record, filename) {
  markUploadState(tabId, 'success');
  const container = document.getElementById(`results-${tabId}`);
  const recId = storeRecord(record);

  container.innerHTML = `
    <div class="results-section">
      <div class="results-header">
        <span class="results-title">Extraction Result</span>
        <span class="results-meta">${esc(filename)}</span>
      </div>
      <div class="batch-summary">
        <span class="badge success">✓ Processed successfully</span>
      </div>
      <div class="download-bar">
        <button class="btn-secondary btn-download-summary" data-rec-id="${recId}">
          📄 Download Summary PDF
        </button>
      </div>
      <div class="results-grid">
        ${buildCaseCard(record, true)}
      </div>
    </div>`;

  wireAccordion(container);
  wireDownloadButtons(container);
  revealWhenVisible(container.querySelector('.results-section'));
}

// ── Render bulk/zip result ────────────────────────
function renderBulkResult(tabId, result) {
  const ok   = result.successfulRecords || [];
  const fail = result.failedRecords     || [];
  const processed = ok.length + fail.length;
  markUploadState(tabId, fail.length ? (ok.length ? 'partial' : 'failure') : 'success');
  const total = result.totalDocuments   || (ok.length + fail.length);
  const ms    = result.totalProcessingTimeMs;
  const batchId = storeRecord(result);

  const container = document.getElementById(`results-${tabId}`);
  container.innerHTML = `
    <div class="results-section">
      <div class="results-header">
        <span class="results-title">Batch Results</span>
        <span class="results-meta">${total} file${total !== 1 ? 's' : ''} · ${ms != null ? ms + ' ms' : ''}</span>
      </div>
      <div class="batch-summary">
        <span class="badge info">${processed} processed</span>
        ${ok.length   ? `<span class="badge success">✓ ${ok.length} successful</span>` : ''}
        ${fail.length ? `<span class="badge error">✗ ${fail.length} failed</span>` : ''}
        ${!processed ? `<span class="badge info">No PDF files found</span>` : ''}
      </div>
      ${(ok.length || fail.length) ? `
      <div class="download-bar">
        ${ok.length ? `
        <button class="btn-secondary btn-download-zip" data-batch-id="${batchId}">
          🗜️ Download All Summaries (.zip)
        </button>
        <button class="btn-secondary btn-download-excel" data-batch-id="${batchId}">
          📊 Download Excel (.xlsx)
        </button>` : ''}
        <button class="btn-secondary btn-download-status" data-batch-id="${batchId}">
          📄 Download Status (.json)
        </button>
      </div>` : ''}
      <div class="results-grid">
        ${ok.map(r   => buildCaseCard(r, false)).join('')}
        ${fail.map(f => buildFailCard(f)).join('')}
      </div>
    </div>`;

  wireAccordion(container);
  wireDownloadButtons(container);
  revealWhenVisible(container.querySelector('.results-section'));
}

// ── Card builders ─────────────────────────────────
function v(field) {
  if (!field) return { val: null, page: null };
  return { val: field.value ?? null, page: field.sourcePage ?? null };
}
function disp(field) {
  const { val } = v(field);
  return (val != null && val !== '') ? String(val) : null;
}
function dispOrUnknown(field) {
  return disp(field) ?? '—';
}

function buildCaseCard(record, expanded) {
  const reportId = disp(record.reportId) || 'Unknown';
  const filename = esc(record.sourceFilename || 'document.pdf');
  const cls = expanded ? 'case-card success expanded' : 'case-card success';
  const recId = storeRecord(record);

  const suspect = record.suspect || {};
  const recipient = record.recipient || {};
  const files = record.uploadedFiles || [];

  return `
    <div class="${cls}" id="card-${rand()}">
      <div class="case-card-header">
        <div class="case-card-left">
          <span class="status-dot success"></span>
          <div>
            <div class="case-filename">${filename}</div>
            <div class="case-report-id">Report #${esc(reportId)}</div>
          </div>
        </div>
        <div class="case-card-right">
          <button class="btn-download" data-rec-id="${recId}">
            ↓ Summary PDF
          </button>
          <span class="chevron">▾</span>
        </div>
      </div>
      <div class="case-details">
        <div class="detail-section-title">Overview</div>
        <div class="detail-grid">
          ${di('Report ID', record.reportId)}
          ${di('Date', record.reportDate)}
          ${di('Priority', record.priorityLevel)}
          ${di('ESP', record.reportingEsp)}
          ${di('Incident Type', record.incidentType)}
          ${di('Child Victim', record.espReportedChildVictim)}
          ${di('Total Files', record.totalUploadedFiles)}
        </div>

        <div class="detail-section-title">Suspect</div>
        <div class="detail-grid">
          ${di('Name', suspect.name)}
          ${di('Age', suspect.age)}
          ${di('Phone', suspect.phoneNumber)}
          ${di('Screen Name', suspect.screenName)}
          ${di('Profile URL', suspect.profileUrl)}
        </div>

        <div class="detail-section-title">Recent Network Activity</div>
        <div class="detail-grid">
          ${di('IP Address', record.recentSuspectIp)}
          ${di('Port', record.recentSuspectPort)}
          ${di('Timestamp', record.recentSuspectTimestamp)}
        </div>

        <div class="detail-section-title">Recipient</div>
        <div class="detail-grid">
          ${di('Name', recipient.name)}
          ${di('Age', recipient.age)}
          ${di('Phone', recipient.phoneNumber)}
          ${di('Screen Name', recipient.screenName)}
          ${di('Profile URL', recipient.profileUrl)}
        </div>

        ${files.length ? `
        <div class="detail-section-title">Uploaded File(s)</div>
        <div class="detail-grid">
          ${files.map(f => di('Filename', f.filename) + di('MD5', f.hash)).join('')}
        </div>` : ''}

        <div class="detail-section-title">Content Classification</div>
        <div class="detail-grid">
          ${di('Rating', record.contentRating)}
          ${di('Ranking', record.contentRanking)}
          ${di('Term', record.contentTerm)}
        </div>

        <div class="detail-section-title">IP Geo-Lookup</div>
        <div class="detail-grid">
          ${di('IP', record.geoIp)}
          ${di('Country', record.geoCountry)}
          ${di('Region', record.geoRegion)}
          ${di('City', record.geoCity)}
          ${di('Postal Code', record.geoPostalCode)}
          ${di('ISP / Org', record.geoIspOrg)}
          ${di('Type', record.geoType)}
        </div>
      </div>
    </div>`;
}

function di(label, field) {
  const val = disp(field);
  return `
    <div class="detail-item">
      <span class="detail-label">${esc(label)}</span>
      <span class="detail-value ${val ? '' : 'unknown'}">${val ? esc(String(val)) : '—'}</span>
    </div>`;
}

function buildFailCard(failed) {
  return `
    <div class="case-card failure">
      <div class="case-card-header" style="cursor:default">
        <div class="case-card-left">
          <span class="status-dot failure"></span>
          <div>
            <div class="case-filename">${esc(failed.filename || 'Unknown file')}</div>
            <div class="case-report-id">Report #${esc(failed.reportId || 'UNKNOWN')}</div>
          </div>
        </div>
      </div>
      <div class="case-details" style="display:block">
        <div class="error-card">
          <span class="error-icon">⚠️</span>
          <div>
            <div class="error-text">Processing failed</div>
            <div class="error-reason">${esc(failed.failureReason || 'Unknown error')}</div>
          </div>
        </div>
      </div>
    </div>`;
}

// ── Accordion ─────────────────────────────────────
function wireAccordion(container) {
  container.querySelectorAll('.case-card.success .case-card-header').forEach(hdr => {
    hdr.addEventListener('click', () => {
      hdr.closest('.case-card').classList.toggle('expanded');
    });
  });
}

// ── Download helpers ──────────────────────────────
async function downloadSummaryPdf(recordOrJsonStr, stem, btn) {
  const originalText = btn ? btn.innerHTML : '';

  try {
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = '⏳ Generating...';
    }

    let record;

    if (typeof recordOrJsonStr === 'string') {
      record = JSON.parse(recordOrJsonStr);
    } else {
      record = recordOrJsonStr;
    }

    const response = await fetch('/api/cases/summary-pdf', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(record)
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`PDF generation failed (${response.status}): ${errorText}`);
    }

    const contentType = response.headers.get('content-type') || '';

    if (!contentType.includes('application/pdf')) {
      throw new Error(`Expected PDF but received ${contentType}`);
    }

    const blob = await response.blob();

    if (!blob.size) {
      throw new Error('Generated PDF is empty');
    }

    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.download = `${stem || 'CaseBrief'}_Summary_Brief.pdf`;
    link.style.display = 'none';

    document.body.appendChild(link);
    link.click();
    link.remove();

    setTimeout(() => {
      URL.revokeObjectURL(url);
    }, 1000);

    if (btn) {
      btn.innerHTML = '✓ Downloaded';
      setTimeout(() => {
        btn.innerHTML = originalText;
        btn.disabled = false;
      }, 1500);
    }

  } catch (error) {
    console.error('Summary PDF download failed:', error);

    if (btn) {
      btn.innerHTML = '✕ Failed';
      setTimeout(() => {
        btn.innerHTML = originalText;
        btn.disabled = false;
      }, 2000);
    }

    alert(`Could not download the Summary PDF.\n\n${error.message}`);
  }
}

async function downloadBatchZip(result, stem, btn) {
  const originalText = btn ? btn.innerHTML : '';
  try {
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = '⏳ Zipping…';
    }

    const response = await fetch('/api/cases/batch-summary-zip', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(result)
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`ZIP generation failed (${response.status}): ${errorText}`);
    }

    const blob = await response.blob();
    if (!blob.size) {
      throw new Error('Generated ZIP is empty');
    }

    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${stem || 'CaseBrief_Summaries'}.zip`;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    link.remove();

    setTimeout(() => { URL.revokeObjectURL(url); }, 1000);

    if (btn) {
      btn.innerHTML = '✓ Downloaded';
      setTimeout(() => {
        btn.innerHTML = originalText;
        btn.disabled = false;
      }, 1500);
    }
  } catch (error) {
    console.error('Batch ZIP download failed:', error);
    if (btn) {
      btn.innerHTML = '✕ Failed';
      setTimeout(() => {
        btn.innerHTML = originalText;
        btn.disabled = false;
      }, 2000);
    }
    alert(`Could not download the Summary ZIP.\n\n${error.message}`);
  }
}

async function downloadBatchExcel(result, stem, btn) {
  const originalText = btn ? btn.innerHTML : '';
  try {
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = '⏳ Exporting…';
    }

    const response = await fetch('/api/cases/excel', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(result)
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Excel export failed (${response.status}): ${errorText}`);
    }

    const blob = await response.blob();
    if (!blob.size) {
      throw new Error('Generated Excel file is empty');
    }

    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${stem || 'CaseBrief_Batch_Report'}.xlsx`;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    link.remove();

    setTimeout(() => { URL.revokeObjectURL(url); }, 1000);

    if (btn) {
      btn.innerHTML = '✓ Downloaded';
      setTimeout(() => {
        btn.innerHTML = originalText;
        btn.disabled = false;
      }, 1500);
    }
  } catch (error) {
    console.error('Excel export failed:', error);
    if (btn) {
      btn.innerHTML = '✕ Failed';
      setTimeout(() => {
        btn.innerHTML = originalText;
        btn.disabled = false;
      }, 2000);
    }
    alert(`Could not download the Excel report.\n\n${error.message}`);
  }
}

function downloadJson(jsonStr, stem) {
  const blob = new Blob([jsonStr], { type: 'application/json' });
  triggerDownload(blob, (stem || 'casebrief') + '.json');
}
function downloadBulkJson(jsonStr) {
  const blob = new Blob([jsonStr], { type: 'application/json' });
  triggerDownload(blob, 'casebrief-batch.json');
}
function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = filename;
  document.body.appendChild(a);
  a.click();
  setTimeout(() => { URL.revokeObjectURL(url); a.remove(); }, 1000);
}
function safeFileStem(record) {
  const id = disp(record?.reportId);
  return id ? `case-${id}` : 'case-brief';
}

// ── Modals ─────────────────────────────────────────
const modals = {
  about: document.getElementById('modal-about'),
  howto: document.getElementById('modal-howto'),
  feedback: document.getElementById('modal-feedback'),
  contact: document.getElementById('modal-contact'),
  privacy: document.getElementById('modal-privacy'),
  terms: document.getElementById('modal-terms'),
};

function openModal(name) {
  const el = modals[name];
  if (!el) return;
  el.classList.add('open');
  const workflow = el.querySelector('.steps');
  if (workflow) {
    workflow.querySelectorAll('.step').forEach((step, index) => {
      step.style.setProperty('--step-index', index);
    });
    workflow.classList.add('workflow-sequence');
    revealWhenVisible(workflow);
  }
  el.querySelectorAll('.about-section table tr').forEach((row, index) => {
    row.style.setProperty('--feature-index', index);
  });
  document.querySelectorAll('.nav-btn[data-open-modal]').forEach(btn => {
    btn.setAttribute('aria-expanded', String(btn.dataset.openModal === name));
  });
  document.body.style.overflow = 'hidden';
}
function closeModal(name) {
  const el = modals[name];
  if (!el) return;
  el.classList.remove('open');
  document.querySelectorAll(`.nav-btn[data-open-modal="${name}"]`).forEach(btn => {
    btn.setAttribute('aria-expanded', 'false');
  });
  document.body.style.overflow = '';
}

// Wire close buttons
document.querySelectorAll('[data-close-modal]').forEach(btn => {
  btn.addEventListener('click', () => closeModal(btn.dataset.closeModal));
});

// Wire open triggers
document.querySelectorAll('[data-open-modal]').forEach(el => {
  el.addEventListener('click', (e) => {
    e.preventDefault();
    openModal(el.dataset.openModal);
  });
});

// Click outside to close
Object.values(modals).forEach(el => {
  el?.addEventListener('click', (e) => {
    if (e.target === el) {
      const name = el.dataset.modalName;
      if (name) closeModal(name);
    }
  });
});

// Escape key
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    Object.keys(modals).forEach(closeModal);
  }
});

// ── Feedback form ─────────────────────────────────
const feedbackForm = document.getElementById('feedback-form');
const feedbackMsg = document.getElementById('feedback-msg');

feedbackForm?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const submitBtn = feedbackForm.querySelector('[type="submit"]');
  submitBtn.disabled = true;
  submitBtn.textContent = 'Sending…';
  feedbackMsg.className = 'alert-banner hidden';

  const payload = {
    name: feedbackForm.querySelector('#fb-name')?.value.trim() || '',
    email: feedbackForm.querySelector('#fb-email')?.value.trim() || '',
    category: feedbackForm.querySelector('#fb-category')?.value || '',
    message: feedbackForm.querySelector('#fb-message')?.value.trim() || '',
  };

  try {
    const res = await fetch('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await res.json();
    if (res.ok) {
      feedbackMsg.className = 'alert-banner success';
      feedbackMsg.innerHTML = `<span>✅</span><span>${esc(data.message || 'Thank you!')}</span>`;
      feedbackForm.reset();
    } else {
      feedbackMsg.className = 'alert-banner error';
      feedbackMsg.innerHTML = `<span>⚠️</span><span>${esc(data.error || 'Submission failed.')}</span>`;
    }
    feedbackMsg.classList.remove('hidden');
  } catch {
    feedbackMsg.className = 'alert-banner error';
    feedbackMsg.innerHTML = `<span>⚠️</span><span>Network error. Please try again.</span>`;
    feedbackMsg.classList.remove('hidden');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = 'Submit Feedback';
  }
});

// ── Utilities ─────────────────────────────────────
function esc(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
function rand() {
  return Math.random().toString(36).slice(2, 9);
}
