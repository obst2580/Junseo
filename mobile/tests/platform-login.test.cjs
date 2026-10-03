const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');
const React = require('react');
const { act, create } = require('react-test-renderer');

global.IS_REACT_ACT_ENVIRONMENT = true;
const root = path.join(__dirname, '..');
const state = 's'.repeat(43);
const code = 'c'.repeat(43);
const callback = `junseo://auth?code=${code}&state=${state}`;
const session = (onboarding = false) => ({ accessToken: 'fixture-session', expiresAt: '2099-01-01T00:00:00Z', user: { id: 1, needsOnboarding: onboarding }, needsOnboarding: onboarding });
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

function harness({ storage = new Map(), exchange = async () => session(), browser = async () => ({ type: 'success', url: callback }) } = {}) {
  const modules = new Map();
  const calls = { exchanges: [], redirects: [], tokens: [], widgets: [] };
  let auth;
  const mocks = {
    'react-native': { Platform: { OS: 'android' }, StyleSheet: { create: value => value }, ActivityIndicator: 'ActivityIndicator', Text: 'Text', View: 'View' },
    'react-native-safe-area-context': { SafeAreaView: 'SafeAreaView' },
    'expo-crypto': {
      getRandomBytesAsync: async size => Uint8Array.from(crypto.randomBytes(size)),
      CryptoDigestAlgorithm: { SHA256: 'sha256' }, CryptoEncoding: { BASE64: 'base64' },
      digestStringAsync: async (_, value) => crypto.createHash('sha256').update(value).digest('base64'),
    },
    'expo-secure-store': { AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY: 1, getItemAsync: async key => storage.get(key) ?? null, setItemAsync: async (key, value) => { storage.set(key, value); }, deleteItemAsync: async key => { storage.delete(key); } },
    'expo-web-browser': { openAuthSessionAsync: browser },
    'expo-router': {
      useLocalSearchParams: () => ({ code, state }),
      router: { replace: url => { calls.redirects.push(url); } },
      Redirect: ({ href }) => { calls.redirects.push(href); return React.createElement('Redirect', { href }); },
    },
    '@/components/ui': { Button: props => React.createElement('Button', props), ErrorText: props => React.createElement('ErrorText', props) },
    '@/lib/theme': { colors: { accent: '#ff0', text: '#fff' } },
    './api': { configureApi: () => {}, api: {
      startLogin: async (challenge, returnUri) => { calls.challenge = challenge; calls.returnUri = returnUri; return { state, launchUrl: 'https://example.test/auth/launch' }; },
      exchangeLogin: async (...args) => { calls.exchanges.push(args); return exchange(...args); },
    } },
    './push': { unregisterPush: async () => {} },
    './tokenStore': { tokenStore: { get: async () => null, set: async token => { calls.tokens.push(token); } } },
    './widgetBridge': { widgetBridge: { signIn: id => { calls.widgets.push(id); }, signOut: () => {} } },
  };
  function load(file) {
    const filename = path.resolve(root, file);
    if (modules.has(filename)) return modules.get(filename).exports;
    const mod = { exports: {} };
    modules.set(filename, mod);
    const source = ts.transpileModule(fs.readFileSync(filename, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText;
    const requireModule = name => {
      if (mocks[name]) return mocks[name];
      if (name.startsWith('@/')) return load(`src/${name.slice(2)}${name === '@/lib/auth' ? '.tsx' : '.ts'}`);
      if (name.startsWith('.')) return load(path.relative(root, path.resolve(path.dirname(filename), `${name}.ts`)));
      return require(name);
    };
    vm.runInNewContext(`(function(require,module,exports){${source}\n})`, { URL, Date, console, globalThis, encodeURIComponent }, { filename })(requireModule, mod, mod.exports);
    return mod.exports;
  }
  async function renderCallback() {
    const { AuthProvider, useAuth } = load('src/lib/auth.tsx');
    const AuthReturn = load('src/app/auth.tsx').default;
    function Capture() { auth = useAuth(); return React.createElement(AuthReturn); }
    let renderer;
    await act(async () => { renderer = create(React.createElement(AuthProvider, null, React.createElement(Capture))); });
    return renderer;
  }
  return { load, storage, calls, renderCallback, auth: () => auth };
}

test('callback waits for exchange and durable token before opening the onboarding screen', async () => {
  const result = deferred();
  const h = harness({ browser: async () => ({ type: 'dismiss' }), exchange: () => result.promise });
  await assert.rejects(h.load('src/lib/platformLogin.ts').platformLogin());
  const screen = await h.renderCallback();
  assert.equal(h.calls.redirects.length, 0);
  assert.equal(h.calls.tokens.length, 0);
  assert.equal(screen.root.findAllByType('ActivityIndicator').length, 1);
  await act(async () => { result.resolve(session(true)); });
  assert.deepEqual(h.calls.tokens, ['fixture-session']);
  assert.equal(h.calls.redirects.at(-1), '/signup');
  assert.equal(h.calls.exchanges.length, 1);
  assert.equal(h.storage.has('junseo.pendingLogin'), false);
  await act(async () => screen.unmount());
});

test('completed profile goes to tabs instead of the protected login route', async () => {
  const h = harness({ browser: async () => ({ type: 'dismiss' }) });
  await assert.rejects(h.load('src/lib/platformLogin.ts').platformLogin());
  const screen = await h.renderCallback();
  assert.equal(h.calls.redirects.at(-1), '/(tabs)');
  assert.equal(h.calls.redirects.includes('/login'), false);
  await act(async () => screen.unmount());
});

test('browser success and Router callback exchange and save the session once', async () => {
  const browserResult = deferred();
  const h = harness({ browser: () => browserResult.promise });
  const login = h.load('src/lib/platformLogin.ts').platformLogin();
  await new Promise(resolve => setImmediate(resolve));
  const screen = await h.renderCallback();
  await act(async () => { browserResult.resolve({ type: 'success', url: callback }); await login; });
  assert.equal(h.calls.exchanges.length, 1);
  assert.deepEqual(h.calls.tokens, ['fixture-session']);
  await act(async () => screen.unmount());
});

test('Android process recreation recovers the encrypted PKCE transaction', async () => {
  const storage = new Map();
  const before = harness({ storage, browser: async () => ({ type: 'dismiss' }) });
  await assert.rejects(before.load('src/lib/platformLogin.ts').platformLogin());
  const verifier = JSON.parse(storage.get('junseo.pendingLogin')).verifier;
  assert.equal(crypto.createHash('sha256').update(verifier).digest('base64url'), before.calls.challenge);
  const after = harness({ storage });
  const screen = await after.renderCallback();
  assert.equal(after.calls.exchanges[0][1], verifier);
  assert.equal(after.calls.redirects.at(-1), '/(tabs)');
  await act(async () => screen.unmount());
});

test('exchange failure shows retry rather than an empty callback screen', async () => {
  const h = harness({ browser: async () => ({ type: 'dismiss' }), exchange: async () => { throw new Error('서버에 연결할 수 없어요.'); } });
  await assert.rejects(h.load('src/lib/platformLogin.ts').platformLogin());
  const screen = await h.renderCallback();
  assert.equal(screen.root.findByType('Button').props.title, '다시 로그인하기');
  assert.equal(h.calls.redirects.length, 0);
  assert.equal(h.calls.tokens.length, 0);
  await act(async () => screen.unmount());
});

test('wrong callback state or URI cannot consume the active login', async () => {
  const h = harness({ browser: async () => ({ type: 'dismiss' }) });
  const login = h.load('src/lib/platformLogin.ts');
  await assert.rejects(login.platformLogin());
  for (const url of [callback.replace(state, 'x'.repeat(43)), callback.replace('junseo://auth', 'junseo://other'), callback.replace('junseo:', 'https:')]) {
    await assert.rejects(login.resumePlatformLogin(url));
    assert.equal(h.calls.exchanges.length, 0);
    assert.equal(h.storage.has('junseo.pendingLogin'), true);
  }
  await login.resumePlatformLogin(callback);
  assert.equal(h.calls.exchanges.length, 1);
});

test('expired transaction never exchanges a code', async () => {
  const h = harness();
  h.storage.set('junseo.pendingLogin', JSON.stringify({ state, verifier: 'v'.repeat(64), returnUri: 'junseo://auth', expiresAt: Date.now() - 1 }));
  await assert.rejects(h.load('src/lib/platformLogin.ts').resumePlatformLogin(callback));
  assert.equal(h.calls.exchanges.length, 0);
  assert.equal(h.storage.has('junseo.pendingLogin'), false);
});

test('an old in-flight exchange cannot accept or delete a newer login', async () => {
  const result = deferred();
  let pending = null;
  const { LoginTransaction } = harness().load('src/lib/loginTransaction.ts');
  const flow = new LoginTransaction({ read: async () => pending, write: async value => { pending = value; } }, () => result.promise);
  await flow.begin({ state, verifier: 'v'.repeat(64), returnUri: 'junseo://auth', expiresAt: Date.now() + 60_000 });
  const old = flow.complete(callback);
  await new Promise(resolve => setImmediate(resolve));
  await flow.begin({ state: 'n'.repeat(43), verifier: 'z'.repeat(64), returnUri: 'junseo://auth', expiresAt: Date.now() + 60_000 });
  result.resolve(session());
  await assert.rejects(old);
  assert.equal(pending.state, 'n'.repeat(43));
});
