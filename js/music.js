// Plays the owner's song (mp3 link) or, if none is set, a built-in happy tune
// made with the Web Audio API.

const EIGHTH = 0.25; // seconds (120 bpm)

// [note, length in eighths]. "R" = rest. 8 bars of 8 eighths.
const MELODY = [
  ["E5", 1], ["G5", 1], ["C6", 2], ["B5", 1], ["A5", 1], ["G5", 2],
  ["D5", 1], ["G5", 1], ["B5", 2], ["A5", 1], ["G5", 1], ["D5", 2],
  ["C5", 1], ["E5", 1], ["A5", 2], ["G5", 1], ["E5", 1], ["C5", 2],
  ["F5", 1], ["A5", 1], ["C6", 1], ["A5", 1], ["G5", 2], ["R", 2],
  ["E5", 1], ["E5", 1], ["G5", 1], ["C6", 1], ["D6", 2], ["C6", 2],
  ["B5", 1], ["A5", 1], ["G5", 1], ["B5", 1], ["D6", 2], ["B5", 2],
  ["C6", 1], ["B5", 1], ["A5", 1], ["E5", 1], ["A5", 2], ["G5", 2],
  ["F5", 1], ["G5", 1], ["A5", 1], ["B5", 1], ["C6", 4],
];
const CHORDS = [
  ["C4", "E4", "G4"], ["B3", "D4", "G4"], ["C4", "E4", "A4"], ["C4", "F4", "A4"],
  ["C4", "E4", "G4"], ["B3", "D4", "G4"], ["C4", "E4", "A4"], ["C4", "F4", "A4"],
];
const BASS = ["C3", "G2", "A2", "F2", "C3", "G2", "A2", "F2"];
const LOOP = MELODY.reduce((s, [, l]) => s + l, 0) * EIGHTH;

const SEMI = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
function freq(note) {
  const m = /^([A-G])(#?)(\d)$/.exec(note);
  const midi = 12 * (Number(m[3]) + 1) + SEMI[m[1]] + (m[2] ? 1 : 0);
  return 440 * 2 ** ((midi - 69) / 12);
}

export class Music {
  constructor() {
    this.ctx = null;
    this.audio = null;
    this.master = null;
    this.playing = false;
    this.muted = false;
    this.timer = null;
  }

  // Must be called from a tap/click so phones allow sound.
  unlock() {
    if (!this.ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (AC) this.ctx = new AC();
    }
    if (this.ctx && this.ctx.state === "suspended") this.ctx.resume();
  }

  play(url) {
    this.stop();
    this.playing = true;
    this.unlock();
    if (url) {
      const audio = new Audio(url);
      audio.loop = true;
      audio.muted = this.muted;
      this.audio = audio;
      const fallback = () => {
        if (this.playing && this.audio === audio) {
          this.audio = null;
          this.playTune();
        }
      };
      audio.addEventListener("error", fallback);
      audio.play().catch(fallback);
      return;
    }
    this.playTune();
  }

  playTune() {
    if (!this.ctx) return;
    const ctx = this.ctx;
    this.master = ctx.createGain();
    this.master.gain.value = this.muted ? 0 : 0.55;
    this.master.connect(ctx.destination);
    this.noise = this.noise || this.makeNoise();
    this.schedule(ctx.currentTime + 0.05);
  }

  schedule(t0) {
    const out = this.master;
    let t = t0;
    for (const [n, len] of MELODY) {
      if (n !== "R") this.bell(out, freq(n), t, len * EIGHTH);
      t += len * EIGHTH;
    }
    for (let bar = 0; bar < 8; bar++) {
      const bt = t0 + bar * 8 * EIGHTH;
      for (const n of CHORDS[bar]) this.pad(out, freq(n), bt, 8 * EIGHTH);
      for (const step of [0, 3, 4, 7]) this.bass(out, freq(BASS[bar]) * (step === 7 ? 2 : 1), bt + step * EIGHTH);
      for (const beat of [0, 2, 4, 6]) this.kick(out, bt + beat * EIGHTH);
      for (const beat of [2, 6]) this.clap(out, bt + beat * EIGHTH);
    }
    const wait = (t0 + LOOP - this.ctx.currentTime - 0.6) * 1000;
    this.timer = setTimeout(() => this.playing && this.master === out && this.schedule(t0 + LOOP), wait);
  }

  env(out, start, attack, peak, end) {
    const g = this.ctx.createGain();
    g.gain.setValueAtTime(0.0001, start);
    g.gain.exponentialRampToValueAtTime(peak, start + attack);
    g.gain.exponentialRampToValueAtTime(0.0001, end);
    g.connect(out);
    return g;
  }

  osc(type, f, dest, start, stop) {
    const o = this.ctx.createOscillator();
    o.type = type;
    o.frequency.setValueAtTime(f, start);
    o.connect(dest);
    o.start(start);
    o.stop(stop + 0.05);
    return o;
  }

  bell(out, f, t, len) {
    const end = t + Math.max(len, 0.3) + 0.25;
    const g = this.env(out, t, 0.01, 0.28, end);
    this.osc("triangle", f, g, t, end);
    const g2 = this.env(out, t, 0.005, 0.06, t + 0.4);
    this.osc("sine", f * 2, g2, t, t + 0.4);
  }

  pad(out, f, t, len) {
    const g = this.env(out, t, 0.15, 0.035, t + len);
    this.osc("sine", f, g, t, t + len);
  }

  bass(out, f, t) {
    const g = this.env(out, t, 0.01, 0.22, t + EIGHTH * 0.9);
    this.osc("triangle", f, g, t, t + EIGHTH);
  }

  kick(out, t) {
    const g = this.env(out, t, 0.005, 0.5, t + 0.18);
    const o = this.osc("sine", 140, g, t, t + 0.2);
    o.frequency.exponentialRampToValueAtTime(45, t + 0.15);
  }

  clap(out, t) {
    const src = this.ctx.createBufferSource();
    src.buffer = this.noise;
    const bp = this.ctx.createBiquadFilter();
    bp.type = "bandpass";
    bp.frequency.value = 1500;
    const g = this.env(out, t, 0.003, 0.18, t + 0.15);
    src.connect(bp).connect(g);
    src.start(t);
    src.stop(t + 0.2);
  }

  makeNoise() {
    const b = this.ctx.createBuffer(1, this.ctx.sampleRate * 0.3, this.ctx.sampleRate);
    const d = b.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    return b;
  }

  // Lower the volume a bit while the boy is speaking.
  duck(on) {
    if (this.audio) this.audio.volume = on ? 0.35 : 1;
    if (this.master && !this.muted) {
      const t = this.ctx.currentTime;
      this.master.gain.cancelScheduledValues(t);
      this.master.gain.setTargetAtTime(on ? 0.2 : 0.55, t, 0.1);
    }
  }

  setMuted(m) {
    this.muted = m;
    if (this.audio) this.audio.muted = m;
    if (this.master) this.master.gain.value = m ? 0 : 0.55;
  }

  stop() {
    this.playing = false;
    clearTimeout(this.timer);
    if (this.audio) {
      this.audio.pause();
      this.audio = null;
    }
    if (this.master) {
      const m = this.master;
      this.master = null;
      const t = this.ctx.currentTime;
      m.gain.cancelScheduledValues(t);
      m.gain.setTargetAtTime(0, t, 0.08);
      setTimeout(() => m.disconnect(), 600);
    }
  }
}
