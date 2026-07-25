package com.clinic.wanotifybridge.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.clinic.wanotifybridge.util.ActiveSchedule

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

    // --- Schedule -----------------------------------------------------------------
    // Forwarding is ON when nobody is free to answer: outside opening hours, on closed
    // days, and during the peak windows inside opening hours. See [ActiveSchedule].

    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()

    /** Clinic opening time, minutes past midnight, local time. */
    var openMinute: Int
        get() = prefs.getInt(KEY_OPEN, ActiveSchedule.DEFAULT.openMinute)
        set(value) = prefs.edit()
            .putInt(KEY_OPEN, value.coerceIn(0, ActiveSchedule.MINUTES_PER_DAY))
            .apply()

    var closeMinute: Int
        get() = prefs.getInt(KEY_CLOSE, ActiveSchedule.DEFAULT.closeMinute)
        set(value) = prefs.edit()
            .putInt(KEY_CLOSE, value.coerceIn(0, ActiveSchedule.MINUTES_PER_DAY))
            .apply()

    /** Days the clinic is open, as [java.time.DayOfWeek.getValue] (1 = Monday). */
    var openDays: Set<Int>
        get() = prefs.getStringSet(KEY_OPEN_DAYS, null)?.mapNotNull(String::toIntOrNull)?.toSet()
            ?: ActiveSchedule.DEFAULT.openDays
        set(value) = prefs.edit()
            .putStringSet(KEY_OPEN_DAYS, value.map(Int::toString).toSet())
            .apply()

    /** Busy stretches within opening hours, as "HH:MM-HH:MM" lines. */
    var peakWindowsText: String
        get() = prefs.getString(KEY_PEAKS, null)
            ?: ActiveSchedule.formatWindows(ActiveSchedule.DEFAULT.peakWindows)
        set(value) = prefs.edit().putString(KEY_PEAKS, value).apply()

    fun activeSchedule(): ActiveSchedule = ActiveSchedule(
        enabled = scheduleEnabled,
        openMinute = openMinute,
        closeMinute = closeMinute,
        openDays = openDays,
        peakWindows = ActiveSchedule.parseWindows(peakWindowsText),
    )

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

    companion object {
        private const val FILE = "bridge_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_URL = "webhook_url"
        private const val KEY_SECRET = "webhook_secret"
        private const val KEY_FORWARD_GROUPS = "forward_groups"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_OPEN = "clinic_open"
        private const val KEY_CLOSE = "clinic_close"
        private const val KEY_OPEN_DAYS = "clinic_open_days"
        private const val KEY_PEAKS = "peak_windows"
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
