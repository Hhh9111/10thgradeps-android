// Generates android/app/src/main/assets/catalog.json from the site's own pages.
// Read-only with respect to the site: index.html, premium.html and netlify/functions/lib/catalog.mjs
// are only read, never modified. No protected PDF content is read or copied.
//
// Usage (from the repository root or from android/):  node android/tools/gen-app-catalog.mjs
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = process.env.SITE_ROOT ? path.resolve(process.env.SITE_ROOT) : path.resolve(here, '..', '..');
const out = path.join(here, '..', 'app', 'src', 'main', 'assets', 'catalog.json');
const SITE = 'https://10thgradeps.netlify.app';

const read = (f) => fs.readFileSync(path.join(repo, f), 'utf8');
function fail(msg) { console.error('ERROR: ' + msg); process.exit(1); }

// Extracts the JS literal that starts right after `marker` (which ends with "[" or "{") by bracket matching
// (string-aware), then evaluates only that literal in an empty sandbox (no globals, files or network).
function literal(src, marker) {
  const start = src.indexOf(marker);
  if (start < 0) fail('marker not found: ' + marker);
  const from = start + marker.length - 1;
  let depth = 0, q = '', esc = false, end = -1;
  for (let i = from; i < src.length; i++) {
    const c = src[i];
    if (q) { if (esc) esc = false; else if (c === '\\') esc = true; else if (c === q) q = ''; continue; }
    if (c === '"' || c === "'" || c === '`') { q = c; continue; }
    if (c === '[' || c === '{') depth++;
    else if (c === ']' || c === '}') { depth--; if (depth === 0) { end = i; break; } }
  }
  if (end < 0) fail('end of literal not found for: ' + marker);
  return vm.runInNewContext('(' + src.slice(from, end + 1) + ')', Object.create(null), { timeout: 2000 });
}

const index = read('index.html');
const premiumHtml = read('premium.html');

const FILES = literal(index, 'var FILES = [');
const THUMBS = literal(index, 'var THUMBS = {');
const coversLine = index.match(/var COVERS = (\{.*?\});/);
const COVERS = coversLine ? vm.runInNewContext('(' + coversLine[1] + ')', Object.create(null)) : {};

let LATEST = [];
try { LATEST = literal(index, 'var LATEST = ['); } catch (e) { LATEST = []; }

const subjects = literal(premiumHtml, 'const subjects=[');
const pfiles = literal(premiumHtml, 'let files=[');

if (!Array.isArray(FILES) || FILES.length < 10) fail('free catalog looks empty');
if (!Array.isArray(pfiles) || pfiles.length < 5) fail('premium catalog looks empty');

const free = FILES.filter(f => f && f.href && f.title).map(f => ({
  href: String(f.href), title: String(f.title), subject: String(f.subject || ''), kind: f.kind === 'img' ? 'img' : 'pdf',
  name: String(f.name || ''), meta: String(f.meta || ''), extra: String(f.extra || ''), thumb: THUMBS[f.href] || ''
}));
const seenFree = new Set();
const freeUnique = free.filter(f => !seenFree.has(f.href) && seenFree.add(f.href));

const premium = pfiles.filter(f => f && f.id && f.t).map(f => ({ id: String(f.id), s: String(f.s || ''), t: String(f.t), d: String(f.d || ''), a: f.a ? String(f.a) : '' }));

// Cross-checks (warnings only, the build is not blocked by them).
const warn = [];
try {
  const { CATALOG } = await import(pathToFileURL(path.join(repo, 'netlify/functions/lib/catalog.mjs')).href);
  for (const p of premium) if (!CATALOG[p.id]) warn.push('premium id not in server CATALOG (server would answer 404): ' + p.id);
  for (const id of Object.keys(CATALOG)) if (!premium.some(p => p.id === id)) warn.push('server CATALOG id not listed in premium.html: ' + id);
} catch (e) { warn.push('could not load lib/catalog.mjs: ' + e.message); }
for (const f of freeUnique) {
  if (/^files\//.test(f.href) && !fs.existsSync(path.join(repo, f.href))) warn.push('free file missing on disk: ' + f.href);
}
warn.forEach(w => console.warn('WARN: ' + w));

const catalog = {
  version: 1, generatedAt: new Date().toISOString(), site: SITE,
  subjects: subjects.map(s => ({ key: s.id, name: s.n, color: s.c })),
  covers: COVERS, latest: Array.isArray(LATEST) ? LATEST.filter(x => typeof x === 'string') : [], free: freeUnique, premium
};
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, JSON.stringify(catalog));
console.log(`catalog.json written: ${freeUnique.length} free items, ${premium.length} premium items, ${subjects.length} subjects, ${warn.length} warnings`);
