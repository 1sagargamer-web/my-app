// No server: the owner's settings travel inside the link (after #d=).
// The owner's draft and password stay only on the owner's own phone.
import { DEFAULT_CONFIG, DEFAULT_QUESTION } from "./defaults.js";

export { DEFAULT_CONFIG, DEFAULT_QUESTION };

const DRAFT_KEY = "loveAppDraft";
const PASS_KEY = "loveAppPass";
const ACTIONS = ["happy", "retry", "sad"];

const clone = (v) => JSON.parse(JSON.stringify(v));
const str = (v, fallback) => (typeof v === "string" ? v : fallback);

function normOptions(list, fallback) {
  if (!Array.isArray(list) || !list.length) return clone(fallback);
  return list.map((o) => ({
    label: str(o?.label, "Option"),
    action: ACTIONS.includes(o?.action) ? o.action : "happy",
  }));
}

function normQuestion(q) {
  const d = DEFAULT_QUESTION;
  return {
    text: str(q?.text, d.text),
    options: normOptions(q?.options, d.options),
    retry: {
      text: str(q?.retry?.text, d.retry.text),
      options: normOptions(q?.retry?.options, d.retry.options),
    },
    happyMessage: str(q?.happyMessage, d.happyMessage),
    sadMessage: str(q?.sadMessage, d.sadMessage),
  };
}

// Fills any missing fields with defaults so old/partial data never breaks the app.
export function normalize(raw) {
  const d = clone(DEFAULT_CONFIG);
  if (!raw || typeof raw !== "object") return d;
  const c = { ...d };
  for (const k of ["herName", "happyTitle", "nextButton", "finalMessage", "replayButton", "sadButton"]) {
    c[k] = str(raw[k], d[k]);
  }
  c.intro = {
    title: str(raw.intro?.title, d.intro.title),
    text: str(raw.intro?.text, d.intro.text),
    button: str(raw.intro?.button, d.intro.button),
  };
  c.song = { url: str(raw.song?.url, "").trim() };
  c.voice = typeof raw.voice === "boolean" ? raw.voice : d.voice;
  c.questions =
    Array.isArray(raw.questions) && raw.questions.length ? raw.questions.map(normQuestion) : d.questions;
  return c;
}

// ---------- link encoding ----------

function toB64url(bytes) {
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromB64url(s) {
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

const pipe = async (bytes, stream) => new Uint8Array(await new Response(new Blob([bytes]).stream().pipeThrough(stream)).arrayBuffer());

export async function encodeConfig(cfg) {
  const bytes = new TextEncoder().encode(JSON.stringify(normalize(cfg)));
  if ("CompressionStream" in window) return "z" + toB64url(await pipe(bytes, new CompressionStream("deflate-raw")));
  return "j" + toB64url(bytes);
}

export async function decodeConfig(s) {
  let bytes = fromB64url(s.slice(1));
  if (s[0] === "z") bytes = await pipe(bytes, new DecompressionStream("deflate-raw"));
  return normalize(JSON.parse(new TextDecoder().decode(bytes)));
}

export async function makeShareLink(cfg) {
  const url = new URL("./", location.href);
  url.hash = "d=" + (await encodeConfig(cfg));
  return url.toString();
}

// For the girlfriend's app: read settings from the link.
export async function loadConfig() {
  const m = /[#&]d=([^&]+)/.exec(location.hash);
  if (m) {
    try {
      return await decodeConfig(m[1]);
    } catch (e) {
      console.warn("Bad link data, using defaults", e);
    }
  }
  return normalize(null);
}

// ---------- owner's draft + password (only on the owner's phone) ----------

const read = (k) => { try { return localStorage.getItem(k); } catch { return null; } };
const write = (k, v) => { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch {} };

export function loadDraft() {
  try { return normalize(JSON.parse(read(DRAFT_KEY))); } catch { return normalize(null); }
}
export const saveDraft = (cfg) => write(DRAFT_KEY, JSON.stringify(normalize(cfg)));

async function hash(text) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode("love-app:" + text));
  return toB64url(new Uint8Array(buf));
}
export const hasPassword = () => Boolean(read(PASS_KEY));
export const setPassword = async (p) => write(PASS_KEY, await hash(p));
export const checkPassword = async (p) => (await hash(p)) === read(PASS_KEY);
export function resetAll() {
  write(PASS_KEY, null);
  write(DRAFT_KEY, null);
}
