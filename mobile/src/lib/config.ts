import Constants from 'expo-constants';

const extra = (Constants.expoConfig?.extra ?? {}) as { appGroup?: string; apnsEnvironment?: string };

export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
export const APP_GROUP = extra.appGroup ?? 'group.com.junseo.app';
export const APNS_ENVIRONMENT: 'development' | 'production' =
  extra.apnsEnvironment === 'production' ? 'production' : 'development';

// 위젯 종류 이름. targets/_shared/WidgetShared.swift 의 SharedConfig.widgetKind 와 같아야 한다.
export const WIDGET_KIND = 'MomentWidget';

/** 서버가 주는 상대 경로(/media/...)를 절대 주소로 바꾼다. */
export function absoluteUrl(path: string): string {
  return path.startsWith('http') ? path : `${API_BASE_URL}${path}`;
}

/** 이용약관 · 개인정보처리방침 (서버가 공개 페이지로 둔다: backend/src/main/resources/static/legal) */
export const legalUrl = (page: 'terms' | 'privacy') => `${API_BASE_URL}/legal/${page}.html`;
