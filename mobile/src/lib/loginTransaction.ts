import type { AuthResponse } from './api';

export type PendingLogin = { state: string; verifier: string; returnUri: string; expiresAt: number };
type PendingStore = { read: () => Promise<PendingLogin | null>; write: (value: PendingLogin | null) => Promise<void> };

export function loginCode(url: string, returnUri: string, state: string): string {
  const actual = new URL(url);
  const expected = new URL(returnUri);
  if (actual.protocol !== expected.protocol || actual.host !== expected.host || actual.pathname !== expected.pathname || actual.searchParams.get('state') !== state) {
    throw new Error('로그인 응답이 올바르지 않아요. 다시 시작해 주세요.');
  }
  const code = actual.searchParams.get('code');
  if (!code || !/^[A-Za-z0-9_-]{43}$/.test(code)) throw new Error('로그인 코드가 없어요. 다시 시작해 주세요.');
  return code;
}

/** The browser promise and Router callback can receive the same single-use code. */
export class LoginTransaction {
  private completion: { url: string; promise: Promise<AuthResponse> } | null = null;

  constructor(private readonly store: PendingStore, private readonly exchange: (code: string, verifier: string) => Promise<AuthResponse>) {}

  async begin(pending: PendingLogin): Promise<void> {
    await this.store.write(pending);
    this.completion = null;
  }

  complete(url: string): Promise<AuthResponse> {
    if (this.completion?.url === url) return this.completion.promise;
    const promise = this.exchangePending(url);
    this.completion = { url, promise };
    return promise;
  }

  private async exchangePending(url: string): Promise<AuthResponse> {
    const pending = await this.store.read();
    if (!pending || !/^[A-Za-z0-9_-]{43}$/.test(pending.state) || !/^[A-Za-z0-9._~-]{43,128}$/.test(pending.verifier) || !Number.isFinite(pending.expiresAt) || pending.expiresAt <= Date.now()) {
      await this.store.write(null);
      throw new Error('로그인 연결이 만료되었어요. 앱에서 다시 시작해 주세요.');
    }
    // Reject unrelated callbacks before consuming or changing the active transaction.
    const code = loginCode(url, pending.returnUri, pending.state);
    try {
      const response = await this.exchange(code, pending.verifier);
      const current = await this.store.read();
      if (current?.state !== pending.state) throw new Error('새 로그인 시도가 시작되었어요. 다시 로그인해 주세요.');
      return response;
    } finally {
      const current = await this.store.read();
      if (current?.state === pending.state) await this.store.write(null);
    }
  }
}
