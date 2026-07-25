package com.clinic.wanotifybridge.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.LocalTime

/**
 * All user-configurable state, held in [EncryptedSharedPreferences].
 *
 * The webhook URL and shared secret are credentials: they are never hardcoded, never
 * logged, and excluded from backup (see res/xml/data_extraction_rules.xml).
 */
class BridgeSettings private constructor(private val prefs: SharedPreferences) {

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var webhookUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_URL, value.trim()).apply()

    var webhookSecret: String
        get() = prefs.getString(KEY_SECRET, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SECRET, value.trim()).apply()

    var forwardGroups: Boolean
        get() = prefs.getBoolean(KEY_FORWARD_GROUPS, false)
        set(value) = prefs.edit().putBoolean(KEY_FORWARD_GROUPS, value).apply()

    // --- Business hours -----------------------------------------------------------

    var businessHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOURS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_HOURS_ENABLED, value).apply()

    /** Minutes past midnight, local time. */
    var businessStartMinute: Int
        get() = prefs.getInt(KEY_HOURS_START, 9 * 60)
        set(value) = prefs.edit().putInt(KEY_HOURS_START, value.coerceIn(0, 24 * 60)).apply()

    var businessEndMinute: Int
        get() = prefs.getInt(KEY_HOURS_END, 18 * 60)
        set(value) = prefs.edit().putInt(KEY_HOURS_END, value.coerceIn(0, 24 * 60)).apply()

    /** Days the window applies to, as [java.time.DayOfWeek.getValue] (1 = Monday). */
    var businessDays: Set<Int>
        get() = prefs.getStringSet(KEY_HOURS_DAYS, null)?.mapNotNull(String::toIntOrNull)?.toSet()
            ?: setOf(1, 2, 3, 4, 5, 6)
        set(value) = prefs.edit()
            .putStringSet(KEY_HOURS_DAYS, value.map(Int::toString).toSet())
            .apply()

    // --- Allow / block lists ------------------------------------------------------

    /**
     * Opt-in filter. **Empty means allow everyone** — including numbers that have never
     * messaged the clinic before. Populate it only if you want to narrow forwarding down
     * to a known set of senders.
     */
    var allowList: List<String>
        get() = readList(KEY_ALLOW)
        set(value) = writeList(KEY_ALLOW, value)

    /** Always wins over the allow-list. */
    var blockList: List<String>
        get() = readList(KEY_BLOCK)
        set(value) = writeList(KEY_BLOCK, value)

    private fun readList(key: String): List<String> =
        prefs.getString(key, "").orEmpty()
            .split('\n')
            .map(String::trim)
            .filter(String::isNotEmpty)

    private fun writeList(key: String, value: List<String>) {
        prefs.edit().putString(key, value.joinToString("\n")).apply()
    }

    // --- Derived checks -----------------------------------------------------------

    fun isConfigured(): Boolean = webhookUrl.startsWith("https://") && webhookSecret.isNotEmpty()

    fun businessWindow(): Pair<LocalTime, LocalTime> =
        LocalTime.ofSecondOfDay(businessStartMinute * 60L) to
            LocalTime.ofSecondOfDay((businessEndMinute % (24 * 60)) * 60L)

    companion object {
        private const val FILE = "bridge_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_URL = "webhook_url"
        private const val KEY_SECRET = "webhook_secret"
        private const val KEY_FORWARD_GROUPS = "forward_groups"
        private const val KEY_HOURS_ENABLED = "hours_enabled"
        private const val KEY_HOURS_START = "hours_start"
        private const val KEY_HOURS_END = "hours_end"
        private const val KEY_HOURS_DAYS = "hours_days"
        private const val KEY_ALLOW = "allow_list"
        private const val KEY_BLOCK = "block_list"

        @Volatile
        private var instance: BridgeSettings? = null

        fun get(context: Context): BridgeSettings =
            instance ?: synchronized(this) { instance ?: create(context).also { instance = it } }

        private fun create(context: Context): BridgeSettings {
            val appContext = context.applicationContext
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                appContext,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return BridgeSettings(prefs)
        }
    }
}
