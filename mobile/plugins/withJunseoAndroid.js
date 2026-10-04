const { withAndroidManifest } = require('expo/config-plugins');

module.exports = function withJunseoAndroid(config) {
  return withAndroidManifest(config, (mod) => {
    const app = mod.modResults.manifest.application[0];
    // The subclass keeps Expo's notification handling and refreshes widgets without a JS process.
    app.service ??= [];
    app.service = app.service.filter((s) => s.$['android:name'] !== 'expo.modules.notifications.service.ExpoFirebaseMessagingService');
    app.service.push({ $: {
      'android:name': 'expo.modules.notifications.service.ExpoFirebaseMessagingService',
      'tools:node': 'remove',
    } });
    mod.modResults.manifest.$['xmlns:tools'] = 'http://schemas.android.com/tools';
    return mod;
  });
};
