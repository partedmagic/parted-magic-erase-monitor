// Copyright 2026 Patrick J. Verner
// SPDX-License-Identifier: Apache-2.0

package com.partedmagic.erasemonitor

import org.json.JSONArray
import org.json.JSONObject

/** One drive from a session's manifest. */
data class Drive(
    val dev: String,
    val model: String,
    val serial: String,
    val size: String,
    val method: String,
)

/** One erase session as reported by /api/sessions and /api/session/<id>. */
data class Session(
    val id: String,
    val status: String,           // erasing | complete | failures | stalled
    val started: String,
    val ended: String,
    val org: String,
    val workOrder: String,
    val technician: String,
    val standard: String,
    val hostProduct: String,
    val hostManufacturer: String,
    val hostSerial: String,
    val nDone: Int,
    val nTotal: Int,
    val manifest: List<Drive>,
    val done: Map<String, Boolean>,       // dev -> pass/fail (live)
    val results: Map<String, String>,     // dev -> final result text
    val files: List<String>,
) {
    val machineLabel: String
        get() {
            val name = listOf(hostManufacturer, hostProduct)
                .filter { it.isNotBlank() }.joinToString(" ")
            return if (name.isNotBlank()) name else "Session $id"
        }

    val failedDevs: Set<String>
        get() = done.filterValues { !it }.keys +
                results.filterValues { it.contains("fail", ignoreCase = true) }.keys
}

object Parse {

    fun sessions(body: String): List<Session> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { session(arr.getJSONObject(it)) }
    }

    fun session(o: JSONObject): Session {
        val host = o.optJSONObject("host") ?: JSONObject()
        val manifest = o.optJSONArray("manifest") ?: JSONArray()
        val drives = (0 until manifest.length()).map { i ->
            val d = manifest.getJSONObject(i)
            Drive(
                dev = d.optString("dev"),
                model = d.optString("model"),
                serial = d.optString("serial"),
                size = d.optString("size"),
                method = d.optString("method"),
            )
        }
        val done = mutableMapOf<String, Boolean>()
        o.optJSONObject("done")?.let { j ->
            j.keys().forEach { k -> done[k] = j.optBoolean(k) }
        }
        val results = mutableMapOf<String, String>()
        o.optJSONObject("results")?.let { j ->
            j.keys().forEach { k -> results[k] = j.optString(k) }
        }
        val files = mutableListOf<String>()
        o.optJSONArray("files")?.let { j ->
            for (i in 0 until j.length()) files.add(j.optString(i))
        }
        return Session(
            id = o.optString("id"),
            status = o.optString("status", "erasing"),
            started = o.optString("started"),
            ended = o.optString("ended"),
            org = o.optString("org"),
            workOrder = o.optString("work_order"),
            technician = o.optString("technician"),
            standard = o.optString("standard"),
            hostProduct = host.optString("product"),
            hostManufacturer = host.optString("manufacturer"),
            hostSerial = host.optString("serial"),
            nDone = o.optInt("n_done", done.size),
            nTotal = o.optInt("n_total", drives.size),
            manifest = drives,
            done = done,
            results = results,
            files = files,
        )
    }
}
