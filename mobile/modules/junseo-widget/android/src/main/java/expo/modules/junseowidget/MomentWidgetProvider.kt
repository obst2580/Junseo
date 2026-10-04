package expo.modules.junseowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MomentWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
    renderAll(context)
    if (WidgetSession.snapshot(context) != null) WidgetRefreshWorker.schedule(context)
  }
  override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
    render(context, id)
  }
  override fun onDeleted(context: Context, ids: IntArray) {
    val editor = WidgetSession.prefs(context).edit()
    ids.forEach { editor.remove("friend_$it").remove("page_$it") }
    editor.apply()
  }
  override fun onDisabled(context: Context) { WidgetRefreshWorker.cancel(context) }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action !in listOf(PREVIOUS, NEXT, REFRESH)) return
    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    val manager = AppWidgetManager.getInstance(context)
    if (id !in manager.getAppWidgetIds(ComponentName(context, MomentWidgetProvider::class.java))) return
    if (intent.action == REFRESH) WidgetRefreshWorker.enqueue(context)
    else synchronized(WidgetSession) {
      val p = WidgetSession.prefs(context)
      p.edit().putInt("page_$id", p.getInt("page_$id", 0) + if (intent.action == NEXT) 1 else -1).apply()
    }
    render(context, id)
  }

  companion object {
    private const val PREVIOUS = "com.junseo.widget.PREVIOUS"
    private const val NEXT = "com.junseo.widget.NEXT"
    private const val REFRESH = "com.junseo.widget.REFRESH"
    fun renderAll(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      manager.getAppWidgetIds(ComponentName(context, MomentWidgetProvider::class.java)).forEach { render(context, it) }
    }

    fun render(context: Context, id: Int) = synchronized(WidgetSession) {
      val manager = AppWidgetManager.getInstance(context)
      val views = RemoteViews(context.packageName, R.layout.junseo_widget)
      val p = WidgetSession.prefs(context)
      val friend = p.getLong("friend_$id", 0)
      val session = WidgetSession.snapshot(context)
      val feed = if (session == null) null else runCatching {
        val cached = JSONObject(File(WidgetSession.directory(context), "feed-$friend.json").readText())
        if (cached.optLong("cachedAt") < System.currentTimeMillis() - 24 * 60 * 60 * 1000) null
        else cached.optJSONArray("items")
      }.getOrNull()
      // The API keeps the newest photo when there are no photos from the last day, as on iOS.
      val items = (0 until (feed?.length() ?: 0)).map { feed!!.getJSONObject(it) }.take(5)
      val page = if (items.isEmpty()) 0 else Math.floorMod(p.getInt("page_$id", 0), items.size)
      val item = items.getOrNull(page)
      views.setViewVisibility(R.id.widget_message, if (item == null) View.VISIBLE else View.GONE)
      views.setViewVisibility(R.id.widget_photo, if (item == null) View.GONE else View.VISIBLE)
      views.setViewVisibility(R.id.widget_previous, if (items.size > 1) View.VISIBLE else View.GONE)
      views.setViewVisibility(R.id.widget_next, if (items.size > 1) View.VISIBLE else View.GONE)
      var route = "junseo://"
      if (item == null) {
        views.setTextViewText(R.id.widget_message,
          if (session == null) "앱에서 로그인해 주세요" else "친구가 사진을 보내면\n여기에 떠요")
        views.setTextViewText(R.id.widget_author, "Junseo")
        views.setTextViewText(R.id.widget_comments, "")
        views.setTextViewText(R.id.widget_page, "")
      } else {
        val moment = item.getJSONObject("moment")
        val momentId = moment.getLong("id")
        views.setTextViewText(R.id.widget_author, moment.getJSONObject("sender").getString("displayName"))
        val bitmap = BitmapFactory.decodeFile(File(WidgetSession.directory(context), "$friend-$momentId.jpg").path)
        if (bitmap != null) views.setImageViewBitmap(R.id.widget_photo, bitmap)
        val large = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) >= 200
        val comments = item.optJSONArray("comments") ?: JSONArray()
        views.setTextViewText(R.id.widget_comments, (0 until minOf(comments.length(), if (large) 2 else 1)).joinToString("\n") {
          val comment = comments.getJSONObject(it)
          "${comment.getString("author")}: ${comment.getString("text")}"
        })
        views.setTextViewText(R.id.widget_page, "${page + 1} / ${items.size}")
        route = "junseo://moments/$momentId"
      }
      val open = Intent(Intent.ACTION_VIEW, Uri.parse(route)).setPackage(context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      views.setOnClickPendingIntent(R.id.widget_photo,
        PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      views.setOnClickPendingIntent(R.id.widget_message,
        PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      listOf(R.id.widget_previous to PREVIOUS, R.id.widget_next to NEXT, R.id.widget_refresh to REFRESH).forEach { (view, action) ->
        val intent = Intent(context, MomentWidgetProvider::class.java).setAction(action)
          .setData(Uri.parse("junseo-widget://$id/$action")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        views.setOnClickPendingIntent(view, PendingIntent.getBroadcast(context, id, intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      }
      val configure = Intent(context, WidgetConfigureActivity::class.java)
        .setData(Uri.parse("junseo-widget://$id/configure")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
      views.setOnClickPendingIntent(R.id.widget_configure, PendingIntent.getActivity(context, id, configure,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      manager.updateAppWidget(id, views)
    }
  }
}
