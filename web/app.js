'use strict';

/* ============================================================================
   UploadGo — web preview
   Faithful browser mirror of the Android app: Select → Preview → (ZIP) → Share
   → Queue → History → Settings. In the browser, "Share" opens the Web Share
   API when available (or a Sharesheet-style dialog); on Android the real app
   hands files to the native Sharesheet (Telegram, WhatsApp, Drive, …).
   ========================================================================== */

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

const uid = () => 'f' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36);

const MAX_SINGLE_ENTRY = 250 * 1024 * 1024; // per-entry decompressed guard
const SHARE_GRACE_MS = 5 * 60 * 1000;

/* ------------------------------- utilities ------------------------------- */

function formatBytes(n) {
  if (n == null || Number.isNaN(n)) return '—';
  if (n < 1024) return n + ' B';
  const units = ['KB', 'MB', 'GB', 'TB'];
  let v = n, i = -1;
  do { v /= 1024; i++; } while (v >= 1024 && i < units.length - 1);
  return v.toFixed(1) + ' ' + units[i];
}

function extOf(name) {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i + 1).toLowerCase() : '';
}

const KIND_BY_EXT = {
  jpg: 'image', jpeg: 'image', png: 'image', webp: 'image', gif: 'image',
  mp4: 'video', mov: 'video', mkv: 'video', zip: 'zip',
};

const TYPE_LABEL = { image: 'Image', video: 'Video', zip: 'ZIP', file: 'File' };

const MIME_BY_EXT = {
  jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp',
  gif: 'image/gif', mp4: 'video/mp4', mov: 'video/quicktime',
  mkv: 'video/x-matroska', zip: 'application/zip',
};

const kindOf = (name) => KIND_BY_EXT[extOf(name)] || 'unsupported';
const mimeFor = (name) => MIME_BY_EXT[extOf(name)] || 'application/octet-stream';
const isMedia = (name) => { const k = kindOf(name); return k === 'image' || k === 'video'; };
const baseName = (name) => { const p = name.split('/').filter(Boolean); return p.length ? p[p.length - 1] : name; };

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function formatTime(ts) {
  return new Date(ts).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}
function dayGroup(ts) {
  const d = new Date(ts), now = new Date();
  const s = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).toDateString();
  if (s(d) === s(now)) return 'Today';
  const y = new Date(now); y.setDate(now.getDate() - 1);
  if (s(d) === s(y)) return 'Yesterday';
  return d.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
}

/* -------------------------------- icons ---------------------------------- */

const ICONS = {
  home: '<path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/>',
  queue: '<path d="M22 7h-9v2h9V7zm0 8h-9v2h9v-2zM5.54 11 2 7.46l1.41-1.41 2.12 2.12 4.24-4.24 1.41 1.41L5.54 11zm0 8-3.54-3.54 1.41-1.41 2.12 2.12 4.24-4.24 1.41 1.41L5.54 19z"/>',
  history: '<path d="M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"/>',
  settings: '<path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96a7.04 7.04 0 0 0-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.488.488 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 0 0-.12-.61l-2.01-1.58zM12 15.6A3.61 3.61 0 0 1 8.4 12c0-1.98 1.62-3.6 3.6-3.6s3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>',
  share: '<path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z"/>',
  close: '<path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>',
  back: '<path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>',
  grid: '<path d="M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z"/>',
  list: '<path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"/>',
  selectAll: '<path d="M3 5h2V3c-1.1 0-2 .9-2 2zm0 8h2v-2H3v2zm4 8h2v-2H7v2zM3 9h2V7H3v2zm10-6h-2v2h2V3zm6 0v2h2c0-1.1-.9-2-2-2zM5 21v-2H3c0 1.1.9 2 2 2zm-2-4h2v-2H3v2zM9 3H7v2h2V3zm2 18h2v-2h-2v2zm8-8h2v-2h-2v2zm0 8c1.1 0 2-.9 2-2h-2v2zm0-12h2V7h-2v2zm0 8h2v-2h-2v2zm-4 4h2v-2h-2v2zm0-16h2V3h-2v2zM7 17h10V7H7v10zm2-8h6v6H9V9z"/>',
  clear: '<path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>',
  check: '<path d="M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>',
  zip: '<path d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z"/>',
  warning: '<path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/>',
  play: '<path d="M8 5v14l11-7z"/>',
  delete: '<path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>',
  add: '<path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>',
};

const icon = (name, cls = '') => `<svg class="${cls}" viewBox="0 0 24 24" aria-hidden="true" focusable="false">${ICONS[name] || ''}</svg>`;

/* -------------------------------- state ---------------------------------- */

const state = {
  tab: 'home',
  files: [],            // {id, name, size, type, kind, url, source, file?}
  included: new Set(),
  zips: new Map(),      // id -> {status, total, supported, unsupported, media, included:Set, error, blobs:[]}
  view: 'grid',
  overlay: null,        // {type:'image'|'video'|'zip'|'share', id?} 
  pendingShare: [],
  settings: loadSettings(),
  history: loadHistory(),
};

const viewer = { list: [], index: 0, scale: 1, tx: 0, ty: 0 };

function loadSettings() {
  try {
    const s = JSON.parse(localStorage.getItem('uploadgo.settings') || 'null') || {};
    return {
      defaultBehavior: s.defaultBehavior || 'contents',   // 'contents' | 'selected'
      autoExtract: s.autoExtract !== false,
      showUnsupported: s.showUnsupported !== false,
      deleteTemp: s.deleteTemp !== false,
      theme: s.theme || 'system',
    };
  } catch { return { defaultBehavior: 'contents', autoExtract: true, showUnsupported: true, deleteTemp: true, theme: 'system' }; }
}
function saveSettings() { localStorage.setItem('uploadgo.settings', JSON.stringify(state.settings)); }

function loadHistory() {
  try { return JSON.parse(localStorage.getItem('uploadgo.history') || '[]'); } catch { return []; }
}
function saveHistory() { localStorage.setItem('uploadgo.history', JSON.stringify(state.history)); }

function recordHistory(entry) {
  state.history.unshift({ ts: Date.now(), ...entry });
  state.history = state.history.slice(0, 500);
  saveHistory();
}

function includedFiles() { return state.files.filter((f) => state.included.has(f.id)); }
function hasIncludedZip() { return includedFiles().some((f) => f.kind === 'zip'); }

/* ----------------------------- ZIP extraction ---------------------------- */

function parseZip(buf) {
  const u8 = new Uint8Array(buf);
  const dv = new DataView(buf);
  if (u8.length < 22) throw new Error('ZIP too small');
  let eocd = -1;
  const min = Math.max(0, u8.length - 22 - 65536);
  for (let i = u8.length - 22; i >= min; i--) {
    if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('End-of-central-directory not found');
  const count = dv.getUint16(eocd + 10, true);
  let off = dv.getUint32(eocd + 16, true);
  const entries = [];
  for (let i = 0; i < count; i++) {
    if (dv.getUint32(off, true) !== 0x02014b50) break;
    entries.push({
      name: new TextDecoder('utf-8').decode(u8.subarray(off + 46, off + 46 + dv.getUint16(off + 28, true))),
      method: dv.getUint16(off + 10, true),
      compressedSize: dv.getUint32(off + 20, true),
      uncompressedSize: dv.getUint32(off + 24, true),
      localOffset: dv.getUint32(off + 42, true),
    });
    off += 46 + dv.getUint16(off + 28, true) + dv.getUint16(off + 30, true) + dv.getUint16(off + 32, true);
  }
  return entries;
}

async function readEntry(buf, e) {
  const u8 = new Uint8Array(buf);
  const dv = new DataView(buf);
  const lo = e.localOffset;
  if (dv.getUint32(lo, true) !== 0x04034b50) return null;
  const nameLen = dv.getUint16(lo + 26, true);
  const extraLen = dv.getUint16(lo + 28, true);
  const start = lo + 30 + nameLen + extraLen;
  const raw = u8.subarray(start, start + e.compressedSize);
  if (e.method === 0) return raw;
  if (e.method === 8) {
    if (typeof DecompressionStream === 'undefined') {
      throw new Error('This browser cannot decompress ZIP entries');
    }
    const stream = new Blob([raw]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
    return new Uint8Array(await new Response(stream).arrayBuffer());
  }
  return null; // unsupported compression method
}

function isSafePath(name) {
  if (name.startsWith('/') || name.includes('\\')) return false;
  return !name.split('/').some((p) => p === '..');
}
function isJunk(name) {
  return name.split('/').some((p) => p === '__MACOSX' || p === '.DS_Store' || p.startsWith('._'));
}

async function extractZip(file) {
  const id = file.id;
  const z = { status: 'extracting', total: 0, supported: 0, unsupported: 0, media: [], included: new Set(), error: null, blobs: [] };
  state.zips.set(id, z);
  render();
  try {
    const buf = await file.file.arrayBuffer();
    const entries = parseZip(buf);
    const media = [];
    let unsupported = 0;
    for (const e of entries) {
      if (e.name.endsWith('/')) continue;
      if (isJunk(e.name) || !isSafePath(e.name) || !isMedia(e.name)) { unsupported++; continue; }
      if (e.compressedSize > MAX_SINGLE_ENTRY) { unsupported++; continue; }
      let bytes;
      try { bytes = await readEntry(buf, e); } catch { unsupported++; continue; }
      if (!bytes) { unsupported++; continue; }
      const blob = new Blob([bytes], { type: mimeFor(e.name) });
      const url = URL.createObjectURL(blob);
      z.blobs.push(url);
      media.push({
        id: uid(), name: baseName(e.name), size: e.uncompressedSize || blob.size,
        type: mimeFor(e.name), kind: KIND_BY_EXT[extOf(e.name)], url, source: 'extracted',
        originZipId: id, originZipName: file.name,
      });
    }
    z.total = entries.length;
    z.supported = media.length;
    z.unsupported = unsupported;
    z.media = media;
    z.included = new Set(media.map((m) => m.id));
    z.status = 'ready';
    recordHistory({ type: 'PREPARED', category: 'ZIP Contents', itemCount: media.length, detail: file.name + ' • ' + media.length + ' media files' });
  } catch (err) {
    z.status = 'error';
    z.error = 'Could not open ZIP — it may be corrupted or unsupported.';
  }
  render();
}

/* ------------------------------- rendering ------------------------------- */

function applyTheme() {
  const t = state.settings.theme;
  if (t === 'system') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', t);
}

function render() {
  applyTheme();
  const app = $('#app');
  app.innerHTML = topBarHTML() + `<main class="main">` + mainHTML() + `</main>` + navHTML();
  renderOverlay();
}

function topBarHTML() {
  if (state.tab === 'settings') {
    return `<header class="topbar"><div class="topbar-inner">
      <button class="icon-btn" data-action="back" aria-label="Back">${icon('back')}</button>
      <div class="topbar-title"><div class="title">Settings</div></div>
    </div></header>`;
  }
  const titles = { home: ['UploadGo', 'Select. Share. Done.'], queue: ['Queue', ''], history: ['History', ''] };
  const [title, subtitle] = titles[state.tab] || titles.home;
  const right =
    state.tab === 'home'
      ? `<button class="icon-btn" data-action="settings" aria-label="Settings">${icon('settings')}</button>`
      : state.tab === 'history' && state.history.length
        ? `<button class="icon-btn" data-action="clear-history" aria-label="Clear history">${icon('delete')}</button>`
        : '';
  return `<header class="topbar"><div class="topbar-inner">
    <div class="topbar-title"><div class="title">${title}</div>${subtitle ? `<div class="subtitle">${subtitle}</div>` : ''}</div>
    ${right}
  </div></header>`;
}

function navHTML() {
  const items = [['home', 'home', 'Home'], ['queue', 'queue', 'Queue'], ['history', 'history', 'History']];
  return `<nav class="bottomnav">` + items.map(([tab, ic, label]) =>
    `<button class="nav-item ${state.tab === tab ? 'active' : ''}" data-action="tab" data-tab="${tab}" aria-label="${label}">${icon(ic)}<span>${label}</span></button>`
  ).join('') + `</nav>`;
}

function mainHTML() {
  switch (state.tab) {
    case 'home': return homeHTML();
    case 'queue': return queueHTML();
    case 'history': return historyHTML();
    case 'settings': return settingsHTML();
    default: return '';
  }
}

/* ---- Home ---- */

function thumbHTML(item, small = false) {
  if (item.kind === 'image') return `<img class="thumb" src="${item.url}" alt="${escapeHtml(item.name)}" loading="lazy">`;
  if (item.kind === 'video') return `<video class="thumb" src="${item.url}" muted playsinline preload="metadata"></video>`;
  if (item.kind === 'zip') return `<div class="thumb thumb-zip">📦</div>`;
  return `<div class="thumb thumb-zip">📄</div>`;
}

function tileHTML(item, grid) {
  const selected = state.included.has(item.id);
  const zipBadge = item.kind === 'zip' ? zipBadgeHTML(item.id) : '';
  const type = TYPE_LABEL[item.kind] || 'File';
  if (grid) {
    return `<div class="tile" data-action="open" data-id="${item.id}" role="button" tabindex="0" aria-label="${escapeHtml(item.name)}">
      <div class="thumbwrap">${thumbHTML(item)}
        <input type="checkbox" class="tile-check" data-id="${item.id}" ${selected ? 'checked' : ''} aria-label="Select ${escapeHtml(item.name)}">
        ${zipBadge}
      </div>
      <div class="meta"><div class="name">${escapeHtml(item.name)}</div><div class="sub">${type} • ${formatBytes(item.size)}</div></div>
    </div>`;
  }
  return `<div class="listrow" data-action="open" data-id="${item.id}" role="button" tabindex="0">
    <div class="thumbwrap">${thumbHTML(item)}${zipBadge}</div>
    <div class="meta"><div class="name">${escapeHtml(item.name)}</div><div class="sub">${type} • ${formatBytes(item.size)}</div></div>
    <input type="checkbox" class="tile-check" data-id="${item.id}" ${selected ? 'checked' : ''} aria-label="Select ${escapeHtml(item.name)}">
  </div>`;
}

function zipBadgeHTML(id) {
  const z = state.zips.get(id);
  if (!z) return '';
  if (z.status === 'extracting') return `<span class="badge"><span class="spinner" style="width:11px;height:11px;border-width:2px"></span> Extracting</span>`;
  if (z.status === 'error') return `<span class="badge err">⚠ Could not open</span>`;
  return `<span class="badge">${icon('check', '')} ${z.total} files • ${z.supported} media</span>`;
}

function homeHTML() {
  const files = state.files;
  const inc = includedFiles();
  const anyZip = hasIncludedZip();

  let html = '';

  if (files.length === 0) {
    html += `<button class="hero" data-action="pick">
      <span class="plus">${icon('add')}</span>
      <h2>Select Files</h2>
      <p>Photos • Videos • ZIP</p>
    </button>`;
    html += `<div class="empty">
      <div class="big">📦</div><h3>No files selected</h3>
      <p>Tap “Select Files” to pick photos, videos or ZIP archives from your device.</p>
    </div>`;
  } else {
    html += `<div class="btn-row"><button class="btn btn-outline" data-action="pick">${icon('add')} Select Files</button></div>`;

    html += `<div class="section-head">
      <div class="label">Selected Files</div>
      <button class="icon-btn" data-action="view-list" aria-label="List view" style="width:40px;height:40px">${icon('list')}</button>
      <button class="icon-btn" data-action="view-grid" aria-label="Grid view" style="width:40px;height:40px">${icon('grid')}</button>
    </div>
    <div class="chip-row">
      <span class="count" style="align-self:center;font-size:13px;color:var(--on-surface-variant)">Selected: ${inc.length} files</span>
      <span style="flex:1"></span>
      <button class="chip" data-action="select-all">${icon('check')} Select All</button>
      <button class="chip" data-action="clear-all">${icon('clear')} Clear All</button>
    </div>`;

    html += files.length
      ? `<div class="${state.view === 'grid' ? 'grid' : 'list'}">` + files.map((f) => tileHTML(f, state.view === 'grid')).join('') + `</div>`
      : '';

    // Share area
    html += `<div style="margin-top:14px">`;
    if (anyZip) {
      const primaryIsContents = state.settings.defaultBehavior === 'contents';
      html += `<div style="display:flex;flex-direction:column;gap:8px">
        <button class="btn btn-primary" data-action="share-primary" ${inc.length ? '' : 'disabled'}>${icon('share')} ${primaryIsContents ? 'Share ZIP Contents' : 'Share Selected'}</button>
        <button class="btn btn-outline" data-action="share-secondary" ${inc.length ? '' : 'disabled'}>${icon('share')} ${primaryIsContents ? 'Share Selected' : 'Share ZIP Contents'}</button>
        <button class="btn btn-text" data-action="share-original-zip" ${inc.some((f) => f.kind === 'zip') ? '' : 'disabled'}>${icon('zip')} Share Original ZIP</button>
      </div>`;
    } else {
      html += `<button class="btn btn-primary" data-action="share-selected" ${inc.length ? '' : 'disabled'}>${icon('share')} Share</button>`;
    }
    html += `<p class="hint">Choose any app — Telegram, WhatsApp, Drive and others — to send the files. The receiving app handles the actual upload.</p>`;
    html += `</div>`;
  }
  return html;
}

/* ---- Queue ---- */

function queueHTML() {
  const inc = includedFiles();
  const zips = state.files.filter((f) => f.kind === 'zip');
  const recent = state.history.slice(0, 5);
  let html = '';

  html += `<div class="section-title">Ready to share</div>`;
  if (inc.length === 0) {
    html += `<div class="empty"><div class="big">📋</div><h3>Nothing queued yet</h3>
      <p>Select files on the Home tab and they will appear here, ready to share.</p>
      <button class="btn btn-outline" data-action="goto-home" style="margin-top:14px">${icon('add')} Select Files</button></div>`;
  } else {
    html += `<div class="card">
      <div class="section-head"><div class="label">${inc.length} files ready</div>
        <span class="count">${formatBytes(inc.reduce((a, f) => a + (f.size || 0), 0))}</span></div>
      ${inc.slice(0, 6).map((f) => `<div class="qrow">
        <div class="ic">${f.kind === 'image' ? '🖼️' : f.kind === 'video' ? '🎬' : '📦'}</div>
        <div class="meta"><div class="name">${escapeHtml(f.name)}</div><div class="sub">${TYPE_LABEL[f.kind]}</div></div>
        <div class="status">${formatBytes(f.size)}</div>
      </div>`).join('')}
      ${inc.length > 6 ? `<div class="hint" style="margin-top:6px">+ ${inc.length - 6} more</div>` : ''}
      <button class="btn btn-primary" data-action="share-selected" style="margin-top:12px">${icon('share')} Share</button>
    </div>`;
  }

  html += `<div class="section-title">ZIP extraction</div>`;
  if (zips.length === 0) {
    html += `<p class="hint">No ZIP extraction tasks.</p>`;
  } else {
    html += zips.map((f) => {
      const z = state.zips.get(f.id);
      let sub = 'Not extracted';
      let status = '';
      if (z && z.status === 'extracting') { sub = 'Extracting…'; status = `<span class="spinner" style="width:20px;height:20px;border-width:3px"></span>`; }
      else if (z && z.status === 'error') { sub = 'Could not open ZIP'; status = icon('warning'); }
      else if (z && z.status === 'ready') { sub = `${z.total} files • ${z.supported} media`; status = icon('check'); }
      return `<div class="qrow" data-action="zip-open" data-id="${f.id}" role="button" tabindex="0">
        <div class="ic">${icon('zip')}</div>
        <div class="meta"><div class="name">${escapeHtml(f.name)}</div><div class="sub">${sub}</div></div>
        <div class="status" style="color:var(--primary)">${status}</div>
      </div>`;
    }).join('');
  }

  if (recent.length) {
    html += `<div class="section-title">Recently shared</div>`;
    html += recent.map((h) => `<div class="hrow">
      <div class="ic">${icon('check')}</div>
      <div class="meta">
        <div class="t1">${h.type === 'SHARED' ? h.itemCount + ' files shared' : h.itemCount + ' files prepared'}</div>
        <div class="t2">${escapeHtml(h.category || '')}</div>
      </div>
      <div class="time">${formatTime(h.ts)}</div>
    </div>`).join('');
  }
  return html;
}

/* ---- History ---- */

function historyHTML() {
  if (!state.history.length) {
    return `<div class="empty"><div class="big">🕓</div><h3>No history yet</h3>
      <p>Files you prepare and share will be recorded here.</p></div>`;
  }
  const groups = {};
  for (const h of state.history) (groups[dayGroup(h.ts)] ||= []).push(h);
  let html = '';
  for (const [day, items] of Object.entries(groups)) {
    html += `<div class="section-title">${escapeHtml(day)}</div>`;
    html += items.map((h) => `<div class="hrow">
      <div class="ic">${icon('check')}</div>
      <div class="meta">
        <div class="t1">${h.type === 'SHARED' ? h.itemCount + ' files shared' : h.itemCount + ' files prepared'}</div>
        <div class="t2">${escapeHtml(h.category || '')}</div>
        <div class="t2">${escapeHtml(h.detail || '')}</div>
      </div>
      <div class="time">${formatTime(h.ts)}</div>
    </div>`).join('');
  }
  html += `<p class="hint" style="margin-top:14px">History records what UploadGo prepared and handed to the Android Sharesheet. The receiving app controls the final delivery.</p>`;
  return html;
}

/* ---- Settings ---- */

function settingsHTML() {
  const s = state.settings;
  const tempBytes = tempStorageBytes();
  return `
  <div class="set-group-title">Sharing</div>
  <div class="set-label">Default share behavior</div>
  <button class="radio" data-action="setting-behavior" data-value="selected"><input type="radio" name="behavior" ${s.defaultBehavior === 'selected' ? 'checked' : ''}><span>Share selected files</span></button>
  <button class="radio" data-action="setting-behavior" data-value="contents"><input type="radio" name="behavior" ${s.defaultBehavior === 'contents' ? 'checked' : ''}><span>Share ZIP contents</span></button>

  <div class="set-group-title">ZIP</div>
  ${switchRow('auto_extract', 'Auto-extract ZIP', 'Extract media as soon as a ZIP is selected', s.autoExtract)}
  ${switchRow('show_unsupported', 'Show unsupported files', 'List skipped files inside ZIP archives', s.showUnsupported)}
  ${switchRow('delete_temp', 'Delete temporary extracted files after sharing', 'Cleans up extracted ZIP contents a few minutes after sharing', s.deleteTemp)}

  <div class="set-group-title">Appearance</div>
  <div class="set-label">Theme</div>
  ${['system', 'light', 'dark'].map((t) => `<button class="radio" data-action="setting-theme" data-value="${t}"><input type="radio" name="theme" ${s.theme === t ? 'checked' : ''}><span>${t[0].toUpperCase() + t.slice(1)}</span></button>`).join('')}

  <div class="set-group-title">Storage</div>
  <div class="card" style="margin-top:8px">
    <div class="section-head"><div class="label">Temporary storage</div>
      <span class="count">${tempBytes ? formatBytes(tempBytes) + ' used by extracted ZIP contents' : 'No temporary files'}</span></div>
    <button class="btn btn-outline" data-action="clear-temp" ${tempBytes ? '' : 'disabled'} style="margin-top:10px">Clear Temporary Files</button>
  </div>

  <div class="set-group-title">About</div>
  <div class="setrow"><div class="meta"><div class="t1">UploadGo</div><div class="t2">Select. Share. Done.</div></div></div>
  <div class="setrow"><div class="meta"><div class="t1">Version</div><div class="t2">1.0.0</div></div></div>
  <button class="radio" data-action="expand" data-target="privacy"><input type="checkbox"> <span>Privacy</span></button>
  <div id="x-privacy" hidden class="hint" style="padding:0 20px 6px">UploadGo never uploads anything itself. It prepares files and hands them to the Android Sharesheet. No accounts, no login, no cloud service.</div>
  <button class="radio" data-action="expand" data-target="licenses"><input type="checkbox"> <span>Open Source Licenses</span></button>
  <div id="x-licenses" hidden class="hint" style="padding:0 20px 6px">This web preview uses no third-party libraries. The Android app is built with Kotlin, Jetpack Compose, Material 3, Coil, Media3, Room and DataStore.</div>

  <p class="hint" style="margin-top:16px">Choose any app — Telegram, WhatsApp, Drive and others — to send the files. The receiving app handles the actual upload.</p>`;
}

function switchRow(key, title, sub, checked) {
  return `<div class="setrow">
    <div class="meta"><div class="t1">${title}</div><div class="t2">${sub}</div></div>
    <label class="switch"><input type="checkbox" data-key="${key}" ${checked ? 'checked' : ''}><span class="track"></span><span class="knob"></span></label>
  </div>`;
}

function tempStorageBytes() {
  let total = 0;
  for (const z of state.zips.values()) for (const m of z.media) total += m.size || 0;
  return total;
}

/* ------------------------------- overlays -------------------------------- */

function renderOverlay() {
  const root = $('#overlay-root');
  if (!state.overlay) { root.innerHTML = ''; return; }
  const o = state.overlay;
  if (o.type === 'image') root.innerHTML = imageOverlayHTML(o.id);
  else if (o.type === 'video') root.innerHTML = videoOverlayHTML(o.id);
  else if (o.type === 'zip') root.innerHTML = zipOverlayHTML(o.id);
  else if (o.type === 'share') root.innerHTML = shareSheetHTML();
}

function imageList() {
  const inc = state.files.filter((f) => f.kind === 'image' && state.included.has(f.id));
  return inc.length ? inc : state.files.filter((f) => f.kind === 'image');
}

function imageOverlayHTML(id) {
  const list = imageList();
  const idx = Math.max(0, list.findIndex((f) => f.id === id));
  const item = list[idx];
  if (!item) return `<div class="overlay dark"><div class="overlay-head">${headRow('', '', () => '')}</div></div>`;
  viewer.list = list; viewer.index = idx; viewer.scale = 1; viewer.tx = 0; viewer.ty = 0;
  return `<div class="overlay dark">
    <div class="overlay-head">
      <button class="icon-btn" data-action="overlay-close" aria-label="Close" style="color:#fff">${icon('close')}</button>
      <div class="meta"><div class="name">${escapeHtml(item.name)}</div><div class="sub">${formatBytes(item.size)}</div></div>
    </div>
    <div class="viewer-canvas" id="viewer-canvas">
      <img src="${item.url}" alt="${escapeHtml(item.name)}" id="viewer-img">
    </div>
    <div class="viewer-tools">
      <button class="icon-btn" data-action="img-prev" aria-label="Previous" style="color:#fff">${icon('back')}</button>
      <span class="pager">${viewer.index + 1} / ${list.length}</span>
      <button class="icon-btn" data-action="img-next" aria-label="Next" style="color:#fff">${icon('back').replace('viewBox="0 0 24 24"', 'viewBox="0 0 24 24" style="transform:scaleX(-1)"')}</button>
      <span style="width:8px"></span>
      <div class="pill">
        <button data-action="img-zoom-out" aria-label="Zoom out">−</button>
        <button data-action="img-reset" aria-label="Reset zoom">1:1</button>
        <button data-action="img-zoom-in" aria-label="Zoom in">+</button>
      </div>
    </div>
  </div>`;
}

function videoOverlayHTML(id) {
  const item = state.files.find((f) => f.id === id);
  if (!item) return `<div class="overlay dark"><div class="overlay-head"><button class="icon-btn" data-action="overlay-close" style="color:#fff">${icon('close')}</button></div></div>`;
  return `<div class="overlay dark">
    <div class="overlay-head">
      <button class="icon-btn" data-action="overlay-close" aria-label="Close" style="color:#fff">${icon('close')}</button>
      <div class="meta"><div class="name">${escapeHtml(item.name)}</div><div class="sub">${formatBytes(item.size)}</div></div>
    </div>
    <div class="video-stage"><video src="${item.url}" controls autoplay playsinline></video></div>
  </div>`;
}

function zipOverlayHTML(id) {
  const file = state.files.find((f) => f.id === id);
  if (!file) return `<div class="overlay"><div class="overlay-head"><button class="icon-btn" data-action="overlay-close">${icon('close')}</button></div></div>`;

  const z = state.zips.get(id);
  let body = '';

  if (!z || z.status === 'extracting') {
    body = `<div class="center"><div class="spinner"></div><div>Extracting ZIP…</div></div>`;
  } else if (z.status === 'error') {
    body = `<div class="center">
      <div style="font-size:44px;color:var(--error)">${icon('warning')}</div>
      <h3 style="margin:10px 0 4px">Could not open ZIP</h3>
      <p class="hint">${escapeHtml(z.error || '')}</p>
      <div class="btn-row" style="margin-top:16px">
        <button class="btn btn-primary" data-action="zip-retry" data-id="${id}">Try again</button>
        <button class="btn btn-outline" data-action="overlay-close">OK</button>
      </div>
    </div>`;
  } else {
    const inc = z.included;
    body = `
    <div class="ziphead">
      <div class="row">
        <div class="zicon">📦</div>
        <div style="min-width:0">
          <div class="name">${escapeHtml(file.name)}</div>
          <div class="sub">${z.total} files • ${z.supported} media</div>
        </div>
      </div>
      ${z.unsupported > 0 && state.settings.showUnsupported ? `<div class="skipped-note">${icon('warning')} ${z.unsupported} unsupported ${z.unsupported === 1 ? 'file' : 'files'} will be skipped.</div>` : ''}
      <div class="chip-row" style="margin-top:8px">
        <button class="chip" data-action="zip-select-all" data-id="${id}">${icon('check')} Select All</button>
        <button class="chip" data-action="zip-clear-all" data-id="${id}">${icon('clear')} Clear All</button>
        <span style="flex:1"></span>
        <span class="count">Selected: ${inc.size}</span>
      </div>
    </div>
    <div class="zip-media grid">
      ${z.media.map((m) => `<div class="tile" data-action="zip-toggle" data-id="${m.id}" data-zip="${id}">
        <div class="thumbwrap">${thumbHTML(m)}<input type="checkbox" class="tile-check" data-zip="${id}" data-id="${m.id}" ${inc.has(m.id) ? 'checked' : ''}></div>
        <div class="meta"><div class="name">${escapeHtml(m.name)}</div><div class="sub">${TYPE_LABEL[m.kind]} • ${formatBytes(m.size)}</div></div>
      </div>`).join('')}
    </div>`;
  }

  return `<div class="overlay">
    <div class="overlay-head">
      <button class="icon-btn" data-action="overlay-close" aria-label="Back">${icon('back')}</button>
      <div class="meta"><div class="name">ZIP Preview</div><div class="sub">${escapeHtml(file.name)}</div></div>
    </div>
    <div class="overlay-body">${body}</div>
    ${z && z.status === 'ready' ? `<div class="overlay-foot">
      <div style="display:flex;flex-direction:column;gap:8px">
        <button class="btn btn-primary" data-action="zip-share-contents" data-id="${id}" ${z.included.size ? '' : 'disabled'}>${icon('share')} Share Contents</button>
        <button class="btn btn-outline" data-action="zip-share-original" data-id="${id}">${icon('zip')} Share Original ZIP</button>
      </div>
      <p class="hint" style="margin-top:8px">The original ZIP is never modified or deleted.</p>
    </div>` : ''}
  </div>`;
}

function shareSheetHTML() {
  const items = state.pendingShare;
  return `<div class="sheet">
    <div class="sheet-card">
      <h3>Share via Android Sharesheet</h3>
      <div class="sub">${items.length} ${items.length === 1 ? 'file' : 'files'} prepared</div>
      <div class="sheet-list">
        ${items.slice(0, 8).map((f) => `<div class="qrow">
          <div class="ic">${f.kind === 'image' ? '🖼️' : f.kind === 'video' ? '🎬' : '📄'}</div>
          <div class="meta"><div class="name">${escapeHtml(f.name)}</div><div class="sub">${TYPE_LABEL[f.kind]} • ${formatBytes(f.size)}</div></div>
        </div>`).join('')}
        ${items.length > 8 ? `<div class="hint" style="margin-top:6px">+ ${items.length - 8} more</div>` : ''}
      </div>
      <div class="sheet-note">On Android, this opens the system Sharesheet — choose Telegram, WhatsApp, Google Drive or any installed app. UploadGo hands the files over; the receiving app performs the upload.</div>
      <div class="btn-row">
        <button class="btn btn-outline" data-action="share-cancel">Cancel</button>
        <button class="btn btn-primary" data-action="share-confirm">${icon('share')} Share</button>
      </div>
    </div>
  </div>`;
}

/* ------------------------------ share flow ------------------------------- */

async function collectShareSelected() { return includedFiles(); }

async function collectShareContents() {
  const out = [];
  for (const f of state.files) {
    if (!state.included.has(f.id)) continue;
    if (f.kind === 'zip') {
      if (!state.zips.has(f.id) || state.zips.get(f.id).status !== 'ready') await extractZip(f);
      const z = state.zips.get(f.id);
      if (!z || z.status !== 'ready') { showToast('Could not extract ' + f.name); continue; }
      for (const m of z.media) if (z.included.has(m.id)) out.push(m);
    } else out.push(f);
  }
  return out;
}

function collectShareOriginalZips() { return includedFiles().filter((f) => f.kind === 'zip'); }

function collectShareZipMedia(id) {
  const z = state.zips.get(id);
  if (!z || z.status !== 'ready') return [];
  return z.media.filter((m) => z.included.has(m.id));
}

function openShare(items) {
  if (!items.length) { showToast('There is nothing selected to share.'); return; }
  state.pendingShare = items;
  state.overlay = { type: 'share' };
  render();
}

async function confirmShare() {
  const items = state.pendingShare;
  const category = items.some((f) => f.source === 'extracted') ? 'ZIP Contents'
    : items.every((f) => f.kind === 'zip') ? 'Original ZIP'
    : items.some((f) => f.kind === 'image') && items.some((f) => f.kind === 'video') ? 'Images + Videos'
    : items.some((f) => f.kind === 'image') ? 'Images'
    : items.some((f) => f.kind === 'video') ? 'Videos' : 'Files';

  // Try the real Web Share API with file attachments (Chrome/Edge on Android).
  const fileObjects = await Promise.all(items.map(async (f) => {
    if (f.file) return f.file;
    try { const blob = await fetch(f.url).then((r) => r.blob()); return new File([blob], f.name, { type: f.type }); }
    catch { return new File([], f.name, { type: f.type }); }
  }));

  let shared = false;
  if (navigator.canShare && navigator.canShare({ files: fileObjects })) {
    try { await navigator.share({ files: fileObjects }); shared = true; }
    catch (err) { if (err && err.name !== 'AbortError') shared = true; /* treat as handed off */ }
  }

  recordHistory({ type: 'SHARED', category, itemCount: items.length, detail: 'Shared via Android Sharesheet' });
  if (state.settings.deleteTemp) scheduleExtractedCleanup(items);
  state.overlay = null;
  render();
  showToast(shared ? 'Shared via Android Sharesheet' : 'Prepared — choose a sharing app');
}

function scheduleExtractedCleanup(items) {
  const zips = new Set(items.filter((f) => f.source === 'extracted').map((f) => f.originZipId));
  setTimeout(() => {
    for (const id of zips) {
      const z = state.zips.get(id);
      if (z) { z.blobs.forEach((u) => URL.revokeObjectURL(u)); state.zips.delete(id); }
    }
    render();
  }, SHARE_GRACE_MS);
}

/* ------------------------------ toast ------------------------------------ */

let toastTimer = null;
function showToast(msg) {
  const root = $('#toast-root');
  root.innerHTML = `<div class="toast">${escapeHtml(msg)}</div>`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { root.innerHTML = ''; }, 3200);
}

/* ---------------------------- event handling ----------------------------- */

const fileInput = $('#fileInput');
fileInput.addEventListener('change', () => {
  handlePicked(Array.from(fileInput.files));
  fileInput.value = '';
});

function handlePicked(list) {
  let skipped = 0;
  for (const f of list) {
    const kind = kindOf(f.name);
    if (kind === 'unsupported') { skipped++; continue; }
    const url = (kind === 'image' || kind === 'video') ? URL.createObjectURL(f) : null;
    const item = { id: uid(), name: f.name, size: f.size, type: f.type || mimeFor(f.name), kind, url, source: 'picked', file: f };
    state.files.push(item);
    state.included.add(item.id);
  }
  render();
  if (skipped) showToast(skipped + ' unsupported file' + (skipped === 1 ? '' : 's') + ' skipped');
  if (state.settings.autoExtract) {
    state.files.filter((f) => f.kind === 'zip' && !state.zips.has(f.id)).forEach((f) => extractZip(f));
  }
}

document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-action]');
  if (!el) return;
  // Checkbox clicks are handled by the 'change' listener.
  if (e.target.matches('.tile-check') || e.target.matches('.switch input')) return;

  const action = el.dataset.action;
  const id = el.dataset.id;
  const zipId = el.dataset.zip;

  switch (action) {
    case 'tab': state.tab = el.dataset.tab; render(); break;
    case 'settings': state.tab = 'settings'; render(); break;
    case 'back': state.tab = 'home'; render(); break;
    case 'goto-home': state.tab = 'home'; render(); break;
    case 'pick': fileInput.click(); break;

    case 'open': openItem(id); break;
    case 'view-grid': state.view = 'grid'; render(); break;
    case 'view-list': state.view = 'list'; render(); break;
    case 'select-all': state.included = new Set(state.files.map((f) => f.id)); render(); break;
    case 'clear-all': state.included = new Set(); render(); break;

    case 'share-primary': {
      const primaryIsContents = state.settings.defaultBehavior === 'contents';
      if (primaryIsContents && hasIncludedZip()) collectShareContents().then(openShare);
      else openShare(collectShareSelected());
      break;
    }
    case 'share-secondary': {
      const primaryIsContents = state.settings.defaultBehavior === 'contents';
      if (primaryIsContents) openShare(collectShareSelected());
      else collectShareContents().then(openShare);
      break;
    }
    case 'share-selected': openShare(collectShareSelected()); break;
    case 'share-original-zip': openShare(collectShareOriginalZips()); break;

    case 'zip-open': openZip(id); break;
    case 'zip-retry': { const f = state.files.find((x) => x.id === id); if (f) extractZip(f).then(() => render()); break; }
    case 'zip-select-all': { const z = state.zips.get(id); if (z) { z.included = new Set(z.media.map((m) => m.id)); render(); } break; }
    case 'zip-clear-all': { const z = state.zips.get(id); if (z) { z.included = new Set(); render(); } break; }
    case 'zip-toggle': toggleZipItem(zipId, id); break;
    case 'zip-share-contents': openShare(collectShareZipMedia(id)); break;
    case 'zip-share-original': { const f = state.files.find((x) => x.id === id); if (f) openShare([f]); break; }

    case 'overlay-close': state.overlay = null; render(); break;
    case 'img-prev': stepImage(-1); break;
    case 'img-next': stepImage(1); break;
    case 'img-zoom-in': zoomImage(1.25); break;
    case 'img-zoom-out': zoomImage(1 / 1.25); break;
    case 'img-reset': viewer.scale = 1; viewer.tx = 0; viewer.ty = 0; applyZoom(); break;

    case 'share-cancel': state.overlay = null; render(); break;
    case 'share-confirm': confirmShare(); break;

    case 'setting-behavior': state.settings.defaultBehavior = el.dataset.value; saveSettings(); render(); break;
    case 'setting-theme': state.settings.theme = el.dataset.value; saveSettings(); render(); break;
    case 'expand': { const t = document.getElementById('x-' + el.dataset.target); if (t) t.hidden = !t.hidden; break; }
    case 'clear-temp': {
      for (const z of state.zips.values()) z.blobs.forEach((u) => URL.revokeObjectURL(u));
      state.zips.clear();
      render(); showToast('Temporary files cleared');
      break;
    }
    case 'clear-history': state.history = []; saveHistory(); render(); break;

    default: break;
  }
});

document.addEventListener('change', (e) => {
  if (e.target.matches('.tile-check')) {
    const id = e.target.dataset.id;
    const zip = e.target.dataset.zip;
    if (zip) toggleZipItem(zip, id);
    else {
      if (state.included.has(id)) state.included.delete(id); else state.included.add(id);
      render();
    }
  } else if (e.target.matches('.switch input')) {
    const key = e.target.dataset.key;
    state.settings[key] = e.target.checked;
    saveSettings();
    if (key === 'theme') { /* handled via radios */ }
    render();
  }
});

document.addEventListener('keydown', (e) => {
  if (!state.overlay) return;
  if (e.key === 'Escape') { state.overlay = null; render(); }
  else if (state.overlay.type === 'image') {
    if (e.key === 'ArrowLeft') stepImage(-1);
    else if (e.key === 'ArrowRight') stepImage(1);
  }
});

function openItem(id) {
  const f = state.files.find((x) => x.id === id);
  if (!f) return;
  if (f.kind === 'image') { state.overlay = { type: 'image', id }; render(); }
  else if (f.kind === 'video') { state.overlay = { type: 'video', id }; render(); }
  else if (f.kind === 'zip') openZip(id);
}

async function openZip(id) {
  const f = state.files.find((x) => x.id === id);
  if (!f) return;
  state.overlay = { type: 'zip', id };
  render();
  if (!state.zips.has(id) || state.zips.get(id).status !== 'ready') {
    await extractZip(f);
    state.overlay = { type: 'zip', id };
    render();
  }
}

function toggleZipItem(zipId, itemId) {
  const z = state.zips.get(zipId);
  if (!z) return;
  if (z.included.has(itemId)) z.included.delete(itemId); else z.included.add(itemId);
  render();
}

function stepImage(dir) {
  const list = viewer.list;
  if (!list.length) return;
  viewer.index = (viewer.index + dir + list.length) % list.length;
  viewer.scale = 1; viewer.tx = 0; viewer.ty = 0;
  state.overlay = { type: 'image', id: list[viewer.index].id };
  render();
}

function zoomImage(factor) {
  viewer.scale = Math.min(6, Math.max(1, viewer.scale * factor));
  if (viewer.scale <= 1) { viewer.tx = 0; viewer.ty = 0; }
  applyZoom();
}

function applyZoom() {
  const img = $('#viewer-img');
  if (img) img.style.transform = `translate(${viewer.tx}px, ${viewer.ty}px) scale(${viewer.scale})`;
}

// Wheel zoom + drag pan on the image viewer.
document.addEventListener('wheel', (e) => {
  if (!state.overlay || state.overlay.type !== 'image') return;
  if (!e.target.closest('#viewer-canvas')) return;
  e.preventDefault();
  zoomImage(e.deltaY < 0 ? 1.15 : 1 / 1.15);
}, { passive: false });

(function initDragPan() {
  let dragging = false, sx = 0, sy = 0, ox = 0, oy = 0;
  document.addEventListener('pointerdown', (e) => {
    if (!state.overlay || state.overlay.type !== 'image') return;
    if (!e.target.closest('#viewer-canvas') || viewer.scale <= 1) return;
    dragging = true; sx = e.clientX; sy = e.clientY; ox = viewer.tx; oy = viewer.ty;
  });
  document.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    viewer.tx = ox + (e.clientX - sx);
    viewer.ty = oy + (e.clientY - sy);
    applyZoom();
  });
  document.addEventListener('pointerup', () => { dragging = false; });
  document.addEventListener('pointercancel', () => { dragging = false; });
})();

/* --------------------------------- boot ---------------------------------- */

render();
