package expo.modules.junseowidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import org.json.JSONArray

class WidgetConfigureActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(RESULT_CANCELED)
    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
    if (info?.provider?.packageName != packageName) { finish(); return }
    val friends = runCatching { JSONArray(WidgetSession.prefs(this).getString("friends", "[]")) }.getOrDefault(JSONArray())
    val names = mutableListOf("모든 친구")
    val ids = mutableListOf(0L)
    for (i in 0 until friends.length()) {
      names.add(friends.getJSONObject(i).getString("displayName"))
      ids.add(friends.getJSONObject(i).getLong("id"))
    }
    AlertDialog.Builder(this).setTitle("위젯에 표시할 친구")
      .setItems(names.toTypedArray()) { _, which ->
        WidgetSession.prefs(this).edit().putLong("friend_$id", ids[which]).putInt("page_$id", 0).apply()
        MomentWidgetProvider.render(this, id)
        WidgetRefreshWorker.schedule(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
      }.setOnCancelListener { finish() }.show()
  }
}
