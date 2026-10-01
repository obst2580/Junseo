// 번들 ID와 App Group은 위젯·알림 확장(targets/*)과 반드시 같아야 한다.
const BUNDLE_ID = process.env.IOS_BUNDLE_ID ?? 'com.junseo.app';
const APP_GROUP = `group.${BUNDLE_ID}`;
// 개발 빌드는 development, TestFlight·App Store 빌드는 production.
const APNS_ENV = process.env.APNS_ENV ?? 'development';

/** @type {import('expo/config').ExpoConfig} */
module.exports = {
  name: 'Junseo',
  slug: 'junseo',
  scheme: 'junseo',
  version: '0.1.0',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'dark',
  ios: {
    bundleIdentifier: BUNDLE_ID,
    appleTeamId: process.env.APPLE_TEAM_ID,
    supportsTablet: false,
    entitlements: {
      'com.apple.security.application-groups': [APP_GROUP],
    },
    infoPlist: {
      NSCameraUsageDescription: '친구에게 보낼 사진을 찍으려면 카메라 권한이 필요해요.',
      // 개발 중 같은 와이파이의 로컬 서버(http)에 붙기 위해서만 필요하다.
      NSAppTransportSecurity: { NSAllowsLocalNetworking: true },
    },
  },
  web: {
    favicon: './assets/favicon.png',
    output: 'single',
  },
  plugins: [
    'expo-router',
    'expo-secure-store',
    'expo-image',
    ['expo-camera', { cameraPermission: '친구에게 보낼 사진을 찍으려면 카메라 권한이 필요해요.', microphonePermission: false, recordAudioAndroid: false }],
    ['expo-notifications', { mode: APNS_ENV }],
    '@bacons/apple-targets',
  ],
  experiments: {
    typedRoutes: true,
  },
  extra: {
    appGroup: APP_GROUP,
    apnsEnvironment: APNS_ENV,
  },
};
