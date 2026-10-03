package expo.modules.junseowidget

import com.google.firebase.messaging.RemoteMessage
import expo.modules.notifications.service.ExpoFirebaseMessagingService

/** The single FCM entry point preserves Expo's notification taps and adds native widget refreshes. */
class WidgetMessagingService : ExpoFirebaseMessagingService() {
  override fun onMessageReceived(message: RemoteMessage) {
    if (WidgetSession.snapshot(this) == null) return
    WidgetRefreshWorker.enqueue(this)
    if (message.data["type"] != "widget-refresh") super.onMessageReceived(message)
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    WidgetSession.prefs(this).edit().putString("pendingFcmToken", token).apply()
    WidgetRefreshWorker.enqueue(this)
  }
}
