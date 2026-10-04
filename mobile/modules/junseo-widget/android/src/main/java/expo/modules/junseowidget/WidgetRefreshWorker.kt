package expo.modules.junseowidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.*
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
  override fun doWork(): Result {
    val c = applicationContext
    val session = WidgetSession.snapshot(c)
    if (session == null) { MomentWidgetProvider.renderAll(c); return Result.success() }
    val p = WidgetSession.prefs(c)
    val pendingToken = p.getString("pendingFcmToken", null)
    if (pendingToken != null && pendingToken != p.getString("registeredFcmToken", null)) {
      try {
        val body = JSONObject().put("token", pendingToken).put("kind", "app")
          .put("environment", "production").put("platform", "android").toString().toByteArray()
        val response = request(session.apiUrl + "/api/devices", session.token, body = body)
        synchronized(WidgetSession) {
          if (response.first == 204 && WidgetSession.matches(c, session)) {
            p.edit().putString("registeredFcmToken", pendingToken).apply()
          }
        }
      } catch (_: Exception) { /* Foreground registration retries on the next app launch. */ }
    }
    val ids = AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, MomentWidgetProvider::class.java))
    val friends = ids.map { WidgetSession.prefs(c).getLong("friend_$it", 0) }.toSet()
    var retry = false
    for (friend in friends) {
      if (!WidgetSession.matches(c, session)) break
      try {
        val path = "/api/widget/feed" + if (friend > 0) "?from=$friend" else ""
        val response = request(session.apiUrl + path, session.token)
        if (response.first == 401) {
          synchronized(WidgetSession) { if (WidgetSession.matches(c, session)) WidgetSession.clear(c) }
          break
        }
        val feed = if (response.first == 204 || response.first == 403 || response.first == 404) {
          JSONObject().put("items", org.json.JSONArray())
        } else {
          check(response.first == 200)
          JSONObject(String(response.second, Charsets.UTF_8))
        }
        val items = feed.optJSONArray("items") ?: org.json.JSONArray()
        val pictures = mutableMapOf<Long, ByteArray>()
        for (i in 0 until minOf(items.length(), 5)) {
          val moment = items.getJSONObject(i).getJSONObject("moment")
          val url = moment.getString("thumbUrl")
          val base = URI(session.apiUrl)
          val target = base.resolve(url)
          require(target.scheme == "https" && target.host == base.host && target.port == base.port)
          val image = request(target.toString(), null, 2 * 1024 * 1024)
          check(image.first == 200)
          pictures[moment.getLong("id")] = image.second
        }
        synchronized(WidgetSession) {
          if (WidgetSession.matches(c, session)) {
            val dir = WidgetSession.directory(c)
            for ((id, bytes) in pictures) {
              val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
              val options = BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 512)
              }
              val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: continue
              File(dir, "$friend-$id.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
              bitmap.recycle()
            }
            feed.put("cachedAt", System.currentTimeMillis())
            File(dir, "feed-$friend.json").writeText(feed.toString())
          }
        }
      } catch (_: Exception) { retry = true }
    }
    synchronized(WidgetSession) {
      // Private images no longer referenced by any current widget feed are removed.
      if (WidgetSession.matches(c, session)) {
      val dir = WidgetSession.directory(c)
      val active = friends.flatMap { friend ->
        val feed = runCatching { JSONObject(File(dir, "feed-$friend.json").readText()).getJSONArray("items") }.getOrNull()
        (0 until (feed?.length() ?: 0)).map { "$friend-${feed!!.getJSONObject(it).getJSONObject("moment").getLong("id")}.jpg" }
      }.toSet()
      dir.listFiles()?.filter { it.extension == "jpg" && it.name !in active }?.forEach { it.delete() }
      }
    }
    MomentWidgetProvider.renderAll(c)
    return if (retry && runAttemptCount < 3) Result.retry() else Result.success()
  }

  private fun request(url: String, token: String?, limit: Int = 256 * 1024, body: ByteArray? = null): Pair<Int, ByteArray> {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 8000
    connection.readTimeout = 8000
    connection.instanceFollowRedirects = false
    connection.setRequestProperty("X-Widget-Source", "android-widget")
    token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
    if (body != null) {
      connection.requestMethod = "PUT"
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "application/json")
      connection.outputStream.use { it.write(body) }
    }
    return try {
      val status = connection.responseCode
      val bytes = if (status == 200) connection.inputStream.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() <= limit) {
          val count = stream.read(buffer)
          if (count < 0) break
          output.write(buffer, 0, count)
        }
        output.toByteArray()
      } else ByteArray(0)
      check(bytes.size <= limit)
      status to bytes
    } finally { connection.disconnect() }
  }

  companion object {
    private const val PERIODIC = "junseo-widget-periodic"
    private const val IMMEDIATE = "junseo-widget-refresh"
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    /**
     * At most one refresh waits behind the running one. A waiting refresh fetches everything new when it runs, so more
     * pushes (or app foregrounds) while offline or in retry backoff must not pile up runs that then go back to back.
     */
    @Synchronized fun enqueue(context: Context) {
      val manager = WorkManager.getInstance(context)
      val waiting = try {
        manager.getWorkInfosForUniqueWork(IMMEDIATE).get(2, TimeUnit.SECONDS)
          .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
      } catch (_: Exception) { false }
      if (waiting) return
      manager.enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.APPEND_OR_REPLACE,
        OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setConstraints(constraints()).build())
    }
    fun schedule(context: Context) {
      if (WidgetSession.snapshot(context) == null) {
        cancel(context)
        MomentWidgetProvider.renderAll(context)
        return
      }
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).setConstraints(constraints()).build())
      enqueue(context)
    }
    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
      WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE)
    }
  }
}
