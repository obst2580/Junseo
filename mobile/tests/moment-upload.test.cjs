const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

const root = path.join(__dirname, '..');
const photoUri = 'file:///cache/ImageManipulator/photo.jpg';
const photoBytes = Uint8Array.from([0xff, 0xd8, 0xff, 0xe0, 0, 6, 0x4a, 0x46, 0x49, 0x46, 0xff, 0xd9]);

// Use the installed SDK's real FormData patch and multipart encoder, so a native
// { uri } descriptor cannot silently pass as it would with a mocked fetch.
function harness({ offline = false } = {}) {
  const requests = [];
  class NativeFile {
    constructor(uri) { assert.equal(uri, photoUri); this.uri = uri; }
    get name() { return 'photo.jpg'; }
    get type() { return 'image/jpeg'; }
    async bytes() { return photoBytes; }
  }
  class NativeFormData {
    constructor() { this._parts = []; }
  }
  const modules = new Map();
  const globals = { Blob, TextEncoder, Uint8Array, URLSearchParams, AbortController, setTimeout, clearTimeout };
  function load(filename) {
    const absolute = path.resolve(root, filename);
    if (modules.has(absolute)) return modules.get(absolute).exports;
    const mod = { exports: {} };
    modules.set(absolute, mod);
    const source = ts.transpileModule(fs.readFileSync(absolute, 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText;
    const requireModule = name => {
      if (name === './config') return { API_BASE_URL: 'https://junseo-api.example.test' };
      if (name === 'expo-file-system') return { File: NativeFile };
      if (name.startsWith('.')) return load(path.relative(root, path.resolve(path.dirname(absolute), `${name}.ts`)));
      return require(name);
    };
    vm.runInNewContext(`(function(require,module,exports){${source}\n})`, globals, { filename: absolute })(requireModule, mod, mod.exports);
    return mod.exports;
  }
  load('node_modules/expo/src/winter/FormData.ts').installFormDataPatch(NativeFormData);
  globals.FormData = NativeFormData;
  const { convertFormDataAsync } = load('node_modules/expo/src/winter/fetch/convertFormData.ts');
  globals.fetch = async (url, init) => {
    const multipart = await convertFormDataAsync(init.body, 'test-boundary');
    requests.push({ url, ...init, multipart });
    if (offline) throw new TypeError('Connection unavailable');
    return new Response(JSON.stringify({ id: 17 }), { status: 201 });
  };
  const { api, configureApi } = load('src/lib/api.ts');
  configureApi({ getToken: () => 'fixture-session', onUnauthorized: () => assert.fail('Upload must not sign out') });
  return { api, requests };
}

test('native photo reaches the SDK multipart encoder with its actual bytes', async () => {
  const { api, requests } = harness();
  assert.equal((await api.uploadMoment(photoUri)).id, 17);
  const request = requests[0];
  assert.equal(request.url, 'https://junseo-api.example.test/api/moments');
  assert.equal(request.headers.Authorization, 'Bearer fixture-session');
  assert.equal(request.headers['Content-Type'], undefined); // SDK sets the boundary.
  const body = Buffer.from(request.multipart.body);
  assert.notEqual(body.indexOf(Buffer.from(photoBytes)), -1);
  assert.match(body.toString('latin1'), /name="image"; filename="photo.jpg"/);
  assert.match(body.toString('latin1'), /content-type: image\/jpeg/);
});

test('selected friends keep repeated recipient fields in the multipart request', async () => {
  const { api, requests } = harness();
  await api.uploadMoment(photoUri, [3, 8]);
  const body = Buffer.from(requests[0].multipart.body).toString('latin1');
  assert.equal((body.match(/name="recipientIds"/g) ?? []).length, 2);
  assert.match(body, /name="recipientIds"\r\n\r\n3\r\n/);
  assert.match(body, /name="recipientIds"\r\n\r\n8\r\n/);
});

test('all-friends uploads omit recipient fields', async () => {
  const { api, requests } = harness();
  await api.uploadMoment(photoUri);
  assert.doesNotMatch(Buffer.from(requests[0].multipart.body).toString('latin1'), /recipientIds/);
});

test('web Blob uploads retain the JPEG filename and image bytes', async () => {
  const { api, requests } = harness();
  await api.uploadMomentBlob(new Blob([photoBytes], { type: 'image/jpeg' }), [3]);
  const body = Buffer.from(requests[0].multipart.body);
  assert.match(body.toString('latin1'), /name="image"; filename="moment.jpg"/);
  assert.notEqual(body.indexOf(Buffer.from(photoBytes)), -1);
});

test('a real transport failure still reports a network error', async () => {
  const { api, requests } = harness({ offline: true });
  await assert.rejects(api.uploadMoment(photoUri), error => error.code === 'NETWORK' && error.status === 0);
  assert.equal(requests.length, 1); // Encoding succeeded before the transport failed.
});
