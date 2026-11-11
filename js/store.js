import { DEFAULT_CONFIG, DEFAULT_QUESTION } from "./defaults.js";
import { firebaseConfig } from "./firebase-config.js";

export { DEFAULT_CONFIG, DEFAULT_QUESTION };

const SDK = "https://www.gstatic.com/firebasejs/10.12.2";
const PREVIEW_KEY = "loveAppPreview";
const ACTIONS = ["happy", "retry", "sad"];

export const isFirebaseReady = () =>
  Boolean(firebaseConfig.apiKey && !firebaseConfig.apiKey.startsWith("PASTE") && firebaseConfig.projectId);

const clone = (v) => JSON.parse(JSON.stringify(v));
const str = (v, fallback) => (typeof v === "string" ? v : fallback);

function normOption(o, fallback) {
  return {
    label: str(o?.label, fallback?.label ?? "Option"),
    action: ACTIONS.includes(o?.action) ? o.action : fallback?.action ?? "happy",
  };
}

function normOptions(list, fallback) {
  if (!Array.isArray(list) || !list.length) return clone(fallback);
  return list.map((o) => normOption(o));
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

// ---------- Firebase (loaded only when configured) ----------

let core;
async function fbCore() {
  if (!core) {
    core = (async () => {
      const [{ initializeApp }, fs] = await Promise.all([
        import(`${SDK}/firebase-app.js`),
        import(`${SDK}/firebase-firestore.js`),
      ]);
      const app = initializeApp(firebaseConfig);
      const db = fs.getFirestore(app);
      return { app, db, fs, ref: fs.doc(db, "loveApp", "config") };
    })();
  }
  return core;
}

let auth;
async function fbAuth() {
  if (!auth) {
    auth = (async () => {
      const { app } = await fbCore();
      const a = await import(`${SDK}/firebase-auth.js`);
      return { a, auth: a.getAuth(app) };
    })();
  }
  return auth;
}

const withTimeout = (p, ms) =>
  Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error("timeout")), ms))]);

export async function loadConfig() {
  if (!isFirebaseReady()) {
    try {
      return normalize(JSON.parse(localStorage.getItem(PREVIEW_KEY)));
    } catch {
      return normalize(null);
    }
  }
  try {
    const { fs, ref } = await withTimeout(fbCore(), 10000);
    const snap = await withTimeout(fs.getDoc(ref), 10000);
    return normalize(snap.exists() ? snap.data() : null);
  } catch (e) {
    console.warn("Could not load from Firebase, using defaults", e);
    return normalize(null);
  }
}

export async function saveConfig(cfg) {
  const data = normalize(cfg);
  if (!isFirebaseReady()) {
    localStorage.setItem(PREVIEW_KEY, JSON.stringify(data));
    return;
  }
  const { fs, ref } = await fbCore();
  await fs.setDoc(ref, { ...data, updatedAt: fs.serverTimestamp() });
}

export async function adminSignIn(email, password) {
  const { a, auth } = await fbAuth();
  return a.signInWithEmailAndPassword(auth, email, password);
}

export async function adminSignOut() {
  const { a, auth } = await fbAuth();
  return a.signOut(auth);
}

export async function onAdminAuth(cb) {
  const { a, auth } = await fbAuth();
  return a.onAuthStateChanged(auth, cb);
}
