"use strict";

// No screenshots, remote network requests, cookies, or persistent message storage.
const PROJECT_PREFIX = "/g/g-p-6b0182d2a7a0819183158c736eb617ed/";
const isProject = () => location.origin === "https://chatgpt.com" &&
  location.pathname.startsWith(PROJECT_PREFIX);

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
  return String(nodes.at(-1)?.innerText || nodes.at(-1)?.textContent || "").trim().slice(0, 900);
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
  return Array.from(document.querySelectorAll('[role="alert"], [data-testid*="error-message"]'))
    .filter((element) => element.getClientRects().length > 0)
    .some((element) => /something went wrong|ocorreu um erro|houve um problema|network error|erro de rede/i.test(
      element.textContent || ""
    ));
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
    lastUserText: latestUserText()
  };
}

function notifySubmitted() {
  if (!isProject()) return;
  const data = probe();
  chrome.runtime.sendMessage({
    type: "XAND_SUBMITTED",
    assistantCount: data.assistantCount,
    signature: data.signature
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
  // ChatGPT's current project composer uses an unlabeled submit button.
  if (button.type === "submit" &&
      button.closest("form")?.contains(promptComposer())) return true;
  const label = ((button.getAttribute("aria-label") || "") + " " +
    (button.getAttribute("data-testid") || "")).toLowerCase();
  return /(^|\s)(send|enviar)(\s|$|[-_])/.test(label) ||
    /send-button/.test(label);
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

function insertHandoff(text) {
  if (!isProject()) return { submitted: false, reason: "outside project" };
  const editor = promptComposer();
  if (!editor) return { submitted: false, reason: "composer not ready" };
  const payload = String(text || "").slice(0, 3500);
  if (!payload.startsWith("RECUPERAÇÃO AUTOMÁTICA")) {
    return { submitted: false, reason: "unexpected handoff" };
  }

  editor.focus();
  // The ChatGPT composer is a rich editor; insertText preserves its input events.
  document.execCommand("insertText", false, payload);
  if (!(editor.innerText || editor.textContent || "").includes("RECUPERAÇÃO AUTOMÁTICA")) {
    return { submitted: false, reason: "composer insertion failed" };
  }

  const composerForm = editor.closest("form");
  const sendButton = composerForm?.querySelector('button[type="submit"]:not([disabled])') ||
    Array.from(document.querySelectorAll("button"))
      .find((button) => isSendButton(button) && !button.disabled);
  if (sendButton) {
    sendButton.click();
    return { submitted: true };
  }
  // A draft is preferable to silently losing the continuity text.
  return { submitted: false, reason: "handoff left in composer; send unavailable" };
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === "XAND_PROBE") {
    sendResponse(isProject() ? probe() : null);
    return false;
  }
  if (message?.type === "XAND_INSERT_HANDOFF") {
    try {
      sendResponse(insertHandoff(message.text));
    } catch (error) {
      sendResponse({ submitted: false, reason: String(error).slice(0, 120) });
    }
    return false;
  }
  return false;
});
