import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { api, configureApi, type AuthResponse, type Me } from './api';
import { unregisterPush } from './push';
import { tokenStore } from './tokenStore';
import { widgetBridge } from './widgetBridge';
import { platformLogin, resumePlatformLogin } from './platformLogin';

type AuthState = {
  ready: boolean;
  me: Me | null;
  signIn: () => Promise<boolean>;
  finishSignIn: (url: string) => Promise<AuthResponse>;
  signOut: () => Promise<void>;
  /** 비밀번호 찾기의 마지막 단계: 새 비밀번호로 바꾸고 바로 로그인 */
  resetPassword: (email: string, code: string, password: string) => Promise<void>;
  /** 계정 삭제 (비밀번호를 한 번 더 확인한다). 성공하면 로그아웃 상태가 된다. */
  deleteAccount: (password: string) => Promise<void>;
  refreshMe: () => Promise<void>;
  setMe: (me: Me) => void;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [me, setMe] = useState<Me | null>(null);
  const tokenRef = useRef<string | null>(null);
  const finishRef = useRef<{ url: string; promise: Promise<AuthResponse> } | null>(null);

  const clear = useCallback(async () => {
    tokenRef.current = null;
    setMe(null);
    await tokenStore.set(null);
    widgetBridge.signOut();
  }, []);

  useEffect(() => {
    configureApi({ getToken: () => tokenRef.current, onUnauthorized: () => void clear() });
    (async () => {
      const saved = await tokenStore.get();
      if (saved) {
        tokenRef.current = saved;
        try {
          const user = await api.me();
          setMe(user);
          widgetBridge.signIn(user.id);
        } catch {
          // 401 이면 onUnauthorized 가 정리한다. 네트워크 오류면 다음 실행에서 다시 시도한다.
        }
      }
      setReady(true);
    })();
  }, [clear]);

  const accept = useCallback(async (res: AuthResponse) => {
    await tokenStore.set(res.accessToken);
    tokenRef.current = res.accessToken;
    widgetBridge.signIn(res.user.id);
    setMe(res.user);
  }, []);

  // 함수들은 참조가 바뀌지 않게 따로 만든다. 화면들이 이펙트 의존성으로 쓰기 때문이다.
  const finishSignIn = useCallback((url: string) => {
    if (finishRef.current?.url === url) return finishRef.current.promise;
    const promise = resumePlatformLogin(url).then(async (response) => {
      await accept(response);
      return response;
    });
    finishRef.current = { url, promise };
    return promise;
  }, [accept]);

  const signIn = useCallback(async () => {
    const response = await platformLogin(finishSignIn);
    return response.needsOnboarding ?? false;
  }, [finishSignIn]);
  const signOut = useCallback(async () => {
    await unregisterPush().catch(() => {});
    await api.logout().catch(() => {});
    await clear();
  }, [clear]);
  const resetPassword = useCallback(
    async (email: string, code: string, password: string) => accept(await api.confirmPasswordReset(email.trim(), code.trim(), password)),
    [accept],
  );
  // 서버가 기기 토큰까지 지우므로 알림 해제는 따로 하지 않는다
  const deleteAccount = useCallback(
    async (password: string) => {
      await api.deleteAccount(password);
      await clear();
    },
    [clear],
  );
  const refreshMe = useCallback(async () => setMe(await api.me()), []);

  const value = useMemo<AuthState>(
    () => ({ ready, me, signIn, finishSignIn, signOut, resetPassword, deleteAccount, refreshMe, setMe }),
    [ready, me, signIn, finishSignIn, signOut, resetPassword, deleteAccount, refreshMe],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
