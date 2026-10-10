// src/의 화면·스타일·스크립트를 게임마다 한 장짜리 HTML로 묶는다.
//  국가의 시대: dist/index.html (완전한 문서), dist/artifact.html (claude.ai 아티팩트용 본문 조각)
//  기업의 시대: dist/business.html, dist/business-artifact.html
// 아티팩트 조각에는 doctype/head가 없다. 게시할 때 씌워진다.
import { build } from 'esbuild';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import kingdom from '../src/modes/kingdom.js';
import business from '../src/modes/business.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const src = (f) => path.join(root, 'src', f);

const GAMES = [
  { mode: kingdom, entry: 'entry-kingdom.js', themes: [], page: 'index.html', fragment: 'artifact.html' },
  { mode: business, entry: 'entry-business.js', themes: ['theme-business.css'], page: 'business.html', fragment: 'business-artifact.html' },
];

const escapeHtml = (v) =>
  String(v).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

// body.html의 {{키}}를 모드 문구로 채운다. 빠진 키가 있으면 빌드를 멈춘다.
function fillTemplate(html, mode) {
  const values = { title: mode.title, ...mode.ui };
  return html.replace(/\{\{(\w+)\}\}/g, (_, key) => {
    if (typeof values[key] !== 'string') throw new Error(`${mode.id}: body.html의 {{${key}}}에 넣을 문구가 없습니다.`);
    return escapeHtml(values[key]);
  });
}

const baseCss = await readFile(src('styles.css'), 'utf8');
const bodyTemplate = await readFile(src('body.html'), 'utf8');
await mkdir(path.join(root, 'dist'), { recursive: true });

for (const game of GAMES) {
  const { mode } = game;
  const bundle = await build({
    entryPoints: [src(game.entry)],
    bundle: true,
    format: 'iife',
    target: 'es2020',
    minify: false,
    legalComments: 'none',
    write: false,
  });
  // 인라인 <script> 안에서 문서가 끊기지 않도록 닫는 태그를 이스케이프한다
  const js = bundle.outputFiles[0].text.replace(/<\/(script)/gi, '<\\/$1');
  const themes = await Promise.all(game.themes.map((f) => readFile(src(f), 'utf8')));
  const css = [baseCss, ...themes].join('\n');
  const body = fillTemplate(bodyTemplate, mode);

  const head = [
    `<title>${escapeHtml(mode.title)}</title>`,
    '<link rel="preconnect" href="https://fonts.googleapis.com">',
    '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>',
    `<link rel="stylesheet" href="${mode.fontsUrl}">`,
    `<style>\n${css}</style>`,
  ].join('\n');

  const fragment = `${head}\n${body}\n<script>\n${js}</script>\n`;
  const full = `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
${head}
</head>
<body>
${body}
<script>
${js}</script>
</body>
</html>
`;
  await writeFile(path.join(root, 'dist', game.fragment), fragment);
  await writeFile(path.join(root, 'dist', game.page), full);
  console.log(`${mode.title}: dist/${game.page}, dist/${game.fragment} (${Math.round(full.length / 1024)} KB)`);
}
