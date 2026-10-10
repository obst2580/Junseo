// src/의 화면·스타일·스크립트를 한 장짜리 HTML로 묶는다.
//  dist/index.html    — 로컬 서버(server.mjs)나 정적 호스팅에서 여는 완전한 문서
//  dist/artifact.html — claude.ai 아티팩트로 게시하는 본문 조각 (doctype/head는 게시 때 씌워진다)
import { build } from 'esbuild';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const src = (f) => path.join(root, 'src', f);

const TITLE = '국가의 시대';
const FONTS =
  'https://fonts.googleapis.com/css2?family=Gowun+Batang:wght@400;700&family=IBM+Plex+Sans+KR:wght@400;500;600&family=Song+Myung&display=swap';

const bundle = await build({
  entryPoints: [src('main.js')],
  bundle: true,
  format: 'iife',
  target: 'es2020',
  minify: false,
  legalComments: 'none',
  write: false,
});
// 인라인 <script> 안에서 문서가 끊기지 않도록 닫는 태그를 이스케이프한다
const js = bundle.outputFiles[0].text.replace(/<\/(script)/gi, '<\\/$1');
const css = await readFile(src('styles.css'), 'utf8');
const body = await readFile(src('body.html'), 'utf8');

const head = [
  `<title>${TITLE}</title>`,
  '<link rel="preconnect" href="https://fonts.googleapis.com">',
  '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>',
  `<link rel="stylesheet" href="${FONTS}">`,
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

await mkdir(path.join(root, 'dist'), { recursive: true });
await writeFile(path.join(root, 'dist', 'artifact.html'), fragment);
await writeFile(path.join(root, 'dist', 'index.html'), full);
console.log(`dist/index.html, dist/artifact.html (${Math.round(full.length / 1024)} KB)`);
