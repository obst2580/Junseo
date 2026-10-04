const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

const root = path.join(__dirname, '..');

/** Loads src/lib/homeWidget.ts with a fake platform, widget count and secure store. */
function load({ os = 'android', androidCount = 0, iosCount = null, store = new Map() } = {}) {
  const mocks = {
    'react-native': { Platform: { OS: os } },
    'expo-secure-store': {
      getItemAsync: async (key) => store.get(key) ?? null,
      setItemAsync: async (key, value) => { store.set(key, value); },
    },
    'expo-modules-core': {
      requireOptionalNativeModule: (name) => (name === 'JunseoWidgetStatus' && iosCount !== null ? { installedCountAsync: async () => iosCount } : null),
    },
    './androidWidget': { androidWidget: os === 'android' ? { installedCount: () => androidCount } : null },
  };
  const filename = path.join(root, 'src/lib/homeWidget.ts');
  const source = ts.transpileModule(fs.readFileSync(filename, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const mod = { exports: {} };
  vm.runInNewContext(`(function(require,module,exports){${source}\n})`, {}, { filename })((name) => mocks[name] ?? require(name), mod, mod.exports);
  return { ...mod.exports, store };
}

test('right after login with no widget on the home screen, the setup screen is offered', async () => {
  assert.equal(await load({ androidCount: 0 }).shouldOfferWidgetSetup(7), true);
  assert.equal(await load({ os: 'ios', iosCount: 0 }).shouldOfferWidgetSetup(7), true);
});

test('a widget already on the home screen means no setup screen (and it is remembered)', async () => {
  const android = load({ androidCount: 1 });
  assert.equal(await android.shouldOfferWidgetSetup(7), false);
  assert.equal(android.store.get('junseo.widgetSetup.7'), '1');
  assert.equal(await load({ os: 'ios', iosCount: 2 }).shouldOfferWidgetSetup(7), false);
});

test('after "later" (or placing one) it is not offered again for that person on this device', async () => {
  const lib = load({ androidCount: 0 });
  await lib.markWidgetSetupDone(7);
  assert.equal(await lib.shouldOfferWidgetSetup(7), false);
  // another account on the same phone still gets it
  assert.equal(await lib.shouldOfferWidgetSetup(8), true);
});

test('when iOS cannot tell, the setup screen is still offered; the web preview never shows it', async () => {
  assert.equal(await load({ os: 'ios', iosCount: -1 }).shouldOfferWidgetSetup(7), true);
  assert.equal(await load({ os: 'web' }).shouldOfferWidgetSetup(7), false);
  assert.equal(await load({ os: 'ios', iosCount: 3 }).installedWidgets(), 3);
  assert.equal(await load({ os: 'ios', iosCount: -1 }).installedWidgets(), null);
});
