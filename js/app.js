import { loadConfig } from "./store.js";
import { createBoy } from "./boy.js";
import { Music } from "./music.js";

const stage = document.getElementById("stage");
const muteBtn = document.getElementById("muteBtn");
const music = new Music();
let cfg;
let qi = 0;

const fill = (t) => (t || "").replaceAll("{name}", cfg.herName || "");

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = fill(text);
  return e;
}

function button(text, cls, onClick) {
  const b = el("button", `btn ${cls}`, text);
  b.type = "button";
  b.addEventListener("click", onClick);
  return b;
}

function show(...children) {
  const screen = el("section", "screen");
  const card = el("div", "card");
  card.append(...children);
  screen.append(card);
  stage.replaceChildren(screen);
  requestAnimationFrame(() => requestAnimationFrame(() => screen.classList.add("in")));
  window.scrollTo({ top: 0 });
}

function boy(state, bubble) {
  const wrap = el("div", `boy-wrap ${state}`);
  if (bubble) wrap.append(el("div", "bubble", bubble));
  wrap.append(createBoy(state));
  return wrap;
}

function progress() {
  if (cfg.questions.length < 2) return el("div");
  const p = el("div", "dots");
  cfg.questions.forEach((_, i) => p.append(el("span", i === qi ? "on" : i < qi ? "done" : "")));
  return p;
}

function options(list) {
  const box = el("div", "options");
  list.forEach((o, i) => {
    const style = o.action === "happy" ? "primary" : "ghost";
    box.append(button(o.label, `${style} ${i === 0 && o.action === "happy" ? "pulse" : ""}`, () => act(o.action)));
  });
  return box;
}

function act(action) {
  if (action === "happy") return happy();
  if (action === "retry") return retry();
  return sad();
}

// ---------- screens ----------

function intro() {
  music.stop();
  qi = 0;
  show(
    el("div", "badge", "💌"),
    el("h1", "title", cfg.intro.title),
    el("p", "sub", cfg.intro.text),
    button(cfg.intro.button, "primary pulse", () => {
      music.unlock();
      question(0);
    }),
  );
}

function question(i) {
  music.stop();
  qi = i;
  const q = cfg.questions[i];
  show(progress(), boy("idle", "🥺"), el("h2", "question", q.text), options(q.options));
}

function retry() {
  const q = cfg.questions[qi];
  show(progress(), boy("idle", "🥹"), el("h2", "question", q.retry.text), options(q.retry.options));
}

function happy() {
  const q = cfg.questions[qi];
  music.play(cfg.song.url);
  burst();
  const last = qi >= cfg.questions.length - 1;
  show(
    el("h1", "title glow", cfg.happyTitle),
    boy("happy", q.happyMessage),
    last ? el("p", "sub final", cfg.finalMessage) : el("div"),
    last
      ? button(cfg.replayButton, "ghost", intro)
      : button(cfg.nextButton, "primary", () => question(qi + 1)),
  );
  speak(q.happyMessage);
}

function sad() {
  music.stop();
  const q = cfg.questions[qi];
  show(boy("sad", q.sadMessage), button(cfg.sadButton, "primary", () => question(qi)));
}

// ---------- effects ----------

function speak(text) {
  if (!cfg.voice || !("speechSynthesis" in window)) return;
  const clean = fill(text).replace(/[\p{Extended_Pictographic}\u{FE0F}\u{200D}]/gu, "").trim();
  if (!clean) return;
  speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(clean);
  const voices = speechSynthesis.getVoices();
  u.voice = voices.find((v) => v.lang === "en-IN") || voices.find((v) => v.lang.startsWith("en")) || null;
  u.rate = 0.95;
  u.pitch = 1.15;
  u.onstart = () => music.duck(true);
  u.onend = () => music.duck(false);
  setTimeout(() => speechSynthesis.speak(u), 500);
}

const HEARTS = ["💖", "💕", "💗", "💘", "❤️", "🌹", "✨", "💞"];
const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;

function burst() {
  const layer = document.getElementById("fx");
  const n = reduceMotion ? 10 : 46;
  for (let i = 0; i < n; i++) {
    const h = el("span", "burst");
    h.textContent = HEARTS[i % HEARTS.length];
    const ang = Math.random() * Math.PI * 2;
    const dist = 120 + Math.random() * 260;
    h.style.setProperty("--dx", `${Math.cos(ang) * dist}px`);
    h.style.setProperty("--dy", `${Math.sin(ang) * dist - 80}px`);
    h.style.setProperty("--r", `${Math.random() * 120 - 60}deg`);
    h.style.setProperty("--s", (0.8 + Math.random() * 1.2).toFixed(2));
    h.style.animationDelay = `${Math.random() * 0.25}s`;
    h.addEventListener("animationend", () => h.remove());
    layer.append(h);
  }
}

function floatingHearts() {
  const bg = document.getElementById("bg");
  const spawn = () => {
    const h = document.createElement("span");
    h.className = "float";
    h.textContent = HEARTS[Math.floor(Math.random() * 5)];
    h.style.left = `${Math.random() * 100}%`;
    h.style.fontSize = `${12 + Math.random() * 22}px`;
    h.style.animationDuration = `${8 + Math.random() * 8}s`;
    h.style.opacity = (0.25 + Math.random() * 0.45).toFixed(2);
    h.addEventListener("animationend", () => h.remove());
    bg.append(h);
  };
  for (let i = 0; i < 6; i++) setTimeout(spawn, i * 300);
  setInterval(spawn, reduceMotion ? 3000 : 900);
}

muteBtn.addEventListener("click", () => {
  music.setMuted(!music.muted);
  muteBtn.textContent = music.muted ? "🔇" : "🔊";
});

// ---------- start ----------

floatingHearts();
loadConfig().then((c) => {
  cfg = c;
  document.title = fill(cfg.intro.title).replace(/[\p{Extended_Pictographic}\u{FE0F}]/gu, "").trim() || "For You";
  intro();
});
