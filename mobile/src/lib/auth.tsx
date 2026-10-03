import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

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

  const clear = useCallback(async () => {
    tokenRef.current = null;
    setMe(null);
    await tokenStore.set(null);
    widgetBridge.signOut();
  }, []);

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
