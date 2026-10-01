/** @type {import('@bacons/apple-targets/app.plugin').ConfigFunction} */
module.exports = (config) => ({
  type: 'widget',
  name: 'JunseoWidget',
  displayName: 'junseo',
  // 앱 번들 ID 뒤에 붙는다 → com.junseo.app.widget (WidgetShared.swift 가 이 규칙으로 App Group 을 찾는다)
  bundleIdentifier: '.widget',
  // containerBackground, contentMargins 등 iOS 17 위젯 API 를 쓴다. 위젯 푸시는 iOS 26 에서만 켜진다.
  deploymentTarget: '17.0',
  frameworks: ['SwiftUI', 'WidgetKit'],
  colors: {
    $accent: '#FFC83D',
    $widgetBackground: '#141414',
  },
  entitlements: {
    'com.apple.security.application-groups': config.ios.entitlements['com.apple.security.application-groups'],
    // iOS 26 위젯 푸시를 받으려면 위젯 확장에도 푸시 권한이 있어야 한다.
    'aps-environment': process.env.APNS_ENV ?? 'development',
  },
});
