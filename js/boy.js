// Cute cartoon boy drawn in SVG. States: "idle" (hopeful), "happy", "sad".
let uid = 0;

export function createBoy(state = "idle") {
  const id = `boy${++uid}`;
  const wrap = document.createElement("div");
  wrap.className = "boy-figure";
  wrap.innerHTML = `
<svg class="boy ${state}" viewBox="0 0 220 300" role="img" aria-label="Boy character">
  <defs>
    <linearGradient id="${id}-shirt" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#ff5c8a"/>
      <stop offset="1" stop-color="#d6336c"/>
    </linearGradient>
    <radialGradient id="${id}-face" cx="0.45" cy="0.4" r="0.7">
      <stop offset="0" stop-color="#ffe6d2"/>
      <stop offset="1" stop-color="#f9c9a3"/>
    </radialGradient>
  </defs>

  <ellipse class="shadow" cx="110" cy="292" rx="58" ry="7" fill="#000" opacity=".25"/>

  <g class="body">
    <!-- legs -->
    <rect x="84" y="236" width="20" height="44" rx="10" fill="#34386b"/>
    <rect x="116" y="236" width="20" height="44" rx="10" fill="#34386b"/>
    <ellipse cx="92" cy="283" rx="16" ry="8" fill="#fff"/>
    <ellipse cx="128" cy="283" rx="16" ry="8" fill="#fff"/>

    <!-- arms (behind torso) -->
    <g class="arm arm-left">
      <line x1="74" y1="178" x2="54" y2="230" stroke="url(#${id}-shirt)" stroke-width="20" stroke-linecap="round"/>
      <circle cx="52" cy="236" r="11" fill="#f9c9a3"/>
    </g>
    <g class="arm arm-right">
      <line x1="146" y1="178" x2="166" y2="230" stroke="url(#${id}-shirt)" stroke-width="20" stroke-linecap="round"/>
      <circle cx="168" cy="236" r="11" fill="#f9c9a3"/>
    </g>

    <!-- torso -->
    <rect x="64" y="160" width="92" height="88" rx="32" fill="url(#${id}-shirt)"/>
    <path class="chest-heart" d="M110 222 C92 208 92 192 102 190 C107 189 110 193 110 196 C110 193 113 189 118 190 C128 192 128 208 110 222 Z" fill="#fff" opacity=".95"/>
    <rect x="100" y="146" width="20" height="18" rx="6" fill="#f2bb93"/>
  </g>

  <g class="head">
    <circle cx="52" cy="106" r="12" fill="#f9c9a3"/>
    <circle cx="168" cy="106" r="12" fill="#f9c9a3"/>
    <circle cx="110" cy="100" r="58" fill="url(#${id}-face)"/>

    <!-- hair -->
    <path d="M52 104 C44 44 88 26 114 28 C152 30 178 54 168 104 C162 84 150 72 134 66 C124 80 98 82 80 72 C68 80 58 90 52 104 Z" fill="#3a2418"/>
    <path d="M104 30 C112 14 128 14 134 22 C124 22 116 26 112 34 Z" fill="#3a2418"/>

    <!-- blush -->
    <ellipse class="blush" cx="76" cy="126" rx="11" ry="6.5" fill="#ff7fa3"/>
    <ellipse class="blush" cx="144" cy="126" rx="11" ry="6.5" fill="#ff7fa3"/>

    <!-- idle: hopeful round eyes -->
    <g class="st st-idle">
      <circle cx="88" cy="108" r="9" fill="#2b1a12"/><circle cx="91" cy="104" r="3.4" fill="#fff"/>
      <circle cx="132" cy="108" r="9" fill="#2b1a12"/><circle cx="135" cy="104" r="3.4" fill="#fff"/>
      <path d="M100 130 Q110 138 120 130" stroke="#7a2a2a" stroke-width="4" fill="none" stroke-linecap="round"/>
    </g>

    <!-- happy: ^ ^ eyes + big smile -->
    <g class="st st-happy">
      <path d="M77 111 Q88 97 99 111" stroke="#2b1a12" stroke-width="5" fill="none" stroke-linecap="round"/>
      <path d="M121 111 Q132 97 143 111" stroke="#2b1a12" stroke-width="5" fill="none" stroke-linecap="round"/>
      <path d="M92 124 Q110 152 128 124 Z" fill="#8a1c3a"/>
      <ellipse cx="110" cy="137" rx="8" ry="4.5" fill="#ff7c9c"/>
    </g>

    <!-- sad: worried brows, teary eyes, frown -->
    <g class="st st-sad">
      <path d="M77 96 L98 90" stroke="#3a2418" stroke-width="4" stroke-linecap="round"/>
      <path d="M122 90 L143 96" stroke="#3a2418" stroke-width="4" stroke-linecap="round"/>
      <circle cx="88" cy="110" r="8" fill="#2b1a12"/><circle cx="90" cy="107" r="3" fill="#fff"/>
      <circle cx="132" cy="110" r="8" fill="#2b1a12"/><circle cx="134" cy="107" r="3" fill="#fff"/>
      <path d="M98 140 Q110 128 122 140" stroke="#7a2a2a" stroke-width="4" fill="none" stroke-linecap="round"/>
      <path class="tear t1" d="M84 120 C80 128 80 132 84 134 C88 132 88 128 84 120 Z" fill="#7cc7ff"/>
      <path class="tear t2" d="M136 120 C132 128 132 132 136 134 C140 132 140 128 136 120 Z" fill="#7cc7ff"/>
    </g>
  </g>
</svg>`;
  return wrap;
}
