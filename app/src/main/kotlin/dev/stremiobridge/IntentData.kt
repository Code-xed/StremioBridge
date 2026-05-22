package dev.stremiobridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Captures everything in a Stremio external playback intent.
 * Fields are nullable because Stremio may or may not populate them.
 */
data class IntentData(
    // Core playback
    val uri: Uri?,
    val mimeType: String?,
    val action: String?,

    // Stremio-specific extras (documented and discovered)
    val title: String?,
    val position: Long?,           // Playback position in ms
    val subtitleUrl: String?,      // External subtitle URL
    val subtitleLanguage: String?, // Subtitle language code
    val headers: Map<String, String>, // HTTP headers for stream

    // Episode metadata
    val seriesTitle: String?,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val imdbId: String?,
    val releaseYear: Int?,
    val videoId: String?,          // Stremio internal video ID

    // All raw extras for debugging (key → string representation)
    val rawExtras: Map<String, String>,

    // Source package (confirms this came from Stremio)
    val sourcePackage: String?
) {
    companion object {
        /**
         * Parse every possible field from an incoming intent.
         * Logs everything — this is our primary debugging tool.
         */
        fun from(intent: Intent): IntentData {
            val extras = intent.extras
            val rawExtras = mutableMapOf<String, String>()

            // Dump ALL extras regardless of type
            extras?.let { bundle ->
                for (key in bundle.keySet()) {
                    val value = bundle.get(key)
                    rawExtras[key] = value?.toString() ?: "null"
                }
            }

            // Parse headers from Stremio — they send these as a Bundle or serialized string
            val headers = parseHeaders(extras)

            return IntentData(
                uri = intent.data,
                mimeType = intent.type,
                action = intent.action,

                // Try common key names Stremio / media players use
                title = extras?.getString("title")
                    ?: extras?.getString("name")
                    ?: extras?.getString("video_title")
                    ?: extras?.getString("android.intent.extra.TITLE"),

                position = extras?.getLong("position", -1L).takeIf { it != -1L }
                    ?: extras?.getLong("startOffset", -1L).takeIf { it != -1L }
                    ?: extras?.getInt("position", -1).takeIf { it != -1 }?.toLong(),

                subtitleUrl = extras?.getString("subtitleUrl")
                    ?: extras?.getString("subtitle_url")
                    ?: extras?.getString("subs")
                    ?: extras?.getString("subUrl"),

                subtitleLanguage = extras?.getString("subtitleLanguage")
                    ?: extras?.getString("subtitle_language")
                    ?: extras?.getString("subLang"),

                headers = headers,

                seriesTitle = extras?.getString("seriesTitle")
                    ?: extras?.getString("series_title")
                    ?: extras?.getString("show_title"),

                season = extras?.getInt("season", -1).takeIf { it != -1 }
                    ?: extras?.getString("season")?.toIntOrNull(),

                episode = extras?.getInt("episode", -1).takeIf { it != -1 }
                    ?: extras?.getString("episode")?.toIntOrNull(),

                episodeTitle = extras?.getString("episodeTitle")
                    ?: extras?.getString("episode_title"),

                imdbId = extras?.getString("imdbId")
                    ?: extras?.getString("imdb_id")
                    ?: extras?.getString("id"),

                releaseYear = extras?.getInt("year", -1).takeIf { it != -1 }
                    ?: extras?.getString("year")?.toIntOrNull(),

                videoId = extras?.getString("videoId")
                    ?: extras?.getString("video_id")
                    ?: extras?.getString("stremio_id"),

                rawExtras = rawExtras,
                sourcePackage = intent.`package` ?: "unknown"
            )
        }

        private fun parseHeaders(extras: Bundle?): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            if (extras == null) return headers

            // Stremio may send headers as a nested Bundle named "headers"
            val headerBundle = extras.getBundle("headers")
            headerBundle?.let { bundle ->
                for (key in bundle.keySet()) {
                    headers[key] = bundle.getString(key) ?: ""
                }
            }

            // Or as a serialized JSON string
            val headerJson = extras.getString("headers")
            if (headerJson != null && headerJson.startsWith("{")) {
                try {
                    // Simple manual parse to avoid Gson dependency at data layer
                    headerJson
                        .removeSurrounding("{", "}")
                        .split(",")
                        .forEach { pair ->
                            val kv = pair.split(":")
                            if (kv.size == 2) {
                                val k = kv[0].trim().removeSurrounding("\"")
                                val v = kv[1].trim().removeSurrounding("\"")
                                headers[k] = v
                            }
                        }
                } catch (_: Exception) { /* not valid JSON, skip */ }
            }

            return headers
        }
    }

    /** Human-readable summary for the debug screen */
    fun toDisplayString(): String {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════")
        sb.appendLine("  STREMIO BRIDGE — INTENT DUMP")
        sb.appendLine("═══════════════════════════════")
        sb.appendLine()
        sb.appendLine("▶ URI:")
        sb.appendLine("  ${uri ?: "none"}")
        sb.appendLine()
        sb.appendLine("▶ MIME Type: ${mimeType ?: "none"}")
        sb.appendLine("▶ Action: ${action ?: "none"}")
        sb.appendLine("▶ Source: ${sourcePackage ?: "unknown"}")
        sb.appendLine()
        sb.appendLine("── MEDIA METADATA ──────────────")
        sb.appendLine("Title:        ${title ?: "—"}")
        sb.appendLine("Series:       ${seriesTitle ?: "—"}")
        sb.appendLine("Season:       ${season ?: "—"}")
        sb.appendLine("Episode:      ${episode ?: "—"}")
        sb.appendLine("Ep. Title:    ${episodeTitle ?: "—"}")
        sb.appendLine("IMDB ID:      ${imdbId ?: "—"}")
        sb.appendLine("Year:         ${releaseYear ?: "—"}")
        sb.appendLine("Video ID:     ${videoId ?: "—"}")
        sb.appendLine()
        sb.appendLine("── PLAYBACK ─────────────────────")
        sb.appendLine("Position:     ${if (position != null) "${position}ms (${position / 1000}s)" else "—"}")
        sb.appendLine()
        sb.appendLine("── SUBTITLES ────────────────────")
        sb.appendLine("URL:          ${subtitleUrl ?: "—"}")
        sb.appendLine("Language:     ${subtitleLanguage ?: "—"}")
        sb.appendLine()
        sb.appendLine("── HTTP HEADERS ─────────────────")
        if (headers.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            headers.forEach { (k, v) -> sb.appendLine("  $k: $v") }
        }
        sb.appendLine()
        sb.appendLine("── ALL EXTRAS (${rawExtras.size}) ──────────────")
        if (rawExtras.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            rawExtras.entries.sortedBy { it.key }.forEach { (k, v) ->
                sb.appendLine("  [$k]")
                sb.appendLine("  → $v")
            }
        }
        return sb.toString()
    }
}
