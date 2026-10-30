/* XAND Music Radar — MVP local, sem APIs pagas e sem gravação.
   Para analisar som remoto: compartilhamento explícito de aba com áudio (getDisplayMedia).
   Player do TikTok e Display API não oferecem PCM para análise. */
(function () {
  'use strict';
  const KEY = 'xand-music-radar-v1';
  const $ = (id) => document.getElementById(id);
  const STATUS = {new:'Inédita',candidate:'Em avaliação',used:'Já publicada',discarded:'Descartada'};
  let tracks = readStored();
  let selectedId = null;
  let mediaStream = null;
  let ctx = null;
  let analyzer = null;
  let audioSource = null;
  let animation = 0;
  let spectrumBars = [];
  let captureStarted = 0;
  let lastMeasure = 0;
  let previousBass = 0;
  let recentBass = [];
  let peaks = [];
  let captured = {frames:0, bassSum:0, sec:0, bpm:null, bassPct:null, db:null};

  function readStored() {
    try {
      const value = JSON.parse(localStorage.getItem(KEY) || '[]');
      return Array.isArray(value) ? value.filter((v) => v && typeof v === 'object').slice(0,5000) : [];
    } catch { return []; }
  }
  function persist() {
    try { localStorage.setItem(KEY, JSON.stringify(tracks)); return true; }
    catch { setMessage('library-msg','Não foi possível salvar. Verifique o espaço ou a privacidade do navegador.',true); return false; }
  }
  function escapeText(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g,(c)=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }
  function cleanUrl(raw) {
    if (!raw) return '';
    try {
      const url = new URL(String(raw).trim());
      if (url.protocol !== 'https:' || !/(^|\.)tiktok\.com$/i.test(url.hostname)) return '';
      return url.href;
    } catch { return ''; }
  }
  function makeId() {
    return String(Date.now()) + '-' + Math.random().toString(36).slice(2,10);
  }
  function getInt(value) {
    if (value === '' || value === null || value === undefined) return null;
    const number = Number(String(value).trim().replace(/[\s.,]/g,''));
    return Number.isFinite(number) && number >= 0 ? Math.round(number) : null;
  }
  function setMessage(id,text,error) {
    const el=$(id); el.textContent=text; el.classList.toggle('error',Boolean(error));
  }
  function text(id,value) { $(id).textContent=String(value); }
  function showNumber(value) { return new Intl.NumberFormat('pt-BR').format(value); }
  function selected() { return tracks.find(t=>t.id===selectedId) || null; }

  function getForm() {
    const urlRaw=$('track-url').value.trim(), url=cleanUrl(urlRaw);
    if (urlRaw && !url) {
      setMessage('track-status-msg','Use um link HTTPS do TikTok.',true);
      return null;
    }
    const title=$('track-title').value.trim();
    if (!title) {setMessage('track-status-msg','Informe o nome da música ou do som.',true);return null;}
    return {
      title:title.slice(0,180),
      artist:$('track-artist').value.trim().slice(0,180),
      url:url,
      genre:$('track-genre').value,
      vocal:$('track-vocal').value,
      status:$('track-status').value,
      views:getInt($('track-views').value),
      notes:$('track-notes').value.trim().slice(0,1000)
    };
  }
  function fillForm(record) {
    $('track-title').value=record ? record.title||'' : '';
    $('track-artist').value=record ? record.artist||'' : '';
    $('track-url').value=record ? record.url||'' : '';
    $('track-genre').value=record ? record.genre||'' : '';
    $('track-vocal').value=record ? record.vocal||'unknown' : 'unknown';
    $('track-status').value=record ? record.status||'new' : 'new';
    $('track-views').value=record && record.views!==null && record.views!==undefined ? record.views : '';
    $('track-notes').value=record ? record.notes||'' : '';
    $('open-track').disabled=!(record && cleanUrl(record.url));
    $('save-capture').disabled=!(record && captured.sec>=8);
  }
  function resetForm() {
    selectedId=null;fillForm(null);
    $('add-track').textContent='Salvar música';
    setMessage('track-status-msg','Nova música. Cole o link do TikTok e preencha o título.');
    render();
  }
  function chooseRecord(id) {
    const record=tracks.find(t=>t.id===id);
    if (!record) return;
    selectedId=id;fillForm(record);
    $('add-track').textContent='Atualizar música';
    setMessage('track-status-msg','Selecionada: '+record.title+'. Agora você pode salvar a análise nela.');
    render();
    document.getElementById('laboratorio').scrollIntoView({behavior:'smooth',block:'start'});
  }
  function saveTrack() {
    const data=getForm();
    if (!data) return;
    let duplicate=tracks.find(t => t.id!==selectedId &&
      ((data.url && t.url===data.url) ||
       (t.title||'').toLocaleLowerCase('pt-BR')===data.title.toLocaleLowerCase('pt-BR') &&
       (t.artist||'').toLocaleLowerCase('pt-BR')===data.artist.toLocaleLowerCase('pt-BR')));
    if (duplicate) {
      setMessage('track-status-msg','Esta música já está cadastrada. Selecione-a na biblioteca para editar.',true);
      return;
    }
    if (selectedId) {
      const item=selected(); if(!item) return;
      Object.assign(item,data,{updatedAt:new Date().toISOString()});
    } else {
      const item={id:makeId(),...data,createdAt:new Date().toISOString(),analysis:null};
      tracks.unshift(item); selectedId=item.id;
    }
    if(persist()) {
      $('add-track').textContent='Atualizar música';
      $('open-track').disabled=!data.url;
      setMessage('track-status-msg','Música salva na biblioteca local.');
      render();
    }
  }
  function render() {
    text('stat-total',tracks.length);
    text('stat-new',tracks.filter(t=>t.status==='new').length);
    text('stat-analyzed',tracks.filter(t=>t.analysis && Number.isFinite(t.analysis.bassPct)).length);
    const search=$('library-search').value.trim().toLocaleLowerCase('pt-BR');
    const filter=$('library-filter').value;
    const filtered=tracks.filter(t=>{
      const matches=(String(t.title||'')+' '+String(t.artist||'')+' '+String(t.genre||'')).toLocaleLowerCase('pt-BR').includes(search);
      return matches && (filter==='all' || (filter==='analyzed' ? Boolean(t.analysis) : t.status===filter));
    });
    text('library-count',showNumber(filtered.length)+' de '+showNumber(tracks.length)+' música(s) — salvas somente neste navegador');
    if(!filtered.length) {
      $('library-list').innerHTML='<div class="empty-state">Nenhuma música neste filtro. Pesquise no TikTok e salve o link com o nome do áudio.</div>';
    } else {
      $('library-list').innerHTML=filtered.map(t=>{
        const markers=[
          '<span class="tag '+(t.status==='new'?'positive':t.status==='used'?'warning':'')+'">'+escapeText(STATUS[t.status]||'Não classificado')+'</span>',
          t.genre?'<span class="tag">'+escapeText(t.genre)+'</span>':'',
          t.vocal==='yes'?'<span class="tag positive">Com vocal</span>':t.vocal==='no'?'<span class="tag">Sem vocal</span>':'',
          t.analysis?'<span class="tag positive">BPM '+(t.analysis.bpm===null?'indeterminado':escapeText(t.analysis.bpm))+' · graves '+escapeText(t.analysis.bassPct)+'%</span>':'',
          t.views!==null && t.views!==undefined?'<span class="tag">'+showNumber(t.views)+' views</span>':''
        ].join('');
        const id=escapeText(t.id);
        return '<article class="library-entry"><div><h4>'+escapeText(t.title||'Sem título')+'</h4><p>'+escapeText(t.artist||'Artista não informado')+'</p><div class="entry-tags">'+markers+'</div></div><div class="entry-actions"><button class="mini-btn" data-action="select" data-id="'+id+'">Selecionar</button>'+(cleanUrl(t.url)?'<button class="mini-btn" data-action="open" data-id="'+id+'">TikTok ↗</button>':'')+'<button class="mini-btn danger" data-action="delete" data-id="'+id+'">Excluir</button></div></article>';
      }).join('');
    }
    const counted=tracks.filter(t=>t.views!==null && t.views!==undefined && Number.isFinite(Number(t.views)));
    text('under-1k',counted.filter(t=>Number(t.views)<1000).length);
    text('over-1k',counted.filter(t=>Number(t.views)>=1000).length);
    const byGenre=new Map();
    counted.forEach(t=>{const genre=t.genre||'Não classificado';byGenre.set(genre,(byGenre.get(genre)||0)+1);});
    $('genre-breakdown').innerHTML=byGenre.size ? Array.from(byGenre).sort((a,b)=>b[1]-a[1]).map(([name,n])=>'<div class="genre-row"><span>'+escapeText(name)+'</span><strong>'+showNumber(n)+'</strong></div>').join('') : 'Nenhuma visualização cadastrada.';
    $('save-capture').disabled=!(selected() && captured.sec>=8);
  }
  function onLibraryClick(event) {
    const button=event.target.closest('button[data-action]'); if(!button)return;
    const item=tracks.find(t=>t.id===button.dataset.id);if(!item)return;
    if(button.dataset.action==='select') chooseRecord(item.id);
    if(button.dataset.action==='open' && cleanUrl(item.url)) window.open(cleanUrl(item.url),'_blank','noopener,noreferrer');
    if(button.dataset.action==='delete' && confirm('Excluir "'+item.title+'" da biblioteca local?')) {
      tracks=tracks.filter(t=>t.id!==item.id);
      if(selectedId===item.id) resetForm();
      persist();render();setMessage('library-msg','Registro removido. O vídeo do TikTok não foi apagado.');
    }
  }
  function explore() {
    const terms=[$('search-style').value,$('search-vocal').value,$('search-region').value,$('search-extra').value.trim().slice(0,80)].filter(Boolean).join(' ');
    window.open('https://www.tiktok.com/search?q='+encodeURIComponent(terms),'_blank','noopener,noreferrer');
  }
  function downloadFile(name,textValue,mime) {
    const blob=new Blob([textValue],{type:mime});
    const url=URL.createObjectURL(blob);
    const a=document.createElement('a');a.href=url;a.download=name;a.click();
    setTimeout(()=>URL.revokeObjectURL(url),3000);
  }
  function exportJson() {
    downloadFile('xand-music-radar.json',JSON.stringify(tracks,null,2),'application/json');
  }
  function csvCell(value) {
    return '"'+String(value===null || value===undefined?'':value).replace(/"/g,'""')+'"';
  }
  function exportCsv() {
    const fields=['title','artist','url','genre','vocal','status','views','notes','bpm','bassPct'];
    const rows=[fields.join(';')];
    for(const t of tracks) rows.push(fields.map(k=>csvCell(k==='bpm'||k==='bassPct'?(t.analysis?t.analysis[k]:''):t[k])).join(';'));
    downloadFile('xand-music-radar.csv','\ufeff'+rows.join('\r\n'),'text/csv;charset=utf-8');
  }
  function parseCsv(source) {
    const s=source.replace(/^\ufeff/,'');
    const top=(s.split(/\r?\n/,1)[0]||'');
    const delimiter=(top.match(/;/g)||[]).length>(top.match(/,/g)||[]).length?';':',';
    const rows=[];let row=[],cell='',quoted=false;
    for(let i=0;i<s.length;i++){
      const ch=s[i];
      if(quoted){
        if(ch==='"'&&s[i+1]==='"'){cell+='"';i++;}
        else if(ch==='"')quoted=false;else cell+=ch;
      }else if(ch==='"')quoted=true;
      else if(ch===delimiter){row.push(cell);cell='';}
      else if(ch==='\n'){row.push(cell.replace(/\r$/,''));if(row.some(v=>v.trim()))rows.push(row);row=[];cell='';}
      else cell+=ch;
    }
    row.push(cell.replace(/\r$/,''));if(row.some(v=>v.trim()))rows.push(row);
    if(rows.length<2)return [];
    const headers=rows.shift().map(v=>v.toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim());
    const aliases={
      title:['title','titulo','musica','music','som','track','song'],
      artist:['artist','artista','criador','author'],
      url:['url','link','video','video_url','tiktok'],
      genre:['genre','genero','estilo'],
      vocal:['vocal','voz'],
      status:['status','situacao','estado'],
      views:['views','visualizacoes','visualizacoes totais','visualizacoes de video'],
      notes:['notes','observacoes','descricao','description']
    };
    return rows.map(values=>{
      const record={};
      for(const [name,possible] of Object.entries(aliases)){
        const idx=headers.findIndex(h=>possible.includes(h));record[name]=idx>=0?values[idx]:'';
      }
      return record;
    });
  }
  function normalizeImport(input) {
    if(!input || typeof input!=='object')return null;
    const title=String(input.title||'').trim().slice(0,180);
    if(!title)return null;
    const vocal=String(input.vocal||'').toLowerCase();
    const status=String(input.status||'').toLowerCase();
    return {
      id:makeId(),title,artist:String(input.artist||'').slice(0,180),
      url:cleanUrl(input.url),genre:String(input.genre||'').slice(0,80),
      vocal:['yes','sim','com vocal','vocal'].includes(vocal)?'yes':['no','nao','não','sem vocal'].includes(vocal)?'no':'unknown',
      status:Object.keys(STATUS).includes(status)?status:(status.includes('publicad')||status.includes('usad'))?'used':'new',
      views:getInt(input.views),
      notes:String(input.notes||'').slice(0,1000),
      createdAt:new Date().toISOString(),
      analysis:input.analysis && typeof input.analysis==='object' && Number.isFinite(Number(input.analysis.bassPct)) ? {
        bpm:Number.isFinite(Number(input.analysis.bpm))?Number(input.analysis.bpm):null,
        bassPct:Number(input.analysis.bassPct),db:Number(input.analysis.db)||null,
        seconds:Number(input.analysis.seconds)||0
      } : null
    };
  }
  async function importFile(event) {
    const file=event.target.files[0]; if(!file)return;
    if(file.size>2*1024*1024){setMessage('library-msg','O arquivo excede 2 MB.',true);return;}
    try{
      const content=await file.text();
      const objects=file.name.toLowerCase().endsWith('.json')?JSON.parse(content):parseCsv(content);
      if(!Array.isArray(objects))throw new Error('O conteúdo deve ser uma lista de registros.');
      let count=0;
      for(const item of objects.slice(0,5000)){
        const normalized=normalizeImport(item);if(!normalized)continue;
        const exists=tracks.some(t=>(normalized.url && t.url===normalized.url) ||
            ((t.title||'').toLocaleLowerCase('pt-BR')===normalized.title.toLocaleLowerCase('pt-BR') &&
             (t.artist||'').toLocaleLowerCase('pt-BR')===normalized.artist.toLocaleLowerCase('pt-BR')));
        if(exists)continue;
        tracks.push(normalized);count++;
      }
      persist();render();
      setMessage('library-msg',count+' música(s) importada(s). Duplicatas não foram adicionadas.');
    }catch(err){setMessage('library-msg','Erro na importação: '+String(err.message||err),true);}
    finally{event.target.value='';}
  }

  function initBars(){
    for(let i=0;i<42;i++){
      const el=document.createElement('span');el.className='spectrum-bar';el.style.height='2%';
      $('spectrum-bars').appendChild(el);spectrumBars.push(el);
    }
  }
  function resetCaptured() {
    captured={frames:0,bassSum:0,sec:0,bpm:null,bassPct:null,db:null};
    recentBass=[];peaks=[];lastMeasure=0;previousBass=0;
    text('live-bpm','—');text('live-bass','—');text('live-level','—');text('live-time','0s');
    $('save-capture').disabled=true;
  }
  function estimateBpm() {
    const intervals=[];
    for(let i=1;i<peaks.length;i++){
      const sec=(peaks[i]-peaks[i-1])/1000;
      if(sec>=.28 && sec<=1.4) intervals.push(sec);
    }
    if(intervals.length<5)return null;
    const bpms=intervals.map(sec=>{
      let bpm=60/sec;
      while(bpm<75)bpm*=2;
      while(bpm>175)bpm/=2;
      return bpm;
    }).sort((a,b)=>a-b);
    const median=bpms[Math.floor(bpms.length/2)];
    const agreed=bpms.filter(b=>Math.abs(b-median)<median*.11);
    if(agreed.length<Math.max(5,Math.ceil(bpms.length*.6)))return null;
    return Math.round(agreed.reduce((sum,n)=>sum+n,0)/agreed.length);
  }
  function tick(time) {
    if(!analyzer||!ctx)return;
    const byte=new Uint8Array(analyzer.frequencyBinCount);
    const waveform=new Float32Array(analyzer.fftSize);
    analyzer.getByteFrequencyData(byte);analyzer.getFloatTimeDomainData(waveform);
    const hz=ctx.sampleRate/analyzer.fftSize;
    let bass=0,total=0;
    const min=Math.ceil(40/hz),max=Math.min(byte.length-1,Math.floor(8000/hz));
    for(let i=min;i<=max;i++){
      const amplitude=byte[i]/255;
      const power=amplitude*amplitude;
      total+=power;
      if(i*hz<=180)bass+=power;
    }
    let rms=0;
    for(let i=0;i<waveform.length;i++)rms+=waveform[i]*waveform[i];
    rms=Math.sqrt(rms/waveform.length);
    const db=rms>0?20*Math.log10(rms):-100;
    const percentage=total>.005?Math.min(100,100*bass/total):0;
    const bins=byte.length;
    for(let j=0;j<spectrumBars.length;j++){
      const f=80*Math.pow(8000/80,j/(spectrumBars.length-1));
      const bin=Math.max(0,Math.min(bins-1,Math.round(f/hz)));
      const height=Math.max(2,Math.min(100,Math.pow(byte[bin]/255,1.3)*100));
      spectrumBars[j].style.height=height.toFixed(1)+'%';
    }
    if(time-lastMeasure>85){
      lastMeasure=time;
      const average=recentBass.length?recentBass.reduce((a,b)=>a+b,0)/recentBass.length:0;
      const rising=bass>previousBass*1.05;
      if(average>.006 && bass>average*1.32 && rising && (!peaks.length||time-peaks[peaks.length-1]>280))peaks.push(time);
      previousBass=bass;
      recentBass.push(bass);if(recentBass.length>28)recentBass.shift();
      peaks=peaks.filter(p=>p>time-25000);
      const elapsed=(time-captureStarted)/1000;
      captured.sec=Math.floor(elapsed);
      if(rms>.003){captured.frames++;captured.bassSum+=percentage;}
      captured.bassPct=captured.frames?Math.round(captured.bassSum/captured.frames):null;
      captured.db=db;
      captured.bpm=estimateBpm();
      text('live-bpm',captured.bpm===null?'—':'≈ '+captured.bpm);
      text('live-bass',rms>.003?Math.round(percentage)+'%':'—');
      text('live-level',db>-80?db.toFixed(0)+' dBFS':'Silêncio');
      text('live-time',Math.max(0,Math.floor(elapsed))+'s');
      $('save-capture').disabled=!(selected() && captured.sec>=8 && captured.frames>20);
    }
    animation=requestAnimationFrame(tick);
  }
  async function startCapture() {
    if(mediaStream)return;
    if(!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getDisplayMedia){
      setMessage('capture-status','Este navegador não oferece compartilhamento de aba com áudio. Use Chrome ou Edge no computador em HTTPS.',true);return;
    }
    $('start-capture').disabled=true;
    resetCaptured();
    try {
      const stream=await navigator.mediaDevices.getDisplayMedia({video:true,audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:false}});
      if(!stream.getAudioTracks().length){
        stream.getTracks().forEach(t=>t.stop());
        throw new Error('Nenhuma faixa de áudio foi compartilhada. Selecione a aba do TikTok e marque "Compartilhar áudio da guia".');
      }
      const AudioContextCtor=window.AudioContext||window.webkitAudioContext;
      if(!AudioContextCtor)throw new Error('Web Audio API não disponível neste navegador.');
      ctx=new AudioContextCtor();
      await ctx.resume();
      mediaStream=stream;
      audioSource=ctx.createMediaStreamSource(new MediaStream(stream.getAudioTracks()));
      analyzer=ctx.createAnalyser();analyzer.fftSize=2048;analyzer.smoothingTimeConstant=.63;
      audioSource.connect(analyzer);
      // Capturar uma aba pode silenciar a reprodução original no Chrome.
      // Reconectar a faixa capturada à saída preserva a audição sem gravar.
      audioSource.connect(ctx.destination);
      stream.getTracks().forEach(track=>{track.addEventListener('ended',()=>stopCapture(true),{once:true});});
      $('stop-capture').disabled=false;
      $('live-dot').classList.add('active');
      text('live-label','ANALISANDO SOM DA ABA');
      text('stat-live','Ao vivo');
      setMessage('capture-status','Análise ativa. Reproduza a música na aba compartilhada. Aguarde pelo menos 8 segundos para salvar as medições.');
      captureStarted=performance.now();
      animation=requestAnimationFrame(tick);
    }catch(err){
      if(mediaStream) stopCapture(false);
      else if(ctx){ctx.close().catch(()=>{});ctx=null;}
      setMessage('capture-status','Não foi possível iniciar: '+String(err.message||err),true);
    }finally{$('start-capture').disabled=Boolean(mediaStream);}
  }
  function stopCapture(external) {
    cancelAnimationFrame(animation);animation=0;
    const former=mediaStream;mediaStream=null;
    if(former)former.getTracks().forEach(t=>{try{t.stop();}catch{}});
    if(audioSource){try{audioSource.disconnect();}catch{}audioSource=null;}
    if(analyzer){try{analyzer.disconnect();}catch{}analyzer=null;}
    if(ctx){ctx.close().catch(()=>{});ctx=null;}
    $('start-capture').disabled=false;$('stop-capture').disabled=true;
    $('live-dot').classList.remove('active');text('live-label','CAPTURA DESLIGADA');text('stat-live','Parada');
    spectrumBars.forEach(bar=>bar.style.height='2%');
    $('save-capture').disabled=!(selected() && captured.sec>=8 && captured.frames>20);
    if(external)setMessage('capture-status','Compartilhamento encerrado. As medidas anteriores continuam disponíveis até a próxima captura.');
    else setMessage('capture-status','Análise encerrada. O áudio não foi gravado.');
  }
  function saveCapture() {
    const record=selected();
    if(!record){setMessage('capture-status','Selecione primeiro uma música da biblioteca.',true);return;}
    if(captured.sec<8 || captured.frames<=20 || captured.bassPct===null){
      setMessage('capture-status','Reproduza pelo menos 8 segundos de áudio audível antes de salvar.',true);return;
    }
    record.analysis={bpm:captured.bpm,bassPct:captured.bassPct,db:Math.round(captured.db),seconds:captured.sec,capturedAt:new Date().toISOString(),method:'Web Audio / getDisplayMedia'};
    persist();render();
    setMessage('capture-status','Medições vinculadas a "'+record.title+'". São estimativas, não identificação automática do gênero.');
  }
  $('search-button').addEventListener('click',explore);
  $('add-track').addEventListener('click',saveTrack);
  $('new-track').addEventListener('click',resetForm);
  $('open-track').addEventListener('click',()=>{const record=selected();if(record && cleanUrl(record.url))window.open(cleanUrl(record.url),'_blank','noopener,noreferrer');});
  $('library-list').addEventListener('click',onLibraryClick);
  $('library-search').addEventListener('input',render);
  $('library-filter').addEventListener('change',render);
  $('export-json').addEventListener('click',exportJson);
  $('export-csv').addEventListener('click',exportCsv);
  $('import-file').addEventListener('change',importFile);
  $('start-capture').addEventListener('click',startCapture);
  $('stop-capture').addEventListener('click',()=>stopCapture(false));
  $('save-capture').addEventListener('click',saveCapture);
  window.addEventListener('pagehide',()=>{if(mediaStream)stopCapture(false);});
  initBars();resetCaptured();render();
})();
