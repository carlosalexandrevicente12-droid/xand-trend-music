"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const contentScript = fs.readFileSync(path.join(__dirname, "..", "content.js"), "utf8");

function element(text, { children = 0, visible = true } = {}) {
  return {
    textContent: text,
    children: Array.from({ length: children }),
    getClientRects: () => visible ? [{}] : []
  };
}

function detected({ alerts = [], leaves = [] } = {}) {
  const context = {
    document: {
      querySelectorAll: (selector) =>
        selector.includes('[role="alert"]') ? alerts : leaves,
      addEventListener: () => {}
    },
    chrome: { runtime: { onMessage: { addListener: () => {} } } },
    location: { origin: "https://chatgpt.com", pathname: "/g/test/project" }
  };
  vm.createContext(context);
  vm.runInContext(contentScript, context);
  return vm.runInContext("visibleError()", context);
}

test("detecta falha de conexao em alerta do ChatGPT", () => {
  assert.equal(detected({
    alerts: [element("Sem resposta do ChatGPT. Verifique sua conexão.")]
  }), true);
});

test("detecta erro exibido como texto visivel sem atributo de alerta", () => {
  assert.equal(detected({
    leaves: [element("Sem resposta do ChatGPT. Verifique sua conexão.")]
  }), true);
});

test("nao confunde texto oculto ou citacao historica com erro atual", () => {
  assert.equal(detected({
    leaves: [
      element("Sem resposta do ChatGPT. Verifique sua conexão.", { visible: false }),
      element("A mensagem anterior dizia: Sem resposta do ChatGPT. Verifique sua conexão.")
    ]
  }), false);
});

test("mantem reconhecimento dos erros de rede anteriores", () => {
  assert.equal(detected({ alerts: [element("Network error")] }), true);
  assert.equal(detected({ alerts: [element("Tudo funcionando")] }), false);
});

test("reconhece botao Enviar sem atributo type=submit", () => {
  const context = {
    document: { addEventListener: () => {} },
    chrome: { runtime: { onMessage: { addListener: () => {} } } },
    location: { origin: "https://chatgpt.com", pathname: "/g/test/project" }
  };
  vm.createContext(context);
  vm.runInContext(contentScript, context);
  const isSendButton = vm.runInContext("isSendButton", context);
  const button = (attributes) => ({
    type: "button",
    textContent: "",
    getAttribute: (key) => attributes[key] || null
  });
  assert.equal(isSendButton(button({ "aria-label": "Enviar mensagem" })), true);
  assert.equal(isSendButton(button({ "data-tooltip": "Enviar" })), true);
  assert.equal(isSendButton(button({ "aria-label": "Fechar" })), false);
});

test("registra o pedido digitado antes de o editor ser esvaziado", () => {
  let sent = null;
  const editor = { innerText: "Link TikTok: https://www.tiktok.com/@teste/video/123" };
  const context = {
    document: {
      addEventListener: () => {},
      querySelector: (selector) => selector.includes("#prompt-textarea") ? editor : null,
      querySelectorAll: () => []
    },
    chrome: {
      runtime: {
        onMessage: { addListener: () => {} },
        sendMessage: (message) => { sent = message; return Promise.resolve(); }
      }
    },
    location: { origin: "https://chatgpt.com", pathname: "/g/g-p-test/project" }
  };
  vm.createContext(context);
  vm.runInContext(contentScript, context);
  // Use the actual project ID instead of a generic project in the test.
  context.location.pathname = "/g/g-p-6b0182d2a7a0819183158c736eb617ed/project";
  vm.runInContext("notifySubmitted()", context);
  assert.equal(sent.type, "XAND_SUBMITTED");
  assert.equal(sent.userText, editor.innerText);
});

test("encontra ultimo pedido na regiao acessivel quando nao ha atributo de autoria", () => {
  const context = {
    document: {
      addEventListener: () => {},
      querySelectorAll: (selector) =>
        selector.includes('[role="region"]')
          ? [{ innerText: "TESTE XAND: mensagem pendente" }]
          : []
    },
    chrome: { runtime: { onMessage: { addListener: () => {} } } },
    location: { origin: "https://chatgpt.com", pathname: "/g/test/project" }
  };
  vm.createContext(context);
  vm.runInContext(contentScript, context);
  assert.equal(vm.runInContext("latestUserText()", context), "TESTE XAND: mensagem pendente");
});

async function attemptHandoffWithDraft(existing) {
  const marker = "RECUPERAÇÃO AUTOMÁTICA — XAND MUSIC";
  const payload = marker + " / CAPAS TIKTOK.\nNovo pedido: teste fictício.";
  let inserted = null;
  let clicks = 0;
  let selections = 0;
  const editor = {
    innerText: existing,
    textContent: existing,
    focus() {}
  };
  const sendButton = {
    disabled: false,
    type: "button",
    textContent: "",
    className: "",
    getAttribute: (key) => key === "aria-label" ? "Enviar" : null,
    click() { clicks++; editor.innerText = ""; editor.textContent = ""; }
  };
  const context = {
    payload,
    setTimeout,
    document: {
      addEventListener: () => {},
      querySelector: (selector) => selector.includes("#prompt-textarea") ? editor : null,
      querySelectorAll: (selector) => selector === "button" ? [sendButton] : [],
      createRange: () => ({ selectNodeContents() { selections++; } }),
      execCommand: (_command, _showUI, text) => {
        inserted = text;
        editor.innerText = text;
        editor.textContent = text;
        return true;
      }
    },
    window: {
      getSelection: () => ({ removeAllRanges() {}, addRange() {} })
    },
    chrome: { runtime: { onMessage: { addListener: () => {} } } },
    location: {
      origin: "https://chatgpt.com",
      pathname: "/g/g-p-6b0182d2a7a0819183158c736eb617ed/project"
    }
  };
  vm.createContext(context);
  vm.runInContext(contentScript, context);
  const result = await vm.runInContext("insertHandoff(payload)", context);
  return { result, inserted, clicks, selections, existing };
}

test("substitui somente rascunho antigo da propria recuperacao", async () => {
  const r = await attemptHandoffWithDraft(
    "RECUPERAÇÃO AUTOMÁTICA — XAND MUSIC / CAPAS TIKTOK.\nPedido antigo."
  );
  assert.equal(r.result.submitted, true);
  assert.match(r.inserted, /Novo pedido: teste fictício/);
  assert.equal(r.selections, 1);
  assert.equal(r.clicks, 1);
});

test("preserva rascunho escrito pelo usuario e bloqueia envio", async () => {
  const r = await attemptHandoffWithDraft("Meu texto de trabalho ainda não enviado");
  assert.equal(r.result.submitted, false);
  assert.equal(r.inserted, null);
  assert.equal(r.clicks, 0);
});

test("detecta aviso de resposta interrompida no ChatGPT", () => {
  const text = "Conexão interrompida. Aguardando a resposta completa";
  assert.equal(detected({ alerts: [element(text)] }), true);
  assert.equal(detected({ leaves: [element(text)] }), true);
  assert.equal(detected({
    leaves: [element("O usuário relatou: " + text)]
  }), false);
});

function detectsLimit(notices) {
  const context = {
    document: {
      querySelectorAll: () => notices,
      addEventListener: () => {}
    },
    chrome: { runtime: { onMessage: { addListener: () => {} } } },
    location: { origin: "https://chatgpt.com", pathname: "/g/test/project" }
  };
  vm.createContext(context);
  vm.runInContext(contentScript, context);
  return vm.runInContext("conversationLimitReached()", context);
}

test("detecta limite explicito em portugues e ingles", () => {
  for (const warning of [
    "Você atingiu o limite máximo desta conversa.",
    "Esta conversa atingiu o limite.",
    "You've reached the maximum length for this conversation, but you can start a new chat."
  ]) {
    assert.equal(detectsLimit([element(warning)]), true, warning);
  }
});

test("ignora mencoes antigas ou ocultas sobre limite", () => {
  const historical = element("Você atingiu o limite máximo desta conversa.");
  historical.closest = () => ({ getAttribute: () => "user" });
  assert.equal(detectsLimit([historical]), false);
  assert.equal(detectsLimit([element("Você atingiu o limite máximo desta conversa.", { visible: false })]), false);
  assert.equal(detectsLimit([element("Ele falou que você atingiu o limite máximo desta conversa.")]), false);
});

test("reconhece recuperacao ja enviada e nao reenvia a mesma mensagem", async () => {
  const message = "RECUPERAÇÃO AUTOMÁTICA — XAND MUSIC / CAPAS TIKTOK.\n\nRECUPERAÇÃO-ID: 123-456\n\nContinuar.";
  let clicks = 0;
  const editor = { innerText: "", textContent: "" };
  const context = {
    message,
    document: {
      addEventListener: () => {},
      querySelector: () => editor,
      querySelectorAll: (selector) => selector.includes('[data-message-author-role="user"]')
        ? [{ innerText: message }] : [],
      execCommand: () => { clicks++; }
    },
    chrome: { runtime: { onMessage: { addListener: () => {} } } },
    location: { origin: "https://chatgpt.com", pathname: "/g/g-p-6b0182d2a7a0819183158c736eb617ed/project" }
  };
  vm.createContext(context);
  vm.runInContext(contentScript, context);
  const result = await vm.runInContext("insertHandoff(message)", context);
  assert.equal(result.submitted, true);
  assert.equal(result.alreadyPresent, true);
  assert.equal(clicks, 0);
});

