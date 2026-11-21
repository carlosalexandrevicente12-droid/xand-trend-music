"use strict";

// Isolated watchdog for the user's XAND MUSIC ChatGPT Project. No remote API calls.
const PROJECT_ID = "g-p-6b0182d2a7a0819183158c736eb617ed";
const PROJECT_PREFIX = "/g/" + PROJECT_ID + "/";
const PROJECT_HOME = "https://chatgpt.com" + PROJECT_PREFIX + "project";
const ALARM_NAME = "xand-watchdog-check";
const PENDING_PREFIX = "pending_";
const RECOVERY_PREFIX = "recovery_";
const SUPPRESS_PREFIX = "suppress_";
const CHECK_PERIOD_MIN = 1;
const ERROR_GRACE_MS = 45 * 1000;
const NO_RESPONSE_MS = 6 * 60 * 1000;
const STREAM_STALL_MS = 9 * 60 * 1000;
const RECOVERY_RETRY_MS = 2 * 60 * 1000;

function isProject(url) {
  try {
    const parsed = new URL(url);
    return parsed.origin === "https://chatgpt.com" &&
      parsed.pathname.startsWith(PROJECT_PREFIX);
  } catch {
    return false;
  }
}

const pendingKey = (tabId) => PENDING_PREFIX + tabId;
const recoveryKey = (tabId) => RECOVERY_PREFIX + tabId;
const suppressKey = (tabId) => SUPPRESS_PREFIX + tabId;
const sessionGet = async (key) => (await chrome.storage.session.get(key))[key];
const sessionSet = async (key, value) => chrome.storage.session.set({ [key]: value });
const sessionRemove = async (key) => chrome.storage.session.remove(key);

async function ensureAlarm() {
  const existing = await chrome.alarms.get(ALARM_NAME);
  if (!existing) {
    await chrome.alarms.create(ALARM_NAME, { periodInMinutes: CHECK_PERIOD_MIN });
  }
}

chrome.runtime.onInstalled.addListener(() => { void ensureAlarm(); });
chrome.runtime.onStartup.addListener(() => { void ensureAlarm(); });
void ensureAlarm();

chrome.runtime.onMessage.addListener((message, sender, respond) => {
  if (!sender.tab || !isProject(sender.tab.url)) return false;
  const tabId = sender.tab.id;
  if (message?.type === "XAND_SUBMITTED") {
    (async () => {
      // The recovery chat is exempt: never enter a self-triggered recovery loop.
      if (await sessionGet(suppressKey(tabId))) return;
      await sessionSet(pendingKey(tabId), {
        submittedAt: Date.now(),
        lastProgressAt: Date.now(),
        assistantCount: Number(message.assistantCount || 0),
        signature: String(message.signature || ""),
        attempted: false,
        urlAtSubmit: sender.tab.url
      });
    })().catch(console.warn);
    return false;
  }
  if (message?.type === "XAND_RECOVERY_RESULT") {
    (async () => {
      const current = await sessionGet(recoveryKey(tabId));
      if (!current) return;
      if (message.submitted) await sessionRemove(recoveryKey(tabId));
      else await sessionSet(recoveryKey(tabId), { ...current, lastError: String(message.reason || "") });
    })().catch(console.warn);
    return false;
  }
  return false;
});

chrome.tabs.onRemoved.addListener((tabId) => {
  void chrome.storage.session.remove([
    pendingKey(tabId), recoveryKey(tabId), suppressKey(tabId)
  ]);
});

async function probeTab(tabId) {
  try {
    return await chrome.tabs.sendMessage(tabId, { type: "XAND_PROBE" });
  } catch {
    return null; // May be loading, sleeping, or not a compatible ChatGPT view.
  }
}

async function recover(tabId, reason, testOnly = false, probe = null) {
  const original = await chrome.tabs.get(tabId).catch(() => null);
  if (!original || !isProject(original.url)) return false;

  const key = pendingKey(tabId);
  const pending = await sessionGet(key);
  if (pending?.attempted && !testOnly) return false;

  // Mark first, so a delayed alarm can never open multiple new chats.
  if (pending && !testOnly) {
    await sessionSet(key, { ...pending, attempted: true, attemptedAt: Date.now() });
  }
  const data = probe || await probeTab(tabId) || {};
  const lastUserText = String(data.lastUserText || "").slice(0, 900);
  const originalUrl = original.url;
  const handoff = [
    "RECUPERAÇÃO AUTOMÁTICA — XAND MUSIC / CAPAS TIKTOK.",
    testOnly
      ? "Este é um TESTE do mecanismo de continuidade; não execute tarefas reais."
      : "O chat anterior ficou sem resposta ou apresentou erro. Retome somente o que ficou pendente.",
    "Projeto: XAND MUSIC - Capas TikTok. Trabalhe exclusivamente no modo Chat.",
    "Chat anterior: " + originalUrl,
    lastUserText ? "Último pedido (trecho): " + lastUserText : "",
    "Consulte as instruções, a memória e as Fontes do projeto. Verifique o estado real antes de executar.",
    "NÃO repita publicações, envios, exclusões, alterações no GitHub ou outras ações externas sem verificar se já ocorreram.",
    "Preserve tudo; diga qual etapa pode continuar e qual depende de confirmação."
  ].filter(Boolean).join("\n\n");

  const next = await chrome.tabs.create({ url: PROJECT_HOME, active: true });
  // New chat never triggers another recovery from its own handoff.
  await sessionSet(suppressKey(next.id), { createdAt: Date.now(), origin: tabId });
  await sessionSet(recoveryKey(next.id), {
    createdAt: Date.now(), text: handoff, originalTabId: tabId, testOnly
  });
  await tryHandoff(next.id);
  return true;
}

async function tryHandoff(tabId) {
  const key = recoveryKey(tabId);
  const job = await sessionGet(key);
  if (!job) return;
  if (Date.now() - job.createdAt > RECOVERY_RETRY_MS) {
    await sessionRemove(key); // New project tab remains open for manual continuation.
    return;
  }
  let result = null;
  try {
    result = await chrome.tabs.sendMessage(tabId, {
      type: "XAND_INSERT_HANDOFF", text: job.text
    });
  } catch {
    return; // React app may not have mounted; next alarm retries.
  }
  if (result?.submitted) await sessionRemove(key);
}

async function checkPending(tabId, state, now) {
  const tab = await chrome.tabs.get(tabId).catch(() => null);
  if (!tab || !isProject(tab.url)) {
    await sessionRemove(pendingKey(tabId));
    return;
  }
  if (state.attempted) return;

  const probe = await probeTab(tabId);
  if (!probe) return;
  const assistantCount = Number(probe.assistantCount || 0);
  const changed = assistantCount > state.assistantCount ||
    (probe.signature && probe.signature !== state.signature);

  // Any new assistant output is considered progress; completed output clears the timer.
  if (changed) {
    state.signature = String(probe.signature || "");
    state.lastProgressAt = now;
    if (!probe.busy && assistantCount > state.assistantCount) {
      await sessionRemove(pendingKey(tabId));
      return;
    }
    await sessionSet(pendingKey(tabId), state);
  }

  const elapsed = now - state.submittedAt;
  const idle = now - state.lastProgressAt;
  const error = !!probe.error && elapsed >= ERROR_GRACE_MS;
  const noResponse = !probe.busy &&
    assistantCount <= state.assistantCount && elapsed >= NO_RESPONSE_MS;
  const stalled = !!probe.busy && idle >= STREAM_STALL_MS;
  if (error || noResponse || stalled) {
    await recover(tabId, error ? "visible-error" : stalled ? "stalled-stream" : "no-response", false, probe);
  }
}

async function checkAll() {
  const now = Date.now();
  const all = await chrome.storage.session.get(null);
  for (const [key, value] of Object.entries(all)) {
    if (key.startsWith(PENDING_PREFIX) && value && !value.attempted) {
      const tabId = Number(key.slice(PENDING_PREFIX.length));
      if (Number.isInteger(tabId)) {
        await checkPending(tabId, value, now).catch(console.warn);
      }
    }
    if (key.startsWith(RECOVERY_PREFIX) && value) {
      const tabId = Number(key.slice(RECOVERY_PREFIX.length));
      if (Number.isInteger(tabId)) await tryHandoff(tabId).catch(console.warn);
    }
  }
}

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === ALARM_NAME) void checkAll();
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.status === "complete") void tryHandoff(tabId);
});

// Manual, non-destructive self-test: click the extension icon while a project tab is active.
chrome.action.onClicked.addListener((tab) => {
  if (isProject(tab.url)) void recover(tab.id, "manual-test", true).catch(console.warn);
});
