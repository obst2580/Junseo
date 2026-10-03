package expo.modules.junseowidget

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import android.security.keystore.KeyPermanentlyInvalidatedException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One Keystore-encrypted credential is shared by the app and its in-process widget workers. */
internal object WidgetSession {
  data class Snapshot(val token: String, val generation: String, val userId: Long, val apiUrl: String)
  private const val ALIAS = "junseo.widget.auth.v1"
  fun prefs(context: Context) = context.getSharedPreferences("junseo.widget", Context.MODE_PRIVATE)
  fun directory(context: Context) = File(context.filesDir, "junseo-widget").apply { mkdirs() }

  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
      init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
    }.generateKey()
  }

  @Synchronized fun token(context: Context): String? {
    val encoded = prefs(context).getString("credential", null) ?: return null
    return try {
      val parts = encoded.split(":")
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
      val token = String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
      val claims = JSONObject(String(Base64.decode(token.split(".")[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
      if (claims.optLong("exp", 0) <= System.currentTimeMillis() / 1000) {
        clear(context)
        null
      } else token
    } catch (e: Exception) {
      // Wipe only when the stored credential can never be read again (tampered, key gone or invalidated, corrupt).
      // A transient Keystore error (device busy, keystore daemon restart) must not silently sign the person out.
      if (unreadable(e)) clear(context)
      null
    }
  }

  private fun unreadable(e: Exception) = e is AEADBadTagException || e is KeyPermanentlyInvalidatedException ||
    e is IllegalArgumentException || e is IndexOutOfBoundsException || e is org.json.JSONException

  @Synchronized fun setToken(context: Context, value: String?) {
    if (value == null) { clear(context); return }
    if (token(context) == value) return
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
      Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    clear(context)
    check(prefs(context).edit().putString("credential", encrypted).commit()) { "Credential storage failed" }
  }

  @Synchronized fun clear(context: Context) {
    check(prefs(context).edit().clear().putString("generation", UUID.randomUUID().toString()).commit())
    File(context.filesDir, "junseo-widget").deleteRecursively()
  }

  @Synchronized fun snapshot(context: Context): Snapshot? {
    val token = token(context) ?: return null
    val p = prefs(context)
    val userId = p.getLong("userId", 0)
    val apiUrl = p.getString("apiUrl", null) ?: return null
    if (userId <= 0) return null
    return Snapshot(token, p.getString("generation", "")!!, userId, apiUrl)
  }

  @Synchronized fun matches(context: Context, expected: Snapshot) = snapshot(context) == expected
}
