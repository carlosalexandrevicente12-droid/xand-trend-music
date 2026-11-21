"use strict";

const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { Script, createContext } = require("node:vm");
const test = require("node:test");
const assert = require("node:assert/strict");

const workerSource = readFileSync(join(__dirname, "..", "background.js"), "utf8");
const PROJECT_ID = "g-p-6b0182d2a7a0819183158c736eb617ed";
const projectHome = "https://chatgpt.com/g/" + PROJECT_ID + "/project";
const projectChat = "https://chatgpt.com/g/" + PROJECT_ID +
  "-xand-music-capas-tiktok/c/test-conversation";

function createHarness(url = projectChat, response = () => ({
  assistantCount: 0, signature: "", busy: false, error: false, lastUserText: "teste"
})) {
  let now = 0;
  let nextTabId = 10;
  const tabs = new Map([[1, { id: 1, url }]]);
  const saved = new Map();
  const opened = [];
  const events = {};
  const fakeChrome = {
    storage: {
      session: {
        async get(key) {
          if (key === null) return Object.fromEntries(saved);
          if (Array.isArray(key)) return Object.fromEntries(key.map((x) => [x, saved.get(x)]));
          return { [key]: saved.get(key) };
        },
        async set(values) {
          for (const [key, value] of Object.entries(values)) saved.set(key, value);
        },
        async remove(keys) {
          for (const key of Array.isArray(keys) ? keys : [keys]) saved.delete(key);
        }
      }
    },
    alarms: {
      async get() { return null; },
      async create() {},
      onAlarm: { addListener(fn) { events.alarm = fn; } }
    },
    runtime: {
      onInstalled: { addListener() {} },
      onStartup: { addListener() {} },
      onMessage: { addListener(fn) { events.message = fn; } }
    },
    tabs: {
      async get(id) {
        if (!tabs.has(id)) throw new Error("missing tab");
        return tabs.get(id);
      },
      async create({ url: newUrl }) {
        const tab = { id: ++nextTabId, url: newUrl };
        tabs.set(tab.id, tab);
        opened.push(tab);
        return tab;
      },
      async sendMessage(id, message) {
        if (message.type === "XAND_PROBE") return response();
        if (message.type === "XAND_INSERT_HANDOFF") return { submitted: true };
        throw new Error("unexpected message");
      },
      onRemoved: { addListener() {} },
      onUpdated: { addListener() {} }
    },
    action: { onClicked: { addListener(fn) { events.action = fn; } } }
  };
  class FakeDate extends Date { static now() { return now; } }
  const context = createContext({ chrome: fakeChrome, Date: FakeDate, URL, console });
  new Script(workerSource).runInContext(context);

  const drain = async () => {
    for (let i = 0; i < 6; i++) await new Promise((resolve) => setImmediate(resolve));
  };
  return {
    events, saved, opened, tabs,
    setTime(t) { now = t; },
    async submit() {
      events.message({ type: "XAND_SUBMITTED", assistantCount: 0, signature: "" },
        { tab: tabs.get(1) }, () => {});
      await drain();
    },
    async tick() {
      events.alarm({ name: "xand-watchdog-check" });
      await drain();
    },
    async clickAction() {
      events.action(tabs.get(1));
      await drain();
    }
  };
}

test("manual recovery opens exactly one chat in the same project", async () => {
  const h = createHarness();
  await h.clickAction();
  assert.equal(h.opened.length, 1);
  assert.equal(h.opened[0].url, projectHome);
});

test("six-minute no-response opens a new project chat only once", async () => {
  const h = createHarness();
  await h.submit();
  h.setTime(5 * 60_000);
  await h.tick();
  assert.equal(h.opened.length, 0);
  h.setTime(6 * 60_000 + 1);
  await h.tick();
  assert.equal(h.opened.length, 1);
  h.setTime(12 * 60_000);
  await h.tick();
  assert.equal(h.opened.length, 1);
});

test("a completed assistant response prevents false recovery", async () => {
  const h = createHarness(projectChat, () => ({
    assistantCount: 1, signature: "Respondido", busy: false, error: false
  }));
  await h.submit();
  h.setTime(7 * 60_000);
  await h.tick();
  assert.equal(h.opened.length, 0);
  assert.equal(h.saved.has("pending_1"), false);
});

test("other projects and general chats are ignored", async () => {
  for (const url of [
    "https://chatgpt.com/c/other-chat",
    "https://chatgpt.com/g/g-p-unrelated-project/c/other"
  ]) {
    const h = createHarness(url);
    await h.submit();
    h.setTime(12 * 60_000);
    await h.tick();
    await h.clickAction();
    assert.equal(h.opened.length, 0);
  }
});
