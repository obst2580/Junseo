/** @type {import('@bacons/apple-targets/app.plugin').ConfigFunction} */
module.exports = (config) => ({
  type: 'notification-service',
  name: 'JunseoNotificationService',
  // → com.junseo.app.notification-service
  bundleIdentifier: '.notification-service',
  deploymentTarget: '17.0',
  frameworks: ['UserNotifications', 'WidgetKit', 'Security'],
  entitlements: {
    'com.apple.security.application-groups': config.ios.entitlements['com.apple.security.application-groups'],
    'keychain-access-groups': config.ios.entitlements['keychain-access-groups'],
  },
});
