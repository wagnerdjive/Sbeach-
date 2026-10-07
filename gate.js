// The gate screen: pick the event, read a ticket with the camera (or type the code), then confirm the entry by hand.
// Used by the staff panel's "Entrada" tab and by entrada.html, which door staff open with their own limited access.
//   const gate = SouthBeachGate.mount(rootElement, { api });   // api(path, options) -> parsed JSON, throws Error on failure
//   gate.load();  gate.stop();
(() => {
  const TEMPLATE = `
    <section class="gate" aria-label="Controlo de entrada">
      <div class="gate-head">
        <label>Evento<select data-g-event></select></label>
        <div class="gate-count" role="status"><p><strong data-g-admitted>0</strong><span> de <b data-g-issued>0</b> entraram</span></p><div class="gate-bar" aria-hidden="true"><i data-g-bar></i></div></div>
      </div>
      <div class="gate-types" data-g-types></div>
      <div class="gate-actions">
        <button type="button" class="gate-scan" data-g-camera>Ler bilhete com a câmara</button>
        <form class="entry-form" data-g-form>
          <label>Ou escreva / leia o código<input name="code" autocomplete="off" autocapitalize="characters" spellcheck="false" maxlength="64" required placeholder="Código do bilhete" /></label>
          <button class="admin-primary" type="submit">Verificar</button>
        </form>
      </div>
      <p class="admin-message" data-g-message role="status"></p>
      <div class="entry-result idle" data-g-result role="status" aria-live="assertive"><strong>Pronto para ler</strong><span>Aponte a câmara ao QR do bilhete ou escreva o código.</span></div>
      <div class="gate-log"><h3>Últimas entradas</h3><ol data-g-log></ol><p class="admin-empty" data-g-empty>Ainda não entrou ninguém.</p></div>
    </section>
    <div class="scan" data-g-scan hidden role="dialog" aria-modal="true" aria-label="Leitura de bilhetes">
      <video class="scan-video" data-g-video playsinline muted></video>
      <canvas data-g-canvas hidden></canvas>
      <div class="scan-top"><div><strong data-g-scan-event></strong><span data-g-scan-count></span></div><div class="scan-tools"><button type="button" data-g-torch hidden>Lanterna</button><button type="button" data-g-sound aria-pressed="true">Som</button><button type="button" data-g-close>Fechar</button></div></div>
      <div class="scan-frame" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
      <p class="scan-hint">Aponte ao QR do bilhete</p>
      <div class="scan-result idle" data-g-scan-result role="status" aria-live="assertive"></div>
    </div>`;

  const el = (tag, text, className) => { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; if (className) node.className = className; return node; };
  const button = (label, onClick) => { const b = el('button', label); b.type = 'button'; b.addEventListener('click', onClick); return b; };
  const maputo = (options) => new Intl.DateTimeFormat('pt-PT', { timeZone: 'Africa/Maputo', ...options });
  const timeOf = (iso) => maputo({ timeStyle: 'short' }).format(new Date(iso));

  function mount(root, { api }) {
    root.innerHTML = TEMPLATE;
    const q = (name) => root.querySelector(`[data-g-${name}]`);
    const eventSelect = q('event'), form = q('form'), result = q('result'), message = q('message');
    const camera = q('camera'), scan = q('scan'), video = q('video'), canvas = q('canvas'), scanResult = q('scan-result');
    const torchButton = q('torch'), soundButton = q('sound');

    const eventName = () => eventSelect.selectedOptions[0]?.text.split(' — ')[0] || '';
    const outcomes = {
      ADMITTED: ['ok', 'ENTRADA AUTORIZADA', (r) => r.ticketType || ''],
      ALREADY_USED: ['bad', 'JÁ UTILIZADO', (r) => `Entrou às ${timeOf(r.usedAt)}${r.ticketType ? ` · ${r.ticketType}` : ''}`],
      WRONG_EVENT: ['bad', 'BILHETE DE OUTRO EVENTO', (r) => `Este bilhete é para «${r.eventTitle || 'outro evento'}». Está a validar «${eventName()}». Não entra.`],
      VOID: ['bad', 'BILHETE ANULADO', () => 'Encomenda reembolsada ou cancelada.'],
      NOT_FOUND: ['bad', 'CÓDIGO DESCONHECIDO', () => 'Confira o código ou peça outro bilhete.'],
      UNDONE: ['warn', 'ENTRADA DESFEITA', (r) => `${r.ticketType || 'Bilhete'} pode voltar a ser lido.`],
      NOT_USED: ['warn', 'AINDA NÃO ENTROU', () => 'Este bilhete não tem entrada registada.'],
      VALID: ['info', 'BILHETE VÁLIDO', (r) => `${r.ticketType || ''} · ainda não entrou`]
    };
    const say = (text) => { message.textContent = text; };

    // ---- Sound and vibration: feedback without looking at the screen. The choice is remembered on this device.
    let soundOn = (() => { try { return localStorage.getItem('gateSound') !== 'off'; } catch (_) { return true; } })();
    let audio = null;
    function beep(kind) {
      if (kind === 'warn') kind = 'bad';
      try { navigator.vibrate?.(kind === 'ok' ? 90 : [140, 70, 140]); } catch (_) { /* not supported */ }
      if (!soundOn) return;
      try {
        audio = audio || new (window.AudioContext || window.webkitAudioContext)();
        const tones = kind === 'ok' ? [[880, 0, 0.14]] : [[240, 0, 0.18], [200, 0.24, 0.22]];
        tones.forEach(([freq, start, length]) => {
          const osc = audio.createOscillator(), gain = audio.createGain();
          osc.type = 'sine'; osc.frequency.value = freq; gain.gain.value = 0.18;
          osc.connect(gain); gain.connect(audio.destination);
          osc.start(audio.currentTime + start); osc.stop(audio.currentTime + start + length);
        });
      } catch (_) { /* no audio available */ }
    }
    const paintSound = () => { soundButton.setAttribute('aria-pressed', String(soundOn)); soundButton.textContent = soundOn ? 'Som ligado' : 'Som desligado'; };
    soundButton.addEventListener('click', () => { soundOn = !soundOn; try { localStorage.setItem('gateSound', soundOn ? 'on' : 'off'); } catch (_) { /* ignore */ } paintSound(); if (soundOn) beep('ok'); });
    paintSound();

    // ---- Counts and the latest entries
    async function refresh() {
      if (!eventSelect.value) return;
      const id = eventSelect.value;
      const [stats, recent] = await Promise.all([api(`/api/gate/events/${id}/entry-stats`), api(`/api/gate/events/${id}/entry-recent`)]);
      q('admitted').textContent = stats.admitted; q('issued').textContent = stats.issued;
      q('bar').style.width = stats.issued ? `${Math.round((stats.admitted / stats.issued) * 100)}%` : '0%';
      q('scan-count').textContent = `${stats.admitted} de ${stats.issued} entraram`;
      q('types').replaceChildren(...stats.byType.map((type) => el('span', `${type.name} ${type.admitted}/${type.issued}`, 'gate-type')));
      q('log').replaceChildren(...recent.map((entry) => {
        const li = el('li');
        li.append(el('span', timeOf(entry.usedAt), 'gate-log-time'), el('span', entry.ticketType, 'gate-log-type'), el('code', `…${entry.code.slice(-6)}`));
        li.append(button('Desfazer', async () => {
          if (!confirm(`Desfazer a entrada do bilhete …${entry.code.slice(-6)} (${entry.ticketType})? Ele poderá voltar a ser lido.`)) return;
          await undo(entry.code);
        }));
        return li;
      }));
      q('empty').hidden = recent.length > 0;
    }

    async function load() {
      const events = await api('/api/gate/events');
      const previous = eventSelect.value;
      eventSelect.replaceChildren(...events.map((event) => new Option(`${event.title} — ${maputo({ dateStyle: 'short', timeStyle: 'short' }).format(new Date(event.startsAt))}`, event.id)));
      if (previous && [...eventSelect.options].some((o) => o.value === previous)) eventSelect.value = previous;
      else { // the event that is on now or next
        const now = Date.now(), next = events.find((event) => new Date(event.endsAt || event.startsAt).getTime() + 6 * 3600e3 >= now);
        if (next) eventSelect.value = String(next.id);
      }
      q('scan-event').textContent = eventName();
      say(events.length ? '' : 'Não há eventos abertos para validar bilhetes.');
      await refresh();
      form.elements.code.focus({ preventScroll: true });
    }

    // ---- Answers: shown on the page and in the camera view
    let resetTimer = null, pausedUntil = 0, lastScan = { code: '', at: 0 };
    function paint(target, kind, title, detail, extra) {
      target.className = `${target === scanResult ? 'scan-result' : 'entry-result'} ${kind}`;
      target.replaceChildren(el('strong', title), el('span', detail || ''), ...(extra ? [extra] : []));
    }
    function idleResult() {
      result.className = 'entry-result idle'; result.replaceChildren(el('strong', 'Pronto para ler'), el('span', 'Aponte a câmara ao QR do bilhete ou escreva o código.'));
      scanResult.className = 'scan-result idle'; scanResult.replaceChildren();
    }
    function clearResult() { clearTimeout(resetTimer); idleResult(); pausedUntil = 0; lastScan = { code: '', at: 0 }; }
    function present(kind, title, detail, action, secondary) {
      const row = () => {
        const items = [action, secondary].filter(Boolean);
        if (!items.length) return null;
        const wrap = el('div', undefined, 'result-actions');
        items.forEach((item, index) => { const b = button(item.label, item.run); if (index === 0 && kind === 'info') b.className = 'result-main'; wrap.append(b); });
        return wrap;
      };
      paint(result, kind, title, detail, row());
      paint(scanResult, kind, title, detail, row());
      beep(kind === 'info' ? 'ok' : kind);
      clearTimeout(resetTimer);
      if (kind === 'info') { pausedUntil = performance.now() + 120000; return; } // waits for a decision: no auto-reset, camera paused
      resetTimer = setTimeout(idleResult, kind === 'ok' ? 4500 : 6500);
    }
    const explain = (outcome) => outcomes[outcome] || ['bad', outcome, () => ''];
    const post = (path, code) => api(path, { method: 'POST', body: JSON.stringify({ eventId: Number(eventSelect.value), code }) });

    async function admit(code) {
      const r = await post('/api/gate/check-in', code);
      const [kind, title, detail] = explain(r.outcome);
      present(kind, title, detail(r), r.outcome === 'ADMITTED' ? { label: 'Foi engano? Desfazer', run: () => undo(code) } : null);
      pausedUntil = performance.now() + 1200;
      await refresh();
    }
    async function undo(code) {
      try {
        const r = await post('/api/gate/check-in/undo', code);
        const [kind, title, detail] = explain(r.outcome);
        present(kind, title, detail(r));
        await refresh();
      } catch (error) { present('bad', 'ERRO', error.message); }
    }
    // Reading a ticket never uses it: the answer comes with a button, and the entry is only recorded when staff tap it.
    async function validate(code) {
      if (!eventSelect.value) { say('Escolha o evento.'); return; }
      try {
        const r = await post('/api/gate/check-in/peek', code);
        const [kind, title, detail] = explain(r.outcome);
        const valid = r.outcome === 'VALID';
        present(kind, title, detail(r),
          valid ? { label: 'Confirmar entrada', run: () => admit(code).catch((error) => present('bad', 'ERRO', error.message)) } : null,
          valid ? { label: 'Ler outro', run: clearResult } : null);
      } catch (error) { present('bad', 'ERRO', error.message); }
    }

    eventSelect.addEventListener('change', () => { q('scan-event').textContent = eventName(); refresh().catch((error) => say(error.message)); });
    form.addEventListener('submit', async (submitEvent) => {
      submitEvent.preventDefault();
      const input = form.elements.code;
      const code = input.value.trim().toUpperCase();
      input.value = '';
      if (code) await validate(code);
      input.focus({ preventScroll: true });
    });

    // ---- Camera: the browser's own detector where it exists, jsQR everywhere else (Safari on iPhone included)
    let stream = null, frameHandle = null, lastFrame = 0, detector = null, torchOn = false;
    async function startCamera() {
      if (!eventSelect.value) { say('Escolha o evento.'); return; }
      if (!navigator.mediaDevices?.getUserMedia || !window.isSecureContext) {
        present('warn', 'CÂMARA INDISPONÍVEL', 'A câmara só funciona numa ligação segura (https). Use o campo de código ou um leitor de QR.');
        return;
      }
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false });
      } catch (_) {
        present('warn', 'SEM ACESSO À CÂMARA', 'Permita a câmara nas definições do navegador, ou use o campo de código.');
        return;
      }
      video.srcObject = stream;
      await video.play().catch(() => {});
      scan.hidden = false; document.documentElement.classList.add('scan-open');
      try { audio = audio || new (window.AudioContext || window.webkitAudioContext)(); audio.resume?.(); } catch (_) { /* a touch on the button also unlocks sound on iPhone */ }
      const track = stream.getVideoTracks()[0];
      torchButton.hidden = !track.getCapabilities?.().torch;
      torchOn = false; torchButton.textContent = 'Lanterna';
      detector = 'BarcodeDetector' in window ? new BarcodeDetector({ formats: ['qr_code'] }) : null;
      pausedUntil = 0;
      frameHandle = requestAnimationFrame(tick);
    }
    function stop() {
      cancelAnimationFrame(frameHandle); frameHandle = null;
      stream?.getTracks().forEach((track) => track.stop()); stream = null;
      video.srcObject = null; scan.hidden = true; document.documentElement.classList.remove('scan-open');
    }
    async function tick(now) {
      frameHandle = requestAnimationFrame(tick);
      if (now - lastFrame < 110 || now < pausedUntil || video.readyState < 2) return; // about nine reads per second
      lastFrame = now;
      let code = null;
      try {
        if (detector) code = (await detector.detect(video))[0]?.rawValue;
        else if (window.jsQR) {
          const width = 640, height = Math.round(width * (video.videoHeight / video.videoWidth)) || 480;
          canvas.width = width; canvas.height = height;
          const context = canvas.getContext('2d', { willReadFrequently: true });
          context.drawImage(video, 0, 0, width, height);
          code = window.jsQR(context.getImageData(0, 0, width, height).data, width, height, { inversionAttempts: 'dontInvert' })?.data;
        }
      } catch (_) { return; } // a frame without a readable code
      code = code?.trim().toUpperCase();
      // The same QR stays in view for a while: ignore repeats for four seconds.
      if (!code || (code === lastScan.code && Date.now() - lastScan.at < 4000)) return;
      lastScan = { code, at: Date.now() }; pausedUntil = performance.now() + 1600;
      await validate(code);
    }
    camera.addEventListener('click', startCamera);
    q('close').addEventListener('click', stop);
    document.addEventListener('keydown', (keyEvent) => { if (keyEvent.key === 'Escape' && !scan.hidden) stop(); });
    torchButton.addEventListener('click', async () => {
      try { torchOn = !torchOn; await stream.getVideoTracks()[0].applyConstraints({ advanced: [{ torch: torchOn }] }); torchButton.textContent = torchOn ? 'Lanterna ligada' : 'Lanterna'; }
      catch (_) { torchButton.hidden = true; }
    });
    return { load, stop };
  }

  window.SouthBeachGate = { mount };
})();
