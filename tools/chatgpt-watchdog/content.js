"use strict";

// No screenshots, remote network requests, cookies, or persistent message storage.
const PROJECT_ID = "g-p-6b0182d2a7a0819183158c736eb617ed";
const isProject = () => {
  const projectSegment = location.pathname.split("/")[2] || "";
  return location.origin === "https://chatgpt.com" &&
    (projectSegment === PROJECT_ID || projectSegment.startsWith(PROJECT_ID + "-"));
};

function assistantNodes() {
  const direct = Array.from(document.querySelectorAll('[data-message-author-role="assistant"]'));
  if (direct.length) return direct;
  return Array.from(document.querySelectorAll(
    'main article[data-testid*="conversation-turn"], main [data-testid^="conversation-turn-"]'
  )).filter((el) => /assistant|chatgpt/i.test(
    (el.getAttribute("data-message-author-role") || "") +
    (el.getAttribute("aria-label") || "")
  ));
}

function latestUserText() {
  const nodes = Array.from(document.querySelectorAll('[data-message-author-role="user"]'));
  if (nodes.length) {
    return String(nodes.at(-1)?.innerText || nodes.at(-1)?.textContent || "").trim().slice(0, 1400);
  }
  // ChatGPT may render the most recent user turn as an accessible labelled region.
  const regions = Array.from(document.querySelectorAll(
    '[role="region"][aria-label^="Você disse"], [role="region"][aria-label^="You said"]'
  ));
  return String(regions.at(-1)?.innerText || regions.at(-1)?.textContent || "")
    .trim().slice(0, 1400);
}

function isGenerating() {
  if (document.querySelector('button[data-testid="stop-button"], button[data-testid="stop-generating-button"]')) return true;
  return Array.from(document.querySelectorAll("main button")).some((btn) => {
    const label = ((btn.getAttribute("aria-label") || "") + " " +
      (btn.getAttribute("data-testid") || "")).trim();
    return /^(stop generating|stop response|parar de gerar|parar resposta|interromper resposta|stop-button)$/i.test(label);
  });
}

function visibleError() {
  const knownError = /something went wrong|ocorreu um erro|houve um problema|network error|erro de rede|sem resposta do chatgpt\.?\s*verifique sua conex[aã]o/i;
  const alerts = Array.from(document.querySelectorAll(
    '[role="alert"], [role="status"], [data-sonner-toast], [data-testid*="error-message"]'
  ));
  if (alerts.some((element) =>
    element.getClientRects().length > 0 && knownError.test(element.textContent || "")
  )) return true;

  // Some ChatGPT connection failures appear as plain text, without an alert role.
  // Match only the entire visible leaf to avoid treating a quoted past error as a live failure.
  return Array.from(document.querySelectorAll("p, span, small, div")).some((element) =>
    element.children.length === 0 &&
    element.getClientRects().length > 0 &&
    /^sem resposta do chatgpt\.?\s*verifique sua conex[aã]o\.?$/i.test(
      (element.textContent || "").trim()
    )
  );
}

function probe() {
  const answers = assistantNodes();
  const last = answers.at(-1);
  const lastText = String(last?.innerText || last?.textContent || "");
  return {
    assistantCount: answers.length,
    signature: lastText.slice(-350),
    busy: isGenerating(),
    error: visibleError(),
    lastUserText: latestUserText(),
    lastAssistantText: lastText.trim().slice(-650)
  };
}

function notifySubmitted() {
  if (!isProject()) return;
  const data = probe();
  // Capture the actual composer text before ChatGPT clears it on submission.
  // This stays in chrome.storage.session only and is never saved to disk.
  const editor = promptComposer();
  const userText = String(editor?.innerText || editor?.textContent || "").trim().slice(0, 1400);
  chrome.runtime.sendMessage({
    type: "XAND_SUBMITTED",
    assistantCount: data.assistantCount,
    signature: data.signature,
    userText,
    previousAssistantText: data.lastAssistantText
  }).catch(() => {});
}

function promptComposer() {
  return document.querySelector(
    '#prompt-textarea, [data-testid="prompt-textarea"], ' +
    'main [contenteditable="true"][role="textbox"], ' +
    '[contenteditable="true"][data-placeholder]'
  );
}

function isSendButton(button) {
  if (!button) return false;
  const label = [button.getAttribute("aria-label"), button.getAttribute("data-testid"),
    button.getAttribute("title"), button.getAttribute("data-tooltip"), button.textContent]
    .filter(Boolean).join(" ").toLowerCase();
  if (/(^|\s)(send|enviar)(\s|$|[-_])/.test(label) || /send-button/.test(label)) return true;
  if (button.type !== "submit" || !promptComposer()) return false;
  const editorRect = promptComposer().getBoundingClientRect();
  const buttonRect = button.getBoundingClientRect();
  return buttonRect.width > 0 && buttonRect.height > 0 &&
    buttonRect.left >= editorRect.left &&
    Math.abs(buttonRect.bottom - editorRect.bottom) < 160;
}

document.addEventListener("click", (event) => {
  if (!isProject()) return;
  const button = event.target?.closest?.("button");
  if (isSendButton(button)) notifySubmitted();
}, true);

document.addEventListener("keydown", (event) => {
  if (!isProject() || event.key !== "Enter" || event.shiftKey ||
    event.ctrlKey || event.metaKey || event.altKey || event.isComposing) return;
  const composer = promptComposer();
  if (composer && (event.target === composer || composer.contains(event.target))) {
    notifySubmitted();
  }
}, true);

async function insertHandoff(text) {
  if (!isProject()) return { submitted: false, reason: "outside project" };
  const editor = promptComposer();
  if (!editor) return { submitted: false, reason: "composer not ready" };
  const payload = String(text || "").slice(0, 3500);
  const marker = "RECUPERAÇÃO AUTOMÁTICA — XAND MUSIC";
  if (!payload.startsWith(marker)) {
    return { submitted: false, reason: "unexpected handoff" };
  }

  // A previous recovery may have left a stale draft at the project home.
  // Replace only our own recovery draft; never overwrite an unrelated user draft.
  const currentText = () => String(editor.innerText || editor.textContent || "");
  const normalize = (value) => String(value).replace(/\s+/g, " ").trim();
  const existing = currentText();
  if (existing.trim() && !existing.trim().startsWith(marker)) {
    return { submitted: false, drafted: true, reason: "unrelated user draft preserved" };
  }
  if (normalize(existing) !== normalize(payload)) {
    editor.focus();
    if (existing.trim()) {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(editor);
      selection.removeAllRanges();
      selection.addRange(range);
    }
    document.execCommand("insertText", false, payload);
  }
  if (normalize(currentText()) !== normalize(payload)) {
    return { submitted: false, drafted: true, reason: "handoff draft differs from requested payload" };
  }

  // Let the editor update the enabled state of its submit button.
  await new Promise((resolve) => setTimeout(resolve, 250));
  const buttons = Array.from(document.querySelectorAll('button[type="submit"]'))
    .filter((button) => !button.disabled && isSendButton(button));
  const sendButton = buttons.find((button) =>
    String(button.className).includes("bg-composer-primary")) || buttons.at(-1) ||
    Array.from(document.querySelectorAll("button"))
      .find((button) => isSendButton(button) && !button.disabled);
  if (sendButton) {
    sendButton.click();
    return { submitted: true };
  }
  // Leave the single draft available and stop retries rather than duplicating it.
  return { submitted: false, drafted: true, reason: "draft ready; send unavailable" };
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === "XAND_PROBE") {
    sendResponse(isProject() ? probe() : null);
    return false;
  }
  if (message?.type === "XAND_INSERT_HANDOFF") {
    void insertHandoff(message.text).then(sendResponse).catch((error) => {
      sendResponse({ submitted: false, reason: String(error).slice(0, 120) });
    });
    return true; // Keep the response channel open while the editor updates.
  }
  return false;
});
