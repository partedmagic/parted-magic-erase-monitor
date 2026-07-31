// Copyright 2026 Patrick J. Verner
// SPDX-License-Identifier: Apache-2.0

package com.partedmagic.erasemonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object Notifier {
    const val CH_MONITOR = "monitoring"
    const val CH_ALERTS = "alerts"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CH_MONITOR, "Background monitoring",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Quiet notice shown while monitoring runs" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_ALERTS, "Erase alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Drive failures, stalled sessions, completions" }
        )
    }

    private fun tapIntent(ctx: Context, sessionId: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (sessionId != null) putExtra("session_id", sessionId)
        }
        return PendingIntent.getActivity(
            ctx, sessionId?.hashCode() ?: 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun foreground(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Parted Magic Erase Monitor")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(tapIntent(ctx, null))
            .build()

    fun alert(ctx: Context, id: Int, title: String, text: String, sessionId: String) {
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tapIntent(ctx, sessionId))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

/**
 * Foreground service: polls /api/sessions every POLL_SECONDS, diffs against
 * the last notified state, and raises alerts for new drive failures, newly
 * stalled sessions, and completions.  State persists in Prefs so a service
 * restart does not replay old alerts.
 */
class MonitorService : Service() {

    companion object {
        const val POLL_SECONDS = 30L
        private const val FG_ID = 1

        fun start(ctx: Context) {
            val i = Intent(ctx, MonitorService::class.java)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var nextNoteId = 100
    private var polling = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FG_ID, Notifier.foreground(this, "Connecting…"))
        if (!polling) {
            polling = true
            scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            val base = Prefs.serverBase(this)
            if (base.isEmpty()) {
                updateForeground("No server configured")
            } else {
                try {
                    val sessions = Api.sessions(base)
                    diffAndNotify(sessions)
                    val live = sessions.count { it.status == "erasing" }
                    val stalled = sessions.count { it.status == "stalled" }
                    updateForeground(
                        buildString {
                            append("$live erasing")
                            if (stalled > 0) append(" · $stalled STALLED")
                            append(" · ${sessions.size} total")
                        }
                    )
                } catch (e: Exception) {
                    updateForeground("Server unreachable — on shop Wi-Fi?")
                }
            }
            delay(POLL_SECONDS * 1000)
        }
    }

    private fun updateForeground(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(FG_ID, Notifier.foreground(this, text))
    }

    /**
     * Persisted shape: { "<sid>": {"status": "...", "failed": ["/dev/sdb"],
     * "final": true } }
     */
    private fun diffAndNotify(sessions: List<Session>) {
        val prev = JSONObject(Prefs.lastState(this))
        val next = JSONObject()
        val wantFail = Prefs.alertFailures(this)
        val wantStall = Prefs.alertStalled(this)
        val wantDone = Prefs.alertComplete(this)

        for (s in sessions) {
            val old = prev.optJSONObject(s.id) ?: JSONObject()
            val oldFailed = mutableSetOf<String>()
            old.optJSONArray("failed")?.let { a ->
                for (i in 0 until a.length()) oldFailed.add(a.optString(i))
            }
            val oldStatus = old.optString("status", "")
            val oldFinal = old.optBoolean("final", false)

            val newlyFailed = s.failedDevs - oldFailed
            if (wantFail) {
                for (dev in newlyFailed.sorted()) {
                    Notifier.alert(
                        this, nextNoteId++,
                        "Drive FAILED — ${s.machineLabel}",
                        "$dev failed to erase (session ${s.id})",
                        s.id
                    )
                }
            }

            if (wantStall && s.status == "stalled" && oldStatus != "stalled") {
                Notifier.alert(
                    this, nextNoteId++,
                    "Session STALLED — ${s.machineLabel}",
                    "No traffic for 90s. ${s.nDone}/${s.nTotal} drives had " +
                        "completed; evidence so far is safe on the server.",
                    s.id
                )
            }

            val isFinal = s.status == "complete" || s.status == "failures"
            if (wantDone && isFinal && !oldFinal) {
                val failed = s.failedDevs
                val title =
                    if (failed.isEmpty()) "Erase complete — ${s.machineLabel}"
                    else "Finished with FAILURES — ${s.machineLabel}"
                val text =
                    if (failed.isEmpty()) "All ${s.nTotal} drive(s) passed."
                    else "${failed.size} of ${s.nTotal} drive(s) failed: " +
                        failed.sorted().joinToString(", ")
                Notifier.alert(this, nextNoteId++, title, text, s.id)
            }

            next.put(s.id, JSONObject().apply {
                put("status", s.status)
                put("failed", JSONArray(s.failedDevs.sorted()))
                put("final", isFinal || oldFinal)
            })
        }
        Prefs.setLastState(this, next.toString())
    }
}
