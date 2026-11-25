package com.lagidconnect.wallet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * What the wallet remembers after linking, stored encrypted.
 * The LagID stays on the phone: the server keeps only a hash,
 * and the phone sends the LagID back when the resident
 * approves a request.
 */
class Session(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "lagid_wallet_session",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val isLinked: Boolean
        get() = deviceId != null && lagId != null

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE, null)
        set(value) = prefs.edit().putString(KEY_DEVICE, value).apply()

    var lagId: String?
        get() = prefs.getString(KEY_LAG_ID, null)
        set(value) = prefs.edit().putString(KEY_LAG_ID, value).apply()

    var displayName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var lagIdLast4: String?
        get() = prefs.getString(KEY_LAST4, null)
        set(value) = prefs.edit().putString(KEY_LAST4, value).apply()

    /** Enrolled face template (base64). Never leaves the phone at rest. */
    var faceTemplate: String?
        get() = prefs.getString(KEY_FACE, null)
        set(value) = prefs.edit().putString(KEY_FACE, value).apply()

    fun save(link: LinkResult, lagId: String) {
        prefs.edit()
            .putString(KEY_DEVICE, link.deviceId)
            .putString(KEY_LAG_ID, lagId)
            .putString(KEY_NAME, link.displayName)
            .putString(KEY_LAST4, link.lagIdLast4)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_DEVICE = "device_id"
        const val KEY_LAG_ID = "lag_id"
        const val KEY_NAME = "display_name"
        const val KEY_LAST4 = "lag_id_last4"
        const val KEY_FACE = "face_template"
    }
}
