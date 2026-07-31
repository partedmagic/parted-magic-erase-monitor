// Copyright 2026 Patrick J. Verner
// SPDX-License-Identifier: Apache-2.0

package com.partedmagic.erasemonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.partedmagic.erasemonitor.Session

// ─── Palette (mirrors the erase app / dashboard) ─────────────────────────────
val BgDark = Color(0xFF10151B)
val BgCard = Color(0xFF171C21)
val Accent = Color(0xFF2A7FFF)
val TxtDim = Color(0xFF8A949E)
val OkGreen = Color(0xFF7FE0A7)
val BadRed = Color(0xFFFF9AA2)
val StallOrange = Color(0xFFFFB38F)

@Composable
fun PMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = BgDark,
            surface = BgCard,
            onBackground = Color(0xFFDDE3E8),
            onSurface = Color(0xFFDDE3E8),
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BgDark) { content() }
    }
}

fun statusColors(status: String): Pair<Color, Color> = when (status) {
    "complete" -> Color(0xFF173D2B) to OkGreen
    "failures" -> Color(0xFF4A1F24) to BadRed
    "stalled" -> Color(0xFF5E2317) to StallOrange
    else -> Color(0xFF173A5E) to Color(0xFF8FC7FF)   // erasing
}

@Composable
fun StatusBadge(status: String) {
    val (bg, fg) = statusColors(status)
    Text(
        text = status.uppercase(),
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 2.dp)
    )
}

// ─── Session list ────────────────────────────────────────────────────────────
@Composable
fun SessionList(
    sessions: List<Session>?,
    error: String?,
    onOpen: (Session) -> Unit,
) {
    when {
        error != null -> CenterNote(error)
        sessions == null -> CenterNote("Connecting…")
        sessions.isEmpty() -> CenterNote(
            "No sessions yet.\nStart an erase with this server configured."
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sessions, key = { it.id }) { s ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(s) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.machineLabel,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            StatusBadge(s.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${s.nDone} / ${s.nTotal} drives · " +
                                listOf(s.org, s.workOrder)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { "session ${s.id}" },
                            color = TxtDim, fontSize = 12.sp, maxLines = 1,
                        )
                        Text(
                            s.started.replace("T", " "),
                            color = TxtDim, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

// ─── Session detail ──────────────────────────────────────────────────────────
@Composable
fun SessionDetail(
    session: Session?,
    error: String?,
    onOpenFile: (String) -> Unit,
) {
    when {
        error != null -> CenterNote(error)
        session == null -> CenterNote("Loading…")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            session.machineLabel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            modifier = Modifier.weight(1f),
                        )
                        StatusBadge(session.status)
                    }
                    Text(
                        "serial ${session.hostSerial.ifBlank { "N/A" }} · " +
                            session.standard,
                        color = TxtDim, fontSize = 12.sp,
                    )
                    Text(
                        listOf(session.org, session.workOrder, session.technician)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        color = TxtDim, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFF2A3138))
                }
            }
            val rows = session.manifest.ifEmpty {
                session.done.keys.sorted().map {
                    com.partedmagic.erasemonitor.Drive(it, "", "", "", "")
                }
            }
            items(rows, key = { it.dev }) { d ->
                val done = session.done[d.dev]
                val result = session.results[d.dev] ?: ""
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row {
                        Text(
                            d.dev, fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp, modifier = Modifier.width(120.dp),
                        )
                        Text(
                            d.model, fontSize = 13.sp, maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when {
                                result.contains("fail", true) || done == false ->
                                    "✗ FAILED"
                                result.isNotBlank() || done == true -> "✓ done"
                                else -> "erasing…"
                            },
                            color = when {
                                result.contains("fail", true) || done == false ->
                                    BadRed
                                result.isNotBlank() || done == true -> OkGreen
                                else -> TxtDim
                            },
                            fontSize = 13.sp,
                        )
                    }
                    Text(
                        listOf(d.serial, d.size, d.method)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        color = TxtDim, fontSize = 11.sp,
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Files", fontWeight = FontWeight.SemiBold)
                if (session.files.isEmpty()) {
                    Text(
                        "none yet — uploaded on completion",
                        color = TxtDim, fontSize = 12.sp,
                    )
                }
            }
            items(session.files, key = { "f:$it" }) { f ->
                Text(
                    f, color = Accent, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFile(f) }
                        .padding(vertical = 6.dp),
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ─── Settings ────────────────────────────────────────────────────────────────
@Composable
fun Settings(
    server: String,
    onServer: (String) -> Unit,
    monitoring: Boolean,
    onMonitoring: (Boolean) -> Unit,
    alertFail: Boolean, onAlertFail: (Boolean) -> Unit,
    alertStall: Boolean, onAlertStall: (Boolean) -> Unit,
    alertDone: Boolean, onAlertDone: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = server,
            onValueChange = onServer,
            label = { Text("Report server") },
            placeholder = { Text("IP or hostname[:port]") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "The machine running pmagic_report_server. Default port 8341.",
            color = TxtDim, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        SettingRow(
            "Background monitoring",
            "Polls every 30 s and alerts even when the app is closed. " +
                "Shows a small persistent notification while on.",
            monitoring, onMonitoring,
        )
        HorizontalDivider(color = Color(0xFF2A3138))
        SettingRow("Alert on drive failure", null, alertFail, onAlertFail)
        SettingRow("Alert on stalled session", null, alertStall, onAlertStall)
        SettingRow("Alert on completion", null, alertDone, onAlertDone)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(subtitle, color = TxtDim, fontSize = 12.sp)
            }
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun CenterNote(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = TxtDim)
    }
}
