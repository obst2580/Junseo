import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';
import * as WebBrowser from 'expo-web-browser';
import { Platform } from 'react-native';

import { api, type AuthResponse } from './api';
import { LoginTransaction, type PendingLogin } from './loginTransaction';

export const NATIVE_RETURN = 'junseo://auth';
const base64url = (value: string) => value.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const PENDING_KEY = 'junseo.pendingLogin';
const pendingOptions: SecureStore.SecureStoreOptions = {
  keychainService: 'junseo.login',
  keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
};

export { loginCode } from './loginTransaction';

const transaction = new LoginTransaction({
  async read() {
    const raw = Platform.OS === 'web'
      ? globalThis.sessionStorage?.getItem(PENDING_KEY)
      : await SecureStore.getItemAsync(PENDING_KEY, pendingOptions);
    if (!raw) return null;
    try { return JSON.parse(raw) as PendingLogin; } catch { return null; }
  },
  async write(value) {
    if (Platform.OS === 'web') {
      if (value) globalThis.sessionStorage?.setItem(PENDING_KEY, JSON.stringify(value));
      else globalThis.sessionStorage?.removeItem(PENDING_KEY);
    } else if (value) {
      await SecureStore.setItemAsync(PENDING_KEY, JSON.stringify(value), pendingOptions);
    } else {
      await SecureStore.deleteItemAsync(PENDING_KEY, pendingOptions);
    }
  },
}, api.exchangeLogin);

export const resumePlatformLogin = (url: string) => transaction.complete(url);

export async function platformLogin(complete = resumePlatformLogin): Promise<AuthResponse> {
  const bytes = await Crypto.getRandomBytesAsync(32);
  const verifier = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  const hash = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, verifier, { encoding: Crypto.CryptoEncoding.BASE64 });
  const returnUri = Platform.OS === 'web' ? `${globalThis.location.origin}/auth-callback` : NATIVE_RETURN;
  const started = await api.startLogin(base64url(hash), returnUri);
  // Android may recreate the app while Chrome is open. Keep PKCE until the callback finishes.
  await transaction.begin({ state: started.state, verifier, returnUri, expiresAt: Date.now() + 10 * 60_000 });
  const result = await WebBrowser.openAuthSessionAsync(started.launchUrl, returnUri);
  // Keep the pending transaction: an Android URL intent can arrive after the browser reports dismiss.
  if (result.type !== 'success') throw new Error('로그인이 취소되었어요.');
  return complete(result.url);
}
