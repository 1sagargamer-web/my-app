import {
  loadConfig, saveConfig, normalize, isFirebaseReady,
  adminSignIn, adminSignOut, onAdminAuth, DEFAULT_QUESTION,
} from "./store.js";
import { Music } from "./music.js";

const root = document.getElementById("root");
const logoutBtn = document.getElementById("logout");
const toastEl = document.getElementById("toast");
const music = new Music();
let cfg;

const ACTIONS = [
  ["happy", "💖 Happy boy + song (Yes)"],
  ["retry", "🤔 'Phir se soch lo' screen (No)"],
  ["sad", "😢 Sad boy (final No)"],
];

const clone = (v) => JSON.parse(JSON.stringify(v));

function h(tag, props = {}, ...children) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else if (k === "class") e.className = v;
    else if (k.includes("-")) e.setAttribute(k, v);
    else if (v === true) e.setAttribute(k, "");
    else if (v !== false && v != null) e[k] = v;
  }
  for (const c of children.flat()) if (c != null) e.append(c);
  return e;
}

function toast(msg, err = false) {
  toastEl.textContent = msg;
  toastEl.className = `toast show${err ? " err" : ""}`;
  clearTimeout(toast.t);
  toast.t = setTimeout(() => (toastEl.className = "toast"), 3500);
}

// Text input bound to obj[key].
function field(label, obj, key, { multi = false, placeholder = "" } = {}) {
  const input = h(multi ? "textarea" : "input", {
    value: obj[key] ?? "",
    placeholder,
    oninput: (e) => (obj[key] = e.target.value),
  });
  return h("label", { class: "field" }, h("span", {}, label), input);
}

function actionSelect(opt) {
  const sel = h("select", { onchange: (e) => (opt.action = e.target.value), "aria-label": "Click karne pe kya ho" });
  for (const [v, t] of ACTIONS) sel.append(h("option", { value: v, selected: opt.action === v }, t));
  return sel;
}

function optionsEditor(list) {
  const box = h("div");
  list.forEach((opt, i) => {
    box.append(
      h("div", { class: "opt-row" },
        h("input", { value: opt.label, placeholder: "Button ka text", oninput: (e) => (opt.label = e.target.value), "aria-label": "Button ka text" }),
        h("div", { class: "row2" },
          actionSelect(opt),
          h("button", {
            class: "icon-btn danger", type: "button", title: "Option hatao", disabled: list.length <= 1,
            onclick: () => { list.splice(i, 1); render(); },
          }, "🗑"),
        ),
      ),
    );
  });
  box.append(h("button", { class: "add", type: "button", onclick: () => { list.push({ label: "Naya option", action: "happy" }); render(); } }, "+ Option add karo"));
  return box;
}

function questionCard(q, i) {
  const qs = cfg.questions;
  const move = (d) => { [qs[i], qs[i + d]] = [qs[i + d], qs[i]]; render(); };
  return h("div", { class: "q-card" },
    h("div", { class: "q-head" },
      h("strong", {}, `Sawal ${i + 1}`),
      h("div", { class: "q-tools" },
        h("button", { class: "icon-btn", type: "button", title: "Upar", disabled: i === 0, onclick: () => move(-1) }, "↑"),
        h("button", { class: "icon-btn", type: "button", title: "Neeche", disabled: i === qs.length - 1, onclick: () => move(1) }, "↓"),
        h("button", {
          class: "icon-btn danger", type: "button", title: "Sawal hatao", disabled: qs.length <= 1,
          onclick: () => { if (confirm(`Sawal ${i + 1} delete karein?`)) { qs.splice(i, 1); render(); } },
        }, "🗑"),
      ),
    ),
    field("Sawal", q, "text", { multi: true }),
    h("h3", {}, "Options (buttons)"),
    optionsEditor(q.options),
    h("h3", {}, "'No' ke baad wali screen"),
    field("Message", q.retry, "text"),
    optionsEditor(q.retry.options),
    h("h3", {}, "Boy kya bolega"),
    field("Yes pe (happy boy) 💖", q, "happyMessage"),
    field("Final No pe (sad boy) 😢", q, "sadMessage"),
  );
}

let testing = false;
function songPanel() {
  const testBtn = h("button", {
    class: "chip", type: "button",
    onclick: () => {
      if (testing) { music.stop(); testing = false; testBtn.textContent = "▶ Song test karo"; return; }
      music.play(cfg.song.url.trim());
      testing = true;
      testBtn.textContent = "⏹ Stop";
    },
  }, "▶ Song test karo");
  return h("div", { class: "panel" },
    h("h2", {}, "🎵 Song"),
    h("p", { class: "hint" }, "Khaali chhodo to built-in happy tune bajegi. Apna song lagane ke liye mp3 ka direct link daalo, ya mp3 file GitHub repo ke 'music' folder mein upload karke yahan likho: music/song.mp3"),
    field("Song link (mp3)", cfg.song, "url", { placeholder: "music/song.mp3  ya  https://…/song.mp3" }),
    h("div", { class: "row" },
      testBtn,
      h("button", { class: "chip", type: "button", onclick: () => { cfg.song.url = ""; render(); toast("Default tune set ho gayi — Save dabana mat bhoolna"); } }, "Default tune"),
    ),
    h("h3", {}, "Awaaz"),
    h("label", { class: "toggle" },
      h("input", { type: "checkbox", checked: cfg.voice, onchange: (e) => (cfg.voice = e.target.checked) }),
      "Boy apna message bol ke sunaye (phone ki awaaz)",
    ),
  );
}

function render() {
  const notice = isFirebaseReady()
    ? null
    : h("div", { class: "notice" },
        h("strong", {}, "Preview mode: "),
        "Firebase abhi set nahi hai, isliye changes sirf isi phone/browser mein save honge. GF ke phone pe dikhane ke liye SETUP.md ke steps follow karo.",
      );

  root.replaceChildren(
    ...[notice,
      h("div", { class: "panel" },
        h("h2", {}, "💕 Basic"),
        field("Girlfriend ka naam", cfg, "herName"),
        h("p", { class: "hint" }, "Kisi bhi text mein {name} likhoge to wahan uska naam aa jayega."),
        h("h3", {}, "Pehli screen"),
        field("Title", cfg.intro, "title"),
        field("Message", cfg.intro, "text", { multi: true }),
        field("Button", cfg.intro, "button"),
      ),
      h("div", { class: "panel" },
        h("h2", {}, "❓ Sawal", h("span", { class: "hint", style: "margin:0" }, `${cfg.questions.length} total`)),
        ...cfg.questions.map(questionCard),
        h("button", { class: "add", type: "button", onclick: () => { cfg.questions.push(clone(DEFAULT_QUESTION)); cfg.questions.at(-1).text = "Naya sawal? 💭"; render(); } }, "+ Naya sawal add karo"),
      ),
      h("div", { class: "panel" },
        h("h2", {}, "🎉 Baaki text"),
        field("Yes ke baad title", cfg, "happyTitle"),
        field("Agle sawal ka button", cfg, "nextButton"),
        field("Aakhri message (sab sawal ke baad)", cfg, "finalMessage"),
        field("Phir se shuru button", cfg, "replayButton"),
        field("Sad screen ka button (wapas sawal pe)", cfg, "sadButton"),
      ),
      songPanel(),
      h("div", { class: "panel" },
        h("h2", {}, "⚙️ Reset"),
        h("button", { class: "chip", type: "button", onclick: () => { if (confirm("Sab kuch default pe reset karein?")) { cfg = normalize(null); render(); toast("Reset ho gaya — Save dabao to pakka ho jayega"); } } }, "Sab default pe reset karo"),
      ),
      h("div", { class: "savebar" }, saveBtn()),
    ].filter(Boolean),
  );
}

function saveBtn() {
  const b = h("button", { class: "btn primary", type: "button" }, "💾 Save karo");
  b.addEventListener("click", async () => {
    b.disabled = true;
    b.textContent = "Saving…";
    try {
      await saveConfig(cfg);
      toast(isFirebaseReady() ? "Save ho gaya ✅ GF ke app mein update ho gaya" : "Save ho gaya ✅ (sirf is browser mein)");
    } catch (e) {
      console.error(e);
      const denied = String(e?.code || e).includes("permission");
      toast(denied ? "Permission nahi mili ❌ firestore.rules mein apna email check karo" : "Save nahi hua ❌ Internet check karo", true);
    } finally {
      b.disabled = false;
      b.textContent = "💾 Save karo";
    }
  });
  return b;
}

function loginForm() {
  const state = { email: "", password: "" };
  const btn = h("button", { class: "btn primary", type: "submit" }, "Login 🔐");
  const form = h("form", {
    class: "panel login",
    onsubmit: async (e) => {
      e.preventDefault();
      btn.disabled = true;
      try {
        await adminSignIn(state.email.trim(), state.password);
      } catch (err) {
        console.error(err);
        toast("Email ya password galat hai ❌", true);
      } finally {
        btn.disabled = false;
      }
    },
  },
    h("h2", {}, "Sirf owner ke liye 🔒"),
    h("p", { class: "hint" }, "Apna Firebase wala email aur password daalo."),
    h("label", { class: "field" }, h("span", {}, "Email"), h("input", { type: "email", autocomplete: "username", required: true, oninput: (e) => (state.email = e.target.value) })),
    h("label", { class: "field" }, h("span", {}, "Password"), h("input", { type: "password", autocomplete: "current-password", required: true, oninput: (e) => (state.password = e.target.value) })),
    btn,
  );
  root.replaceChildren(form);
}

async function start() {
  if (!isFirebaseReady()) {
    cfg = await loadConfig();
    render();
    return;
  }
  logoutBtn.addEventListener("click", () => adminSignOut());
  try {
    await onAdminAuth(async (user) => {
      logoutBtn.hidden = !user;
      if (!user) return loginForm();
      root.replaceChildren(h("p", {}, "Loading…"));
      cfg = await loadConfig();
      render();
    });
  } catch (e) {
    console.error(e);
    root.replaceChildren(h("div", { class: "notice" }, "Firebase se connect nahi ho paya. Internet aur js/firebase-config.js check karo."));
  }
}

start();
