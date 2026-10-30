// Generates CineVerse's original demo artwork (posters, backdrops, stills, event art, food, offers) as SVG.
// Everything here is abstract, procedurally drawn and fictional — no third-party artwork.
import { mkdirSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "public", "media");
const out = (path, svg) => {
  mkdirSync(dirname(join(root, path)), { recursive: true });
  writeFileSync(join(root, path), svg.trim() + "\n");
};

function rng(seed) {
  let s = 0;
  for (const c of seed) s = (s * 31 + c.charCodeAt(0)) >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

const esc = (t) => t.replace(/&/g, "&amp;").replace(/</g, "&lt;");

const MOVIES = [
  { slug: "galactic-dawn", title: "GALACTIC DAWN", tag: "The last sunrise belongs to the brave.", c: ["#0b0630", "#3a0f6b", "#e0467c", "#ffb86b"], motif: "planet" },
  { slug: "midnight-protocol", title: "MIDNIGHT PROTOCOL", tag: "Trust no signal after 12.", c: ["#03040c", "#0a1b44", "#1f6bff", "#7ee0ff"], motif: "grid" },
  { slug: "veera", title: "VEERA", tag: "One village. One promise.", c: ["#2a0707", "#8e1b12", "#f06a1d", "#ffd27a"], motif: "sun" },
  { slug: "the-last-signal", title: "THE LAST SIGNAL", tag: "Someone is still transmitting.", c: ["#0d1117", "#1c2b3a", "#6aa6c8", "#e8f4fb"], motif: "dish" },
  { slug: "neon-city", title: "NEON CITY", tag: "The city never sleeps.", c: ["#120322", "#3c0a5e", "#ff3ea5", "#6ef2ff"], motif: "skyline" },
  { slug: "monsoon-letters", title: "MONSOON LETTERS", tag: "Some letters take a lifetime.", c: ["#062522", "#0f4d47", "#3fb59f", "#e4f7ef"], motif: "rain" },
  { slug: "kaveri-express", title: "KAVERI EXPRESS", tag: "Next stop: absolute chaos.", c: ["#3b1a00", "#c2410c", "#fbbf24", "#fff7d6"], motif: "train" },
  { slug: "iron-tide", title: "IRON TIDE", tag: "The sea keeps its secrets.", c: ["#030b17", "#0c2a4a", "#3d7fb8", "#c9e4ff"], motif: "waves" },
  { slug: "whispers-of-hampi", title: "WHISPERS OF HAMPI", tag: "History remembers.", c: ["#2b1407", "#7a3b17", "#d9894a", "#f8dcb4"], motif: "arches" },
  { slug: "orbit-kids", title: "ORBIT KIDS", tag: "Small crew. Giant leap.", c: ["#0a2a55", "#1e6fd9", "#5ee6c8", "#fff6c2"], motif: "rocket" },
];

function motif(kind, w, h, c, r) {
  const cx = w / 2;
  switch (kind) {
    case "planet":
      return `<circle cx="${cx}" cy="${h * 0.42}" r="${w * 0.3}" fill="url(#glow)"/>
        <ellipse cx="${cx}" cy="${h * 0.42}" rx="${w * 0.48}" ry="${w * 0.08}" fill="none" stroke="${c[3]}" stroke-opacity=".7" stroke-width="3" transform="rotate(-14 ${cx} ${h * 0.42})"/>
        <path d="M0 ${h * 0.78} Q ${cx} ${h * 0.62} ${w} ${h * 0.78} L ${w} ${h} L 0 ${h}Z" fill="${c[0]}" opacity=".9"/>`;
    case "grid": {
      let g = "";
      for (let i = 0; i <= 12; i++) g += `<line x1="${(i * w) / 12}" y1="${h * 0.55}" x2="${cx + (i - 6) * w * 0.35}" y2="${h}" stroke="${c[2]}" stroke-opacity=".45"/>`;
      for (let j = 0; j < 8; j++) g += `<line x1="0" y1="${h * 0.55 + (j * j * h) / 140}" x2="${w}" y2="${h * 0.55 + (j * j * h) / 140}" stroke="${c[2]}" stroke-opacity=".35"/>`;
      let bits = "";
      for (let k = 0; k < 40; k++) bits += `<text x="${r() * w}" y="${r() * h * 0.5}" fill="${c[3]}" fill-opacity="${0.15 + r() * 0.4}" font-family="monospace" font-size="${10 + r() * 10}">${r() > 0.5 ? 1 : 0}</text>`;
      return g + bits + `<circle cx="${cx}" cy="${h * 0.34}" r="${w * 0.12}" fill="none" stroke="${c[3]}" stroke-width="3"/><circle cx="${cx}" cy="${h * 0.34}" r="${w * 0.04}" fill="${c[3]}"/>`;
    }
    case "sun":
      return `<circle cx="${cx}" cy="${h * 0.4}" r="${w * 0.26}" fill="url(#glow)"/>
        ${Array.from({ length: 5 }, (_, i) => `<path d="M0 ${h * (0.62 + i * 0.07)} Q ${w * 0.25} ${h * (0.58 + i * 0.07)} ${cx} ${h * (0.62 + i * 0.07)} T ${w} ${h * (0.62 + i * 0.07)}" stroke="${c[3]}" stroke-opacity="${0.6 - i * 0.1}" stroke-width="3" fill="none"/>`).join("")}`;
    case "dish":
      return `<path d="M${cx - w * 0.22} ${h * 0.5} A ${w * 0.25} ${w * 0.25} 0 0 1 ${cx + w * 0.22} ${h * 0.34}" fill="none" stroke="${c[3]}" stroke-width="6"/>
        <line x1="${cx}" y1="${h * 0.44}" x2="${cx - w * 0.05}" y2="${h * 0.66}" stroke="${c[3]}" stroke-width="5"/>
        ${[1, 2, 3].map((i) => `<path d="M${cx + w * 0.12 + i * 18} ${h * 0.26 - i * 10} a ${i * 22} ${i * 22} 0 0 1 ${i * 14} ${i * 28}" stroke="${c[2]}" stroke-width="3" fill="none" opacity="${1 - i * 0.25}"/>`).join("")}
        <path d="M0 ${h * 0.7} L ${w * 0.3} ${h * 0.55} L ${w * 0.55} ${h * 0.68} L ${w * 0.8} ${h * 0.5} L ${w} ${h * 0.62} L ${w} ${h} L 0 ${h}Z" fill="${c[3]}" fill-opacity=".85"/>`;
    case "skyline": {
      let b = "";
      let x = 0;
      while (x < w) {
        const bw = 20 + r() * 50, bh = h * (0.2 + r() * 0.35);
        b += `<rect x="${x}" y="${h - bh}" width="${bw}" height="${bh}" fill="${c[0]}"/>`;
        for (let k = 0; k < 6; k++) b += `<rect x="${x + 4 + r() * (bw - 10)}" y="${h - bh + 8 + r() * (bh - 20)}" width="4" height="6" fill="${r() > 0.5 ? c[2] : c[3]}" opacity=".8"/>`;
        x += bw + 2;
      }
      let rain = "";
      for (let k = 0; k < 60; k++) { const rx = r() * w, ry = r() * h; rain += `<line x1="${rx}" y1="${ry}" x2="${rx - 6}" y2="${ry + 22}" stroke="${c[3]}" stroke-opacity=".25"/>`; }
      return `<circle cx="${w * 0.7}" cy="${h * 0.25}" r="${w * 0.14}" fill="${c[2]}" opacity=".55"/>` + rain + b;
    }
    case "rain": {
      let d = "";
      for (let k = 0; k < 70; k++) { const rx = r() * w, ry = r() * h; d += `<line x1="${rx}" y1="${ry}" x2="${rx}" y2="${ry + 16}" stroke="${c[3]}" stroke-opacity="${0.2 + r() * 0.4}" stroke-width="2" stroke-linecap="round"/>`; }
      return d + `<g transform="translate(${cx - w * 0.2} ${h * 0.36}) rotate(-8)"><rect width="${w * 0.4}" height="${w * 0.27}" rx="6" fill="${c[3]}"/><path d="M0 0 L ${w * 0.2} ${w * 0.15} L ${w * 0.4} 0" fill="none" stroke="${c[1]}" stroke-width="4"/></g>`;
    }
    case "train":
      return Array.from({ length: 7 }, (_, i) => `<rect x="-20" y="${h * 0.3 + i * 34}" width="${w + 40}" height="16" fill="${i % 2 ? c[3] : c[2]}" opacity="${0.85 - i * 0.08}" transform="rotate(-12 ${cx} ${h / 2})"/>`).join("") +
        `<circle cx="${w * 0.78}" cy="${h * 0.2}" r="${w * 0.1}" fill="${c[3]}"/>`;
    case "waves":
      return Array.from({ length: 6 }, (_, i) => `<path d="M0 ${h * (0.5 + i * 0.08)} C ${w * 0.25} ${h * (0.44 + i * 0.08)} ${w * 0.5} ${h * (0.56 + i * 0.08)} ${w} ${h * (0.48 + i * 0.08)} L ${w} ${h} L 0 ${h}Z" fill="${c[i % 2 ? 1 : 2]}" opacity="${0.35 + i * 0.1}"/>`).join("") +
        `<path d="M${w * 0.32} ${h * 0.46} l ${w * 0.3} 0 l -${w * 0.04} ${h * 0.05} l -${w * 0.22} 0 z M${w * 0.44} ${h * 0.46} l 0 -${h * 0.12} l ${w * 0.08} ${h * 0.12}" fill="${c[0]}"/>`;
    case "arches":
      return Array.from({ length: 4 }, (_, i) => `<path d="M${w * 0.08 + i * w * 0.22} ${h} L ${w * 0.08 + i * w * 0.22} ${h * 0.55} Q ${w * 0.17 + i * w * 0.22} ${h * 0.42} ${w * 0.26 + i * w * 0.22} ${h * 0.55} L ${w * 0.26 + i * w * 0.22} ${h}" fill="none" stroke="${c[3]}" stroke-width="7" opacity="${0.9 - i * 0.12}"/>`).join("") +
        `<circle cx="${cx}" cy="${h * 0.26}" r="${w * 0.12}" fill="url(#glow)"/>`;
    case "rocket":
      return `<ellipse cx="${cx}" cy="${h * 0.42}" rx="${w * 0.42}" ry="${w * 0.16}" fill="none" stroke="${c[3]}" stroke-dasharray="6 8" stroke-width="3" transform="rotate(-20 ${cx} ${h * 0.42})"/>
        <g transform="translate(${cx} ${h * 0.42}) rotate(35)"><path d="M0 -70 C 22 -40 22 20 14 40 L -14 40 C -22 20 -22 -40 0 -70Z" fill="${c[3]}"/><circle cy="-18" r="9" fill="${c[1]}"/><path d="M-14 30 L -30 52 L -12 44Z M14 30 L 30 52 L 12 44Z" fill="${c[2]}"/><path d="M-9 42 Q 0 80 9 42Z" fill="#ff9f43"/></g>`;
    default:
      return "";
  }
}

function stars(w, h, r, n, color) {
  let s = "";
  for (let i = 0; i < n; i++) s += `<circle cx="${(r() * w).toFixed(1)}" cy="${(r() * h * 0.7).toFixed(1)}" r="${(r() * 1.6 + 0.3).toFixed(2)}" fill="${color}" opacity="${(0.3 + r() * 0.7).toFixed(2)}"/>`;
  return s;
}

function art({ w, h, c, motifKind, seed, title, tag, label }) {
  const r = rng(seed);
  const words = title ? title.split(" ") : [];
  let lines = [title];
  if (title && title.length > 11 && words.length > 1) {
    let best = null;
    for (let i = 1; i < words.length; i++) {
      const a = words.slice(0, i).join(" "), b = words.slice(i).join(" ");
      const score = Math.max(a.length, b.length);
      if (!best || score < best[0]) best = [score, [a, b]];
    }
    lines = best[1];
  }
  const longest = Math.max(...lines.map((l) => (l || "").length), 6);
  const titleSize = Math.min(w * 0.13, (w * 1.35) / longest);
  const baseY = h * 0.86 - (lines.length - 1) * titleSize * 0.95;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img" aria-label="${esc(title)} artwork">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0.4" y2="1"><stop offset="0" stop-color="${c[0]}"/><stop offset=".55" stop-color="${c[1]}"/><stop offset="1" stop-color="${c[0]}"/></linearGradient>
    <radialGradient id="glow"><stop offset="0" stop-color="${c[3]}"/><stop offset=".6" stop-color="${c[2]}"/><stop offset="1" stop-color="${c[2]}" stop-opacity="0"/></radialGradient>
    <linearGradient id="fade" x1="0" y1="0" x2="0" y2="1"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".75"/></linearGradient>
  </defs>
  <rect width="${w}" height="${h}" fill="url(#bg)"/>
  ${stars(w, h, r, Math.round((w * h) / 4000), c[3])}
  ${motif(motifKind, w, h, c, r)}
  <rect width="${w}" height="${h}" fill="url(#fade)"/>
  ${title ? lines.map((l, i) => `<text x="${w / 2}" y="${(baseY + i * titleSize * 0.95).toFixed(0)}" text-anchor="middle" fill="#fff" font-family="'Sora','Segoe UI',Arial,sans-serif" font-weight="800" font-size="${titleSize.toFixed(0)}" letter-spacing="${(titleSize * 0.04).toFixed(1)}">${esc(l)}</text>`).join("") : ""}
  ${tag ? `<text x="${w / 2}" y="${h * 0.86 + titleSize * 0.8}" text-anchor="middle" fill="${c[3]}" font-family="'Plus Jakarta Sans',Arial,sans-serif" font-size="${(titleSize * 0.3).toFixed(0)}" opacity=".9">${esc(tag)}</text>` : ""}
  ${label ? `<text x="${w * 0.06}" y="${h * 0.08}" fill="#fff" fill-opacity=".8" font-family="'Plus Jakarta Sans',Arial,sans-serif" font-weight="700" font-size="${(w * 0.035).toFixed(0)}" letter-spacing="3">${esc(label)}</text>` : ""}
</svg>`;
}

for (const m of MOVIES) {
  out(`posters/${m.slug}.svg`, art({ w: 400, h: 600, c: m.c, motifKind: m.motif, seed: m.slug, title: m.title, tag: m.tag, label: "CINEVERSE ORIGINAL" }));
  out(`backdrops/${m.slug}.svg`, art({ w: 1600, h: 900, c: m.c, motifKind: m.motif, seed: m.slug + "-b", title: "", tag: "" }));
  for (let i = 1; i <= 3; i++) {
    const shifted = [m.c[0], m.c[(i % 3) + 1], m.c[((i + 1) % 3) + 1], m.c[3]];
    out(`stills/${m.slug}-${i}.svg`, art({ w: 960, h: 540, c: shifted, motifKind: m.motif, seed: `${m.slug}-s${i}`, title: "", tag: "" }));
  }
}

const EVENTS = [
  ["hyderabad-comedy-night", "COMEDY NIGHT", ["#1a0633", "#5b1a8f", "#ffcc33", "#fff3c4"], "sun"],
  ["neon-music-festival", "NEON MUSIC FEST", ["#0c0223", "#40107a", "#ff3ea5", "#6ef2ff"], "skyline"],
  ["champions-arena-hawks-mariners", "CHAMPIONS ARENA", ["#03172b", "#0b4a7a", "#2bd4a4", "#e6fff7"], "grid"],
  ["deccan-derby-football", "DECCAN DERBY", ["#062212", "#14683a", "#8be36b", "#f0ffe4"], "grid"],
  ["pro-raiders-kabaddi", "PRO RAIDERS", ["#2a0909", "#9b1c1c", "#fb923c", "#ffedd5"], "sun"],
  ["the-tin-crown", "THE TIN CROWN", ["#1c1203", "#5c3a0a", "#d4a843", "#fff4d6"], "arches"],
  ["stories-from-the-charminar", "CHARMINAR STORIES", ["#1f0b1f", "#6b1f5c", "#f59ec3", "#fde7f1"], "arches"],
  ["strike-night-bowling", "STRIKE NIGHT", ["#07102b", "#1d3aa6", "#ff5d8f", "#e0e7ff"], "planet"],
  ["the-vault-escape-room", "THE VAULT", ["#0b0b0b", "#2e2e2e", "#e3b341", "#fff5d1"], "dish"],
  ["indie-garden-sessions", "INDIE GARDEN", ["#0c2413", "#256d3b", "#fcd34d", "#fefce8"], "rain"],
  ["watercolour-weekend", "WATERCOLOUR WEEKEND", ["#0b2a3a", "#1b7fa6", "#f9a8d4", "#f0f9ff"], "waves"],
  ["harbour-cup-final", "HARBOUR CUP FINAL", ["#04142a", "#123f78", "#f4c542", "#fdf8e3"], "waves"],
];
for (const [slug, title, c, m] of EVENTS) {
  out(`events/${slug}.svg`, art({ w: 400, h: 600, c, motifKind: m, seed: slug, title, tag: "", label: "LIVE ON CINEVERSE" }));
  out(`events/${slug}-banner.svg`, art({ w: 1600, h: 700, c, motifKind: m, seed: slug + "-b", title: "", tag: "" }));
}

const FOOD = {
  "popcorn": ["#fff6e0", "#e11d48", "tub"], "popcorn-large": ["#fff6e0", "#be123c", "tub"], "popcorn-cheese": ["#fff6d5", "#f59e0b", "tub"],
  "nachos": ["#fff4db", "#f59e0b", "chips"], "hotdog": ["#fff1e6", "#b45309", "hotdog"], "cola": ["#fde8e8", "#b91c1c", "cup"],
  "cola-blue": ["#e6efff", "#1d4ed8", "cup"], "water": ["#e8f7ff", "#0ea5e9", "bottle"], "coffee": ["#f6ede4", "#7c4a2d", "cup"],
  "combo": ["#f3ecff", "#7631e3", "combo"], "combo-solo": ["#f3ecff", "#8b4df3", "combo"],
};
const foodShape = (kind, c) => ({
  tub: `<path d="M60 80 L140 80 L128 170 L72 170Z" fill="#fff" stroke="${c}" stroke-width="4"/>${[70, 90, 110, 130].map((x) => `<rect x="${x - 4}" y="84" width="8" height="84" fill="${c}" opacity=".85"/>`).join("")}${[70, 88, 104, 120, 134, 80, 96, 112, 128].map((x, i) => `<circle cx="${x}" cy="${i < 5 ? 72 : 60}" r="13" fill="#fff8e1" stroke="#f3d27a" stroke-width="2"/>`).join("")}`,
  chips: `<path d="M50 150 Q100 175 150 150 L140 120 L60 120Z" fill="${c}" opacity=".3"/>${[0, 1, 2, 3, 4].map((i) => `<path d="M${60 + i * 18} 125 l 18 -50 l 18 50z" fill="#fbbf24" stroke="#d97706" stroke-width="2"/>`).join("")}<ellipse cx="100" cy="112" rx="34" ry="10" fill="#fde047"/>`,
  hotdog: `<rect x="40" y="95" width="120" height="36" rx="18" fill="#f5c07a"/><rect x="34" y="102" width="132" height="20" rx="10" fill="${c}"/><path d="M45 110 q 10 -8 20 0 t 20 0 t 20 0 t 20 0 t 20 0" stroke="#facc15" stroke-width="4" fill="none"/>`,
  cup: `<path d="M68 70 L132 70 L122 170 L78 170Z" fill="${c}"/><rect x="62" y="60" width="76" height="14" rx="5" fill="#fff" stroke="${c}" stroke-width="3"/><line x1="112" y1="60" x2="124" y2="30" stroke="#fff" stroke-width="6" stroke-linecap="round"/><rect x="78" y="100" width="44" height="26" rx="4" fill="#fff" opacity=".9"/>`,
  bottle: `<rect x="80" y="40" width="40" height="16" rx="4" fill="${c}"/><path d="M76 60 Q70 70 72 90 L72 170 Q100 178 128 170 L128 90 Q130 70 124 60Z" fill="#dff4ff" stroke="${c}" stroke-width="4"/><rect x="72" y="105" width="56" height="28" fill="${c}" opacity=".8"/>`,
  combo: `<path d="M40 90 L100 90 L92 170 L48 170Z" fill="#fff" stroke="${c}" stroke-width="4"/>${[48, 62, 76, 90].map((x, i) => `<circle cx="${x}" cy="${82 - (i % 2) * 8}" r="11" fill="#fff8e1" stroke="#f3d27a" stroke-width="2"/>`).join("")}<path d="M112 80 L160 80 L152 170 L120 170Z" fill="${c}"/><line x1="146" y1="80" x2="154" y2="50" stroke="#fff" stroke-width="5" stroke-linecap="round"/>`,
})[kind];
for (const [name, [bg, c, kind]] of Object.entries(FOOD)) {
  out(`food/${name}.svg`, `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200"><rect width="200" height="200" rx="28" fill="${bg}"/>${foodShape(kind, c)}</svg>`);
}

const OFFERS = { welcome: ["#6322c4", "#b07bff", "₹100"], weekend: ["#be185d", "#fb7185", "₹50"], upi: ["#0f766e", "#5eead4", "UPI"],
  student: ["#1d4ed8", "#93c5fd", "EDU"], bank: ["#92400e", "#fbbf24", "20%"], movie: ["#3a0f6b", "#e0467c", "15%"], city: ["#4c1d95", "#f0abfc", "HYD"] };
for (const [name, [a, b, big]] of Object.entries(OFFERS)) {
  const r = rng(name);
  let dots = "";
  for (let i = 0; i < 18; i++) dots += `<circle cx="${r() * 640}" cy="${r() * 320}" r="${4 + r() * 30}" fill="#fff" opacity="${0.04 + r() * 0.08}"/>`;
  out(`offers/${name}.svg`, `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 320" width="640" height="320"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs><rect width="640" height="320" fill="url(#g)"/>${dots}<text x="600" y="250" text-anchor="end" font-family="'Sora',Arial,sans-serif" font-weight="800" font-size="150" fill="#fff" opacity=".18">${big}</text></svg>`);
}

// Open Graph fallback
out("og-default.svg", art({ w: 1200, h: 630, c: ["#22094d", "#6322c4", "#b07bff", "#f5f1ff"], motifKind: "planet", seed: "og", title: "CINEVERSE", tag: "Movies · Events · Sports · Plays", label: "" }));
console.log("CineVerse media generated in", root);
