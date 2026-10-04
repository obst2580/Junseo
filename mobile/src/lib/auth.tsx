import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';

import { api, ApiError, configureApi, type AuthResponse, type Me } from './api';
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
  /**
   * 계정 삭제. 본인 확인: 리리플레닛 계정은 바로 그 자리에서 다시 로그인하고, 이메일 계정(개발용)은 비밀번호를 받는다.
   * 성공하면 로그아웃 상태가 된다.
   */
  deleteAccount: (password?: string) => Promise<void>;
  refreshMe: () => Promise<void>;
  setMe: (me: Me) => void;
};

const AuthContext = createContext<AuthState | null>(null);

/**
 * 로그인 유지: 서버의 준서 세션 토큰은 90일짜리이고, 앱을 쓰는 동안 하루에 한 번 새 만료로 바꾼다.
 * 새 토큰은 위젯 · 알림 확장이 읽는 곳(iOS 공유 Keychain, Android Keystore)에 저장되므로 위젯도 로그인 상태를 유지한다.
 * 앱을 90일 동안 한 번도 열지 않으면 그때는 다시 로그인해야 한다.
 */
const RENEW_AFTER_MS = 24 * 60 * 60 * 1000;

/** 토큰이 발급된 시각(ms). 읽지 못하면 null (그럴 때는 그냥 연장한다). */
function issuedAt(token: string): number | null {
  try {
    const part = token.split('.')[1];
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(part.length / 4) * 4, '=');
    const iat = JSON.parse(globalThis.atob(base64)).iat;
    return typeof iat === 'number' ? iat * 1000 : null;
  } catch {
    return null;
  }
}

/** 위젯 연결이 실패해도 (예: Android 위젯은 HTTPS 서버만 받는다) 로그인은 끝까지 간다. 위젯은 다음 실행 때 다시 연결한다. */
function linkWidget(userId: number) {
  try {
    widgetBridge.signIn(userId);
  } catch (e) {
    console.warn('widget link failed', e);
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [me, setMe] = useState<Me | null>(null);
  const tokenRef = useRef<string | null>(null);
  const finishRef = useRef<{ url: string; promise: Promise<AuthResponse> } | null>(null);
  // 로그아웃한 뒤의 로그인은 예전 리리플레닛 로그인을 이어 쓰지 않는다 (다른 계정으로 바꾸려는 경우)
  const freshRef = useRef(false);
  const renewedAtRef = useRef(0);

  const clear = useCallback(async () => {
    tokenRef.current = null;
    setMe(null);
    await tokenStore.set(null);
    widgetBridge.signOut();
  }, []);

  // 하루에 한 번 세션 연장. 세션이 끝났으면(로그아웃 · 삭제 · 비밀번호 변경) 401 → onUnauthorized 가 로그아웃시킨다.
  const renew = useCallback(async () => {
    const token = tokenRef.current;
    if (!token || Date.now() - renewedAtRef.current < RENEW_AFTER_MS) return;
    const iat = issuedAt(token);
    if (iat !== null && Date.now() - iat < RENEW_AFTER_MS) return;
    renewedAtRef.current = Date.now();
    try {
      const renewed = await api.renew();
      if (tokenRef.current !== token) return; // 그 사이 로그아웃했거나 다른 계정으로 로그인했다
      await tokenStore.set(renewed.accessToken);
      tokenRef.current = renewed.accessToken;
    } catch {
      // 네트워크 오류: 지금 토큰은 아직 오래 남았다. 다음에 앱이 앞으로 나올 때 다시 해 본다.
      renewedAtRef.current = 0;
    }
  }, []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') void renew();
    });
    return () => sub.remove();
  }, [renew]);

  useEffect(() => {
    configureApi({ getToken: () => tokenRef.current, onUnauthorized: () => void clear() });
    (async () => {
      widgetBridge.purgeLegacyToken();
      const saved = await tokenStore.get();
      if (saved) {
        tokenRef.current = saved;
        try {
          const user = await api.me();
          setMe(user);
          linkWidget(user.id);
          void renew();
        } catch {
          // 401 이면 onUnauthorized 가 정리한다. 네트워크 오류면 다음 실행에서 다시 시도한다.
        }
      }
      setReady(true);
    })();
  }, [clear, renew]);

  const accept = useCallback(async (res: AuthResponse) => {
    await tokenStore.set(res.accessToken);
    tokenRef.current = res.accessToken;
    setMe(res.user);
    linkWidget(res.user.id);
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
    const response = await platformLogin(finishSignIn, { fresh: freshRef.current });
    freshRef.current = false;
    return response.needsOnboarding ?? false;
  }, [finishSignIn]);
  const signOut = useCallback(async () => {
    await unregisterPush().catch(() => {});
    await api.logout().catch(() => {});
    freshRef.current = true;
    await clear();
  }, [clear]);
  // 서버가 기기 토큰까지 지우므로 알림 해제는 따로 하지 않는다
  const deleteAccount = useCallback(
    async (password?: string) => {
      if (me?.loginMethod === 'platform') {
        // 본인 확인: 지금 다시 로그인한 토큰으로 지운다. 지금 세션은 그대로 두고, 다른 계정이면 지우지 않는다.
        let fresh: AuthResponse;
        try {
          fresh = await platformLogin(resumePlatformLogin, { reauth: true });
        } catch (e) {
          if (e instanceof ApiError) throw e;
          throw new Error('리리플레닛 확인 로그인을 마치지 못해서 삭제하지 않았어요. 다시 해 주세요.');
        }
        if (fresh.user.id !== me.id) throw new Error('지금 쓰는 계정과 다른 리리플레닛 계정으로 로그인했어요. 같은 계정으로 다시 해 주세요.');
        await api.deleteAccount({ token: fresh.accessToken });
      } else {
        await api.deleteAccount({ password });
      }
      freshRef.current = true;
      await clear();
    },
    [clear, me],
  );
  const refreshMe = useCallback(async () => setMe(await api.me()), []);

  const value = useMemo<AuthState>(
    () => ({ ready, me, signIn, finishSignIn, signOut, deleteAccount, refreshMe, setMe }),
    [ready, me, signIn, finishSignIn, signOut, deleteAccount, refreshMe],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
