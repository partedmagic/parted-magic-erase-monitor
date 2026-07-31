// Copyright 2026 Patrick J. Verner
// SPDX-License-Identifier: Apache-2.0

package com.partedmagic.erasemonitor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP client for the PM Report Server's read-only JSON API. */
object Api {

    class ApiException(message: String) : Exception(message)

    private suspend fun get(base: String, path: String): String =
        withContext(Dispatchers.IO) {
            val conn = URL(base + path).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 4000
                conn.readTimeout = 6000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                if (code != 200) throw ApiException("HTTP $code from $path")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

    suspend fun sessions(base: String): List<Session> =
        Parse.sessions(get(base, "/api/sessions"))

    suspend fun session(base: String, id: String): Session =
        Parse.session(org.json.JSONObject(get(base, "/api/session/$id")))

    fun fileUrl(base: String, sessionId: String, name: String): String =
        "$base/files/$sessionId/${java.net.URLEncoder.encode(name, "UTF-8")}"
}

/** Settings, persisted in SharedPreferences. */
object Prefs {
    private const val FILE = "pm_report_monitor"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Normalize what the user typed into a base URL, the same way the
     * erase script's Reporter does: prepend http://, drop any path,
     * default port 8341, keep [IPv6] literals intact.
     */
    fun normalize(raw: String): String {
        var t = raw.trim()
        if (t.isEmpty()) return ""
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "http://$t"
        val scheme = t.substringBefore("//") + "//"
        var rest = t.substringAfter("//").substringBefore("/")
        if (rest.isEmpty()) return ""
        rest = if (rest.startsWith("[")) {
            if (rest.contains("]:")) rest else "$rest:8341"
        } else {
            if (rest.contains(":")) rest else "$rest:8341"
        }
        return scheme + rest
    }

    fun serverRaw(ctx: Context): String = sp(ctx).getString("server", "") ?: ""
    fun serverBase(ctx: Context): String = normalize(serverRaw(ctx))
    fun setServer(ctx: Context, v: String) =
        sp(ctx).edit().putString("server", v.trim()).apply()

    fun monitoring(ctx: Context): Boolean = sp(ctx).getBoolean("monitoring", false)
    fun setMonitoring(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("monitoring", v).apply()

    fun alertFailures(ctx: Context): Boolean = sp(ctx).getBoolean("alert_fail", true)
    fun setAlertFailures(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("alert_fail", v).apply()

    fun alertStalled(ctx: Context): Boolean = sp(ctx).getBoolean("alert_stall", true)
    fun setAlertStalled(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("alert_stall", v).apply()

    fun alertComplete(ctx: Context): Boolean = sp(ctx).getBoolean("alert_done", true)
    fun setAlertComplete(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("alert_done", v).apply()

    /** Last notified state, so restarts do not re-fire old alerts. */
    fun lastState(ctx: Context): String = sp(ctx).getString("last_state", "{}") ?: "{}"
    fun setLastState(ctx: Context, v: String) =
        sp(ctx).edit().putString("last_state", v).apply()
}
