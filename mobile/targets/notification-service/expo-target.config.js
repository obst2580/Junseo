/** @type {import('@bacons/apple-targets/app.plugin').ConfigFunction} */
module.exports = (config) => ({
  type: 'notification-service',
  name: 'JunseoNotificationService',
  // → com.junseo.app.notification-service
  bundleIdentifier: '.notification-service',
  deploymentTarget: '17.0',
  frameworks: ['UserNotifications', 'WidgetKit'],
  entitlements: {
    'com.apple.security.application-groups': config.ios.entitlements['com.apple.security.application-groups'],
  },
});
