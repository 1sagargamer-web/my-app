import {
  normalize, loadDraft, saveDraft, makeShareLink,
  hasPassword, setPassword, checkPassword, resetAll, DEFAULT_QUESTION,
} from "./store.js";
import { Music } from "./music.js";

const root = document.getElementById("root");
const lockBtn = document.getElementById("lock");
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
      h("button", { class: "chip", type: "button", onclick: () => { cfg.song.url = ""; render(); toast("Default tune set ho gayi — Link banana mat bhoolna"); } }, "Default tune"),
    ),
    h("h3", {}, "Awaaz"),
    h("label", { class: "toggle" },
      h("input", { type: "checkbox", checked: cfg.voice, onchange: (e) => (cfg.voice = e.target.checked) }),
      "Boy apna message bol ke sunaye (phone ki awaaz)",
    ),
  );
}

const linkPanel = h("div", { class: "panel link-panel", hidden: true });

function render() {
  linkPanel.hidden = true;
  root.replaceChildren(
    ...[
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
        h("button", { class: "chip", type: "button", onclick: () => { if (confirm("Sab kuch default pe reset karein?")) { cfg = normalize(null); render(); toast("Reset ho gaya — Link banao to pakka ho jayega"); } } }, "Sab default pe reset karo"),
      ),
      linkPanel,
      h("div", { class: "savebar" }, saveBtn()),
    ],
  );
}

function saveBtn() {
  const b = h("button", { class: "btn primary", type: "button" }, "💌 Save karo + Link banao");
  b.addEventListener("click", async () => {
    b.disabled = true;
    try {
      saveDraft(cfg);
      showLink(await makeShareLink(cfg));
    } catch (e) {
      console.error(e);
      toast("Link nahi ban paya ❌", true);
    } finally {
      b.disabled = false;
    }
  });
  return b;
}

function showLink(url) {
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      toast("Link copy ho gaya ✅ ab GF ko bhej do");
    } catch {
      input.select();
      toast("Link select ho gaya, copy kar lo");
    }
  };
  const input = h("input", { value: url, readOnly: true, onclick: (e) => e.target.select(), "aria-label": "Link" });
  const wa = `https://wa.me/?text=${encodeURIComponent("Tumhare liye kuch special hai 💖 " + url)}`;
  linkPanel.replaceChildren(
    h("h2", {}, "✅ Link ready hai!"),
    h("p", { class: "hint" }, "Ye link GF ko bhejo. Baad mein kuch badloge to dobara 'Link banao' dabake naya link bhejna."),
    h("label", { class: "field" }, input),
    h("div", { class: "row" },
      h("button", { class: "chip", type: "button", onclick: copy }, "📋 Copy"),
      h("a", { class: "chip", href: wa, target: "_blank", rel: "noopener" }, "💬 WhatsApp"),
      h("a", { class: "chip", href: url, target: "_blank", rel: "noopener" }, "👀 Dekho"),
    ),
  );
  linkPanel.hidden = false;
  linkPanel.scrollIntoView({ behavior: "smooth", block: "center" });
  toast("Save ho gaya ✅ Link neeche hai");
}

function passwordForm() {
  const isNew = !hasPassword();
  const state = { p1: "", p2: "" };
  const form = h("form", {
    class: "panel login",
    onsubmit: async (e) => {
      e.preventDefault();
      if (isNew) {
        if (state.p1.length < 4) return toast("Password kam se kam 4 letter ka rakho", true);
        if (state.p1 !== state.p2) return toast("Dono password same nahi hain ❌", true);
        await setPassword(state.p1);
        toast("Password set ho gaya 🔐");
        return openEditor();
      }
      if (await checkPassword(state.p1)) return openEditor();
      toast("Galat password ❌", true);
    },
  },
    h("h2", {}, isNew ? "Apna password banao 🔐" : "Sirf owner ke liye 🔒"),
    h("p", { class: "hint" }, isNew
      ? "Ye password sirf aapke phone pe save hoga. Agli baar admin khologe to yahi maangega."
      : "Apna password daalo."),
    h("label", { class: "field" }, h("span", {}, "Password"),
      h("input", { type: "password", autocomplete: isNew ? "new-password" : "current-password", required: true, oninput: (e) => (state.p1 = e.target.value) })),
    isNew ? h("label", { class: "field" }, h("span", {}, "Password dobara"),
      h("input", { type: "password", autocomplete: "new-password", required: true, oninput: (e) => (state.p2 = e.target.value) })) : null,
    h("button", { class: "btn primary", type: "submit" }, isNew ? "Password set karo" : "Kholo 🔓"),
    isNew ? null : h("button", {
      class: "chip forgot", type: "button",
      onclick: () => {
        if (confirm("Password bhool gaye? Reset karne se password aur saved sawal dono hat jayenge (jo link pehle bheja hai wo chalta rahega).")) {
          resetAll();
          passwordForm();
        }
      },
    }, "Password bhool gaye?"),
  );
  root.replaceChildren(form);
}

function openEditor() {
  cfg = loadDraft();
  lockBtn.hidden = false;
  render();
}

lockBtn.addEventListener("click", () => {
  lockBtn.hidden = true;
  linkPanel.hidden = true;
  passwordForm();
});

passwordForm();
