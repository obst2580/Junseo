// 번들 ID와 App Group은 위젯·알림 확장(targets/*)과 반드시 같아야 한다.
const BUNDLE_ID = process.env.IOS_BUNDLE_ID ?? 'com.junseo.app';
const APP_GROUP = `group.${BUNDLE_ID}`;
// 개발 빌드는 development, TestFlight·App Store 빌드는 production.
const APNS_ENV = process.env.APNS_ENV ?? 'development';
// 보상형 광고 단위. 비우면 Google 테스트 광고가 나온다 (src/lib/ads.ts).
const ADMOB_REWARDED_ID = process.env.ADMOB_IOS_REWARDED_ID ?? '';

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
    // 이 앱의 핵심인 위젯이 iOS 17 API(containerBackground 등)를 쓴다. 앱도 17 부터 깔리게 맞춘다 (16 에서는 위젯이 안 보인다).
    ['expo-build-properties', { ios: { deploymentTarget: '17.0' } }],
    // 템플릿을 만들 때 보는 보상형 광고. 실제 AdMob 앱 ID 를 받기 전까지는 Google 의 테스트 앱 ID 를 쓴다.
    ['react-native-google-mobile-ads', { iosAppId: process.env.ADMOB_IOS_APP_ID ?? 'ca-app-pub-3940256099942544~1458002511' }],
    // 만든 템플릿을 사진첩에 저장 (쓰기만)
    ['expo-media-library', { savePhotosPermission: '만든 사진을 사진첩에 저장하려면 권한이 필요해요.', isAccessMediaLocationEnabled: false }],
  ],
  experiments: {
    typedRoutes: true,
  },
  extra: {
    appGroup: APP_GROUP,
    apnsEnvironment: APNS_ENV,
    admobRewardedId: ADMOB_REWARDED_ID,
  },
};
