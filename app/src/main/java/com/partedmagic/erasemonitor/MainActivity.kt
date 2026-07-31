// Copyright 2026 Patrick J. Verner
// SPDX-License-Identifier: Apache-2.0

package com.partedmagic.erasemonitor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.partedmagic.erasemonitor.ui.PMTheme
import com.partedmagic.erasemonitor.ui.SessionDetail
import com.partedmagic.erasemonitor.ui.SessionList
import com.partedmagic.erasemonitor.ui.Settings
import com.partedmagic.erasemonitor.ui.TxtDim
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val deepLinkSession = intent.getStringExtra("session_id")
        setContent { PMTheme { App(deepLinkSession) } }
    }

    @Composable
    private fun App(deepLinkSession: String?) {
        var screen by remember { mutableStateOf(if (Prefs.serverRaw(this).isBlank()) "settings" else "list") }
        var openSession by remember { mutableStateOf(deepLinkSession) }
        if (openSession != null && screen == "list") screen = "detail"

        var sessions by remember { mutableStateOf<List<Session>?>(null) }
        var listError by remember { mutableStateOf<String?>(null) }
        var detail by remember { mutableStateOf<Session?>(null) }
        var detailError by remember { mutableStateOf<String?>(null) }
        var refreshTick by remember { mutableIntStateOf(0) }

        // settings state
        var server by remember { mutableStateOf(Prefs.serverRaw(this)) }
        var monitoring by remember { mutableStateOf(Prefs.monitoring(this)) }
        var aFail by remember { mutableStateOf(Prefs.alertFailures(this)) }
        var aStall by remember { mutableStateOf(Prefs.alertStalled(this)) }
        var aDone by remember { mutableStateOf(Prefs.alertComplete(this)) }

        // Poll the list while it is on screen.
        LaunchedEffect(screen, refreshTick) {
            if (screen != "list") return@LaunchedEffect
            while (true) {
                val base = Prefs.serverBase(this@MainActivity)
                if (base.isEmpty()) {
                    listError = "No server configured — open Settings."
                } else {
                    try {
                        sessions = Api.sessions(base)
                        listError = null
                    } catch (e: Exception) {
                        listError =
                            "Server unreachable — are you on shop Wi-Fi?\n($base)"
                    }
                }
                delay(5000)
            }
        }

        // Poll the open session while its detail is on screen.
        LaunchedEffect(screen, openSession, refreshTick) {
            val sid = openSession
            if (screen != "detail" || sid == null) return@LaunchedEffect
            while (true) {
                val base = Prefs.serverBase(this@MainActivity)
                if (base.isEmpty()) {
                    detailError = "No server configured."
                } else {
                    try {
                        detail = Api.session(base, sid)
                        detailError = null
                    } catch (e: Exception) {
                        detailError = "Could not load session $sid."
                    }
                }
                delay(5000)
            }
        }

        BackHandler(enabled = screen != "list") {
            if (screen == "detail") {
                openSession = null; detail = null; detailError = null
            }
            screen = "list"
        }

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopBar(
                screen = screen,
                onBack = {
                    if (screen == "detail") {
                        openSession = null; detail = null; detailError = null
                    }
                    screen = "list"
                },
                onSettings = { screen = "settings" },
                onRefresh = { refreshTick++ },
            )
            when (screen) {
                "list" -> SessionList(sessions, listError) { s ->
                    openSession = s.id
                    detail = s          // instant paint; poller refreshes it
                    screen = "detail"
                }
                "detail" -> SessionDetail(detail, detailError) { name ->
                    val sid = openSession ?: return@SessionDetail
                    val url = Api.fileUrl(Prefs.serverBase(this@MainActivity), sid, name)
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                "settings" -> Settings(
                    server = server,
                    onServer = { server = it; Prefs.setServer(this@MainActivity, it) },
                    monitoring = monitoring,
                    onMonitoring = {
                        monitoring = it
                        Prefs.setMonitoring(this@MainActivity, it)
                        if (it) MonitorService.start(this@MainActivity)
                        else MonitorService.stop(this@MainActivity)
                    },
                    alertFail = aFail,
                    onAlertFail = { aFail = it; Prefs.setAlertFailures(this@MainActivity, it) },
                    alertStall = aStall,
                    onAlertStall = { aStall = it; Prefs.setAlertStalled(this@MainActivity, it) },
                    alertDone = aDone,
                    onAlertDone = { aDone = it; Prefs.setAlertComplete(this@MainActivity, it) },
                )
            }
        }
    }

    @Composable
    private fun TopBar(
        screen: String,
        onBack: () -> Unit,
        onSettings: () -> Unit,
        onRefresh: () -> Unit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            if (screen != "list") {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back", tint = TxtDim,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = if (screen == "list") 12.dp else 0.dp)) {
                Text(
                    when (screen) {
                        "settings" -> "Settings"
                        "detail" -> "Session"
                        else -> "Parted Magic"
                    },
                    fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                )
                if (screen == "list") {
                    Text("Live erase sessions", color = TxtDim, fontSize = 12.sp)
                }
            }
            if (screen == "list") {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TxtDim)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TxtDim)
                }
            }
        }
    }
}
