#!/usr/bin/env node
/**
 * XAND MUSIC - companion for the existing Moto G82 Chrome editor.
 * No external dependencies, credentials, editor changes, or social publishing.
 * Requires Node 24+, ADB authorized on the Android device, and an authenticated
 * editor tab in Android Chrome. All media transfer is memory-only on the PC.
 *
 * Commands: inspect | prepare | upload | render | download
 * Options: --adb PATH --serial HOST:PORT --url http://192.168.0.6:1890/
 *          --source /sdcard/path/file.mp4 --force-render
 */
import { execFileSync } from 'node:child_process';
import { basename, posix } from 'node:path';

const options = {};
let command = 'inspect';
for (let i = 2; i < process.argv.length; i++) {
  const a = process.argv[i];
  if (!a.startsWith('--') && command === 'inspect') { command = a; continue; }
  if (!a.startsWith('--')) throw Error('Argumento inesperado: ' + a);
  if (a === '--force-render') { options.forceRender = true; continue; }
  const value = process.argv[++i];
  if (!value || value.startsWith('--')) throw Error('Falta valor para ' + a);
  options[a.slice(2)] = value;
}
if (!['inspect', 'prepare', 'upload', 'render', 'download'].includes(command)) throw Error('Comando inválido: ' + command);

const adb = options.adb || process.env.XAND_ADB_PATH || 'adb';
const serial = options.serial || process.env.XAND_ADB_SERIAL;
const editorUrl = new URL(options.url || 'http://192.168.0.6:1890/');
if (editorUrl.protocol !== 'http:' || editorUrl.hostname !== '192.168.0.6' || editorUrl.port !== '1890') {
  throw Error('Editor fora do endereço local autorizado. Não conectar externamente.');
}
if (!serial) throw Error('Informe --serial ou XAND_ADB_SERIAL.');
const ad = (...args) => execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 25000, maxBuffer: 5 * 1024 * 1024 });
const pause = ms => new Promise(r => setTimeout(r, ms));

function decodeXml(value) {
  return value.replace(/&#(x[0-9a-f]+|\d+);/gi, (_m, c) => String.fromCodePoint(c[0] === 'x' ? parseInt(c.slice(1), 16) : Number(c)))
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
}
function preference(xml, name) {
  const pattern = new RegExp('<string name="' + name + '">([\\s\\S]*?)<\\/string>');
  const match = xml.match(pattern);
  return match ? decodeXml(match[1]) : '';
}
function taskFromQueue() {
  const xml = ad('shell', 'run-as', 'music.xand.share', 'cat', 'shared_prefs/xand_queue.xml');
  const queue = JSON.parse(preference(xml, 'queue') || '[]');
  const metadata = JSON.parse(preference(xml, 'metadata') || '{}');
  const url = queue[0];
  const item = url ? (metadata[url] || {}) : {};
  return { queueLength: queue.length, url, item };
}
function mediaInfo(uri) {
  if (!/^content:\/\/media\/external\/video\/media\/\d+$/.test(uri || '')) throw Error('URI de vídeo local inválida.');
  const row = ad('shell', 'content', 'query', '--uri', uri, '--projection', '_display_name:relative_path:_size:duration:width:height');
  const file = row.match(/_display_name=([^,\r\n]+)/)?.[1];
  const dir = row.match(/relative_path=([^,\r\n]+)/)?.[1];
  if (!file || !dir || /[\\/]/.test(file) || dir.includes('..')) throw Error('Não foi possível localizar o vídeo no MediaStore.');
  return {
    path: posix.join('/sdcard', dir, file), name: file,
    size: Number(row.match(/_size=(\d+)/)?.[1] || 0),
    duration: Number(row.match(/duration=(\d+)/)?.[1] || 0),
    width: Number(row.match(/width=(\d+)/)?.[1] || 0),
    height: Number(row.match(/height=(\d+)/)?.[1] || 0),
  };
}

class Chrome {
  constructor(socket) {
    this.socket = socket;
    this.id = 0;
    this.pending = new Map();
    socket.addEventListener('message', event => {
      let msg;
      try { msg = JSON.parse(event.data); } catch { return; }
      const waiter = this.pending.get(msg.id);
      if (waiter) { this.pending.delete(msg.id); waiter(msg); }
    });
  }
  static async open() {
    const r = await fetch('http://127.0.0.1:9222/json');
    if (!r.ok) throw Error('DevTools local não respondeu.');
    const tabs = await r.json();
    const tab = tabs.find(t => t.type === 'page' && t.url?.startsWith(editorUrl.origin + '/'));
    if (!tab) throw Error('Nenhuma guia do editor encontrada no Chrome do Motorola.');
    const socket = new WebSocket(tab.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true });
      socket.addEventListener('error', () => reject(Error('WebSocket do Chrome indisponível.')), { once: true });
    });
    return new Chrome(socket);
  }
  async send(method, params = {}, timeout = 30000) {
    const id = ++this.id;
    const response = new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(Error('Tempo esgotado no Chrome: ' + method)); }, timeout);
      this.pending.set(id, msg => { clearTimeout(timer); msg.error ? reject(Error(msg.error.message)) : resolve(msg.result); });
    });
    this.socket.send(JSON.stringify({ id, method, params }));
    return response;
  }
  async eval(expression, { awaitPromise = false, userGesture = false, timeout = 30000 } = {}) {
    const r = await this.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise, userGesture }, timeout);
    if (r.exceptionDetails) throw Error('Erro na guia do editor: ' + (r.exceptionDetails.text || r.result?.description));
    return r.result?.value;
  }
  close() { this.socket.close(); }
}

const jsonExpr = value => JSON.stringify(value);
async function state(chrome) {
  return chrome.eval(`(() => {
    const el = id => document.getElementById(id);
    return {
      authenticated: el('editor') && !el('editor').hidden,
      selected: el('existingFiles')?.value,
      files: Array.from(el('existingFiles')?.options || []).map(o => ({value:o.value, text:o.text})),
      processingJob: localStorage.getItem('xandJob'),
      status: el('jobMessage')?.textContent?.trim(),
      videoReady: !!el('finalVideo') && !el('finalVideo').hidden,
      download: el('downloadLink')?.getAttribute('href'),
      duration: el('duration')?.value,
      framing: el('framing')?.value,
      preset: el('preset')?.value
    };
  })()`);
}
function selectedFile(s, filename) {
  const matching = s.files.filter(f => f.value && (f.value === filename || f.value.endsWith('_' + filename)));
  return matching.at(-1)?.value || null;
}
async function prepare(chrome, target, video, { allowMissing = false } = {}) {
  const s = await state(chrome);
  if (!s.authenticated) throw Error('O editor exige uma sessão autorizada no Chrome.');
  const selected = selectedFile(s, video.name);
  if (!selected) {
    if (allowMissing) return { ready: false, reason: 'source_not_uploaded', filename: video.name };
    throw Error('Arquivo de origem não enviado ao editor: ' + video.name);
  }
  const duration = Math.max(60, Math.min(180, Number(target.item.duration) || 60));
  const framing = video.width > video.height ? 'fit' : 'fill';
  const fields = { existingFiles: selected, title: '', subtitle: '', start: '0', duration: String(duration), composition: 'normal', preset: 'vertical', framing, zoom: '1', fade: '0.5' };
  const result = await chrome.eval(`(() => {
    const fields = ${jsonExpr(fields)};
    for (const [id, value] of Object.entries(fields)) {
      const el = document.getElementById(id);
      if (!el) throw Error('Campo não encontrado: '+id);
      el.value = value;
      el.dispatchEvent(new Event('input', {bubbles:true}));
      el.dispatchEvent(new Event('change', {bubbles:true}));
      if (el.value !== value) throw Error('Campo não aplicado: '+id);
    }
    return Object.fromEntries(Object.keys(fields).map(id => [id,document.getElementById(id).value]));
  })()`);
  return { ready: true, fields: result, logoCheck: 'manual_required', sourceBranding: !!target.item.editor_source_has_branding };
}

async function upload(chrome, video) {
  const s = await state(chrome);
  if (selectedFile(s, video.name)) return { alreadyUploaded: true };
  if (!s.authenticated) throw Error('Sessão não autorizada.');
  if (video.size <= 0 || video.size > 250 * 1024 * 1024) throw Error('Tamanho do vídeo inválido para o editor.');
  // Read only from Android to RAM; no screenshots, temporary video files or secrets on the PC.
  const raw = execFileSync(adb, ['-s', serial, 'exec-out', 'cat', video.path], { maxBuffer: 251 * 1024 * 1024, timeout: 120000 });
  if (raw.length !== video.size) throw Error('Transferência ADB incompleta; nenhum upload iniciado.');
  await chrome.eval(`(() => {
    if (window.__xandBridge?.phase === 'uploading') throw Error('Já existe um envio em andamento.');
    window.__xandBridge = { phase:'receiving', chunks:[], size:0 };
    return true;
  })()`);
  try {
    for (let n = 0; n < raw.length; n += 192 * 1024) {
      const base64 = raw.subarray(n, n + 192 * 1024).toString('base64');
      await chrome.eval(`(() => {
        const data=atob('${base64}'), bytes=new Uint8Array(data.length);
        for(let i=0;i<data.length;i++) bytes[i]=data.charCodeAt(i);
        window.__xandBridge.chunks.push(bytes);
        window.__xandBridge.size+=bytes.length;
        return window.__xandBridge.size;
      })()`, { timeout: 45000 });
    }
    const started = await chrome.eval(`(() => {
      const b=window.__xandBridge;
      if (!b || b.size !== ${raw.length}) throw Error('Buffer incompleto.');
      b.phase='uploading';
      const blob=new Blob(b.chunks,{type:'video/mp4'});
      b.chunks=[];
      b.promise=fetch('/api/upload?name='+encodeURIComponent(${jsonExpr(video.name)}), {
        method:'POST', credentials:'same-origin', headers:{'Content-Type':'application/octet-stream'},body:blob
      }).then(async r => {
        const data=await r.json().catch(()=>({}));
        if(r.status!==201 || !data.name) throw Error(data.error||'Upload HTTP '+r.status);
        b.phase='done';b.name=data.name;return data.name;
      }).catch(e => {b.phase='error';b.error=String(e);return null;});
      return {started:true};
    })()`);
    if (!started?.started) throw Error('Upload não iniciado.');
    for (let attempt = 0; attempt < 36; attempt++) {
      await pause(5000);
      const info = await chrome.eval('(() => {const b=window.__xandBridge;return b?{phase:b.phase,name:b.name,error:b.error}:null})()');
      if (info?.phase === 'error') throw Error('Falha no upload: ' + info.error);
      if (info?.phase === 'done') {
        await chrome.eval("document.getElementById('refreshButton').click();true");
        for (let i = 0; i < 12; i++) {
          if (selectedFile(await state(chrome), video.name)) return { uploaded: true, name: info.name };
          await pause(1000);
        }
        throw Error('Upload concluído mas a lista do editor não atualizou.');
      }
    }
    throw Error('Envio ainda pendente no navegador; não repetir sem conferir o estado.');
  } finally {
    raw.fill(0);
    // Deliberately leave a pending browser upload running on timeout; never retry blindly.
  }
}

async function render(chrome, forceRender) {
  const before = await state(chrome);
  if (before.processingJob) return { started: false, reason: 'already_processing', job: before.processingJob };
  if (before.videoReady && !forceRender) return { started: false, reason: 'previous_result_exists', download: before.download };
  const result = await chrome.eval(`(() => {
    const form=document.getElementById('renderForm');
    if(!form || !document.getElementById('existingFiles').value) throw Error('Fonte não selecionada.');
    form.requestSubmit();
    return {submitted:true};
  })()`, { userGesture: true });
  await pause(1500);
  const after = await state(chrome);
  return { ...result, job: after.processingJob, status: after.status };
}

async function download(chrome) {
  const s = await state(chrome);
  if (!s.videoReady || !s.download?.startsWith('/api/download/')) throw Error('Nenhum vídeo concluído para baixar.');
  const name = decodeURIComponent(s.download.split('/').at(-1));
  if (!/^XAND_MOBILE_[A-Za-z0-9_.-]+\.mp4$/.test(name)) throw Error('Nome de download inesperado.');
  try { ad('shell', 'test', '-s', '/sdcard/Download/' + name); return { alreadyDownloaded: true, name }; } catch { /* still absent */ }
  const clicked = await chrome.eval(`(() => {
    const a=document.getElementById('downloadLink');
    if(!a || a.getAttribute('href')!==${jsonExpr(s.download)}) return false;
    a.click();return true;
  })()`, { userGesture: true });
  if (!clicked) throw Error('Link de download mudou.');
  for (let attempt = 0; attempt < 25; attempt++) {
    await pause(1200);
    try { ad('shell', 'test', '-s', '/sdcard/Download/' + name); return { downloaded: true, name }; }
    catch { /* pending */ }
  }
  return { downloaded: false, reason: 'download_pending', name };
}

async function main() {
  ad('get-state');
  // ADB-only loopback forwarding; not an internet endpoint.
  ad('forward', 'tcp:9222', 'localabstract:chrome_devtools_remote');
  const chrome = await Chrome.open();
  try {
    const target = taskFromQueue();
    const video = options.source
      ? { path: options.source, name: basename(options.source), size: -1, width: 0, height: 0 }
      : target.item.video_uri ? mediaInfo(target.item.video_uri) : null;
    if (options.source && command === 'upload') throw Error('Para upload, use a fonte verificada do MediaStore da fila.');
    if (command === 'inspect') {
      const s = await state(chrome);
      console.log(JSON.stringify({ command, queueLength: target.queueLength, link: target.url,
        music: target.item.song, artist: target.item.artist,
        source: video && {name:video.name, size:video.size, duration:video.duration, width:video.width, height:video.height},
        editor: {authenticated:s.authenticated, selected:s.selected, processingJob:s.processingJob,
          status:s.status, videoReady:s.videoReady, download:s.download},
        pending: !video ? 'video_not_ready_in_automation' : !selectedFile(s, video.name) ? 'upload_source' : 'review_logo_position' }, null, 2));
      return;
    }
    if (command === 'download') { console.log(JSON.stringify(await download(chrome), null, 2)); return; }
    if (!target.url || !video) throw Error('Primeira música da fila ainda não possui vídeo local.');
    if (command === 'upload') { console.log(JSON.stringify(await upload(chrome, video), null, 2)); return; }
    if (command === 'prepare') { console.log(JSON.stringify(await prepare(chrome, target, video, {allowMissing:true}), null, 2)); return; }
    if (command === 'render') {
      const s = await state(chrome);
      if (!selectedFile(s, video.name)) throw Error('Execute upload antes do render.');
      const ready = await prepare(chrome, target, video);
      // Position of an embedded logo cannot be proven via DOM: report review as pending.
      const submitted = await render(chrome, options.forceRender);
      console.log(JSON.stringify({ ...ready, render:submitted, publication:'not_attempted' }, null, 2));
    }
  } finally { chrome.close(); }
}

main().catch(err => { console.error(JSON.stringify({ error: err.message, publication: 'not_attempted' })); process.exitCode = 1; });
