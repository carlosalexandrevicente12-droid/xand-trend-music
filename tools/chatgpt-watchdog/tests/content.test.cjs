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
