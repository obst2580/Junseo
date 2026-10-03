package expo.modules.junseowidget

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetSessionTest {
  private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
  @Before fun reset() { WidgetSession.clear(context) }
  @After fun cleanup() { WidgetSession.clear(context) }

  @Test fun credentialRoundTripUsesAndroidKeystoreAndNeverStoresPlaintext() {
    val value = jwt(3600)
    WidgetSession.setToken(context, value)
    assertEquals(value, WidgetSession.token(context))
    assertFalse(WidgetSession.prefs(context).all.values.any { it.toString().contains(value) })
    assertTrue(WidgetSession.prefs(context).getString("credential", "")!!.contains(":"))
  }

  @Test fun expiredCredentialWipesCachedPhotosAndFriendNames() {
    WidgetSession.setToken(context, jwt(-60))
    File(WidgetSession.directory(context), "photo.jpg").writeText("private photo")
    WidgetSession.prefs(context).edit().putString("friends", "private name").commit()
    assertNull(WidgetSession.token(context))
    assertFalse(File(context.filesDir, "junseo-widget/photo.jpg").exists())
    assertNull(WidgetSession.prefs(context).getString("friends", null))
  }

  @Test fun accountSwitchInvalidatesRunningWorkerAndWipesPreviousCache() {
    WidgetSession.setToken(context, jwt(3600))
    WidgetSession.prefs(context).edit().putLong("userId", 1).putString("apiUrl", "https://example.test").commit()
    val previous = WidgetSession.snapshot(context)!!
    File(WidgetSession.directory(context), "feed-0.json").writeText("old account")
    WidgetSession.setToken(context, jwt(7200))
    WidgetSession.prefs(context).edit().putLong("userId", 2).putString("apiUrl", "https://example.test").commit()
    assertFalse(WidgetSession.matches(context, previous))
    assertFalse(File(context.filesDir, "junseo-widget/feed-0.json").exists())
    assertEquals(2L, WidgetSession.snapshot(context)!!.userId)
  }

  @Test fun logoutAndUnreadableCredentialsFailClosed() {
    WidgetSession.setToken(context, jwt(3600))
    WidgetSession.setToken(context, null)
    assertNull(WidgetSession.token(context))
    WidgetSession.prefs(context).edit().putString("credential", "corrupt").commit()
    assertNull(WidgetSession.token(context))
    assertNull(WidgetSession.prefs(context).getString("credential", null))
  }

  private fun jwt(seconds: Long): String {
    fun encode(value: String) = Base64.encodeToString(value.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    return encode("{\"alg\":\"RS256\"}") + "." + encode(JSONObject().put("exp", System.currentTimeMillis() / 1000 + seconds).toString()) + ".offline-test"
  }
}
