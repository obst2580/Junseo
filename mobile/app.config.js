// 번들 ID와 App Group은 위젯·알림 확장(targets/*)과 반드시 같아야 한다.
const BUNDLE_ID = process.env.IOS_BUNDLE_ID ?? 'com.junseo.app';
const APP_GROUP = `group.${BUNDLE_ID}`;
// 개발 빌드는 development, TestFlight·App Store 빌드는 production.
const APNS_ENV = process.env.APNS_ENV ?? 'development';
// 보상형 광고 단위. 비우면 Google 테스트 광고가 나온다 (src/lib/ads.ts).
const ANDROID_PACKAGE = process.env.ANDROID_PACKAGE ?? 'com.junseo.app';
const GOOGLE_SERVICES = process.env.GOOGLE_SERVICES_JSON;
const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'https://junseo-api.liliplanet.net';
if (['preview', 'production'].includes(process.env.EAS_BUILD_PROFILE)) {
  const url = new URL(API_URL);
  if (url.protocol !== 'https:' || ['localhost', '127.0.0.1'].includes(url.hostname) || url.username || url.password) {
    throw new Error('운영 앱에는 공개 HTTPS API 주소가 필요합니다.');
  }
}

/** @type {import('expo/config').ExpoConfig} */
module.exports = {
  name: 'Junseo',
  slug: 'junseo',
  scheme: 'junseo',
  version: require('./package.json').version,
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'dark',
  ios: {
    bundleIdentifier: BUNDLE_ID,
    appleTeamId: process.env.APPLE_TEAM_ID,
    supportsTablet: false,
    entitlements: {
      'com.apple.security.application-groups': [APP_GROUP],
      'keychain-access-groups': [`$(AppIdentifierPrefix)${BUNDLE_ID}.auth`],
    },
    infoPlist: {
      NSCameraUsageDescription: '친구에게 보낼 사진을 찍으려면 카메라 권한이 필요해요.',
      // 개발 중 같은 와이파이의 로컬 서버(http)에 붙기 위해서만 필요하다.
      NSAppTransportSecurity: { NSAllowsLocalNetworking: true },
    },
  },
  android: {
    package: ANDROID_PACKAGE,
    versionCode: 3,
    allowBackup: false,
    ...(GOOGLE_SERVICES ? { googleServicesFile: GOOGLE_SERVICES } : {}),
    adaptiveIcon: {
      foregroundImage: './assets/android-icon-foreground.png',
      backgroundImage: './assets/android-icon-background.png',
      monochromeImage: './assets/android-icon-monochrome.png',
      backgroundColor: '#0e0d0c',
    },
  },
  web: {
    favicon: './assets/favicon.png',
    output: 'single',
  },
  plugins: [
    'expo-router',
    'expo-secure-store',
    'expo-web-browser',
    'expo-image',
    ['expo-camera', { cameraPermission: '친구에게 보낼 사진을 찍으려면 카메라 권한이 필요해요.', microphonePermission: false, recordAudioAndroid: false }],
    ['expo-notifications', { mode: APNS_ENV }],
    '@bacons/apple-targets',
    './plugins/withJunseoAndroid',
    // 이 앱의 핵심인 위젯이 iOS 17 API(containerBackground 등)를 쓴다. 앱도 17 부터 깔리게 맞춘다 (16 에서는 위젯이 안 보인다).
    ['expo-build-properties', { ios: { deploymentTarget: '17.0' }, android: { minSdkVersion: 26 } }],
    // 템플릿을 만들 때 보는 보상형 광고. 실제 AdMob 앱 ID 를 받기 전까지는 Google 의 테스트 앱 ID 를 쓴다.
    ['react-native-google-mobile-ads', {
      iosAppId: process.env.ADMOB_IOS_APP_ID ?? 'ca-app-pub-3940256099942544~1458002511',
      androidAppId: process.env.ADMOB_ANDROID_APP_ID ?? 'ca-app-pub-3940256099942544~3347511713',
    }],
    // 만든 템플릿을 사진첩에 저장 (쓰기만)
    ['expo-media-library', { savePhotosPermission: '만든 사진을 사진첩에 저장하려면 권한이 필요해요.', isAccessMediaLocationEnabled: false }],
  ],
  experiments: {
    typedRoutes: true,
  },
  extra: {
    appGroup: APP_GROUP,
    apnsEnvironment: APNS_ENV,
    admobIosRewardedId: process.env.ADMOB_IOS_REWARDED_ID ?? '',
    admobAndroidRewardedId: process.env.ADMOB_ANDROID_REWARDED_ID ?? '',
    androidPushConfigured: !!GOOGLE_SERVICES,
    apiUrl: API_URL,
  },
};
