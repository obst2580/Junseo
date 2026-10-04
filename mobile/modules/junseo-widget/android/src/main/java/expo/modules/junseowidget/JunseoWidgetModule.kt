package expo.modules.junseowidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.URI

class JunseoWidgetModule : Module() {
  private val context: Context get() = requireNotNull(appContext.reactContext)

  override fun definition() = ModuleDefinition {
    Name("JunseoWidget")
    AsyncFunction("getTokenAsync") { WidgetSession.token(context) }
    AsyncFunction("setTokenAsync") { token: String? ->
      WidgetSession.setToken(context, token)
      MomentWidgetProvider.renderAll(context)
    }
    Function("signIn") { apiUrl: String, userId: Long ->
      require(URI(apiUrl).scheme == "https") { "Widget API requires HTTPS" }
      synchronized(WidgetSession) {
        val p = WidgetSession.prefs(context)
        if (p.getLong("userId", userId) != userId) WidgetSession.clearAccountData(context)
        check(p.edit().putString("apiUrl", apiUrl.trimEnd('/')).putLong("userId", userId).commit())
      }
      WidgetRefreshWorker.schedule(context)
    }
    Function("signOut") {
      WidgetSession.clear(context)
      WidgetRefreshWorker.cancel(context)
      MomentWidgetProvider.renderAll(context)
    }
    Function("setFriends") { friends: String ->
      WidgetSession.prefs(context).edit().putString("friends", friends).apply()
    }
    Function("reload") { WidgetRefreshWorker.enqueue(context) }
    Function("rememberPushToken") { token: String ->
      WidgetSession.prefs(context).edit().putString("registeredFcmToken", token).putString("pendingFcmToken", token).apply()
    }
    Function("pushToken") { WidgetSession.prefs(context).getString("registeredFcmToken", null) }
    /** Widgets of this app on the home screen (the post-login setup screen waits for the first one). */
    Function("installedCount") {
      AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MomentWidgetProvider::class.java)).size
    }
    Function("requestPin") {
      val manager = AppWidgetManager.getInstance(context)
      manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(
        ComponentName(context, MomentWidgetProvider::class.java), null, null)
    }
  }
}
