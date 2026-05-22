package dev.stremiobridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Known external players with their package names and intent construction logic.
 * Add new players here for future smart routing.
 */
enum class Player(
    val displayName: String,
    val packageName: String,
    val description: String
) {
    JUST_PLAYER(
        displayName = "Just Player",
        packageName = "com.brouken.player",
        description = "FFmpeg-based, best EAC3/DTS/TrueHD support"
    ),
    MPV_ANDROID(
        displayName = "mpv-android",
        packageName = "is.xyz.mpv",
        description = "mpv core, maximum codec/format support"
    ),
    VLC(
        displayName = "VLC",
        packageName = "org.videolan.vlc",
        description = "libVLC, broad format support + Dolby Vision"
    ),
    MX_PLAYER(
        displayName = "MX Player",
        packageName = "com.mxtech.videoplayer.ad",
        description = "MX Player with custom codec packs"
    ),
    MX_PLAYER_PRO(
        displayName = "MX Player Pro",
        packageName = "com.mxtech.videoplayer.pro",
        description = "MX Player Pro"
    );

    companion object {
        fun fromPackage(pkg: String): Player? = entries.firstOrNull { it.packageName == pkg }
    }
}

/**
 * Routing rules for smart player selection.
 * Not implemented yet — architecture is ready for future expansion.
 */
object PlayerRouter {

    /**
     * Build a forwarding Intent for the selected player.
     * Preserves URI, MIME type, and all extras Stremio sent.
     */
    fun buildIntent(intentData: IntentData, targetPlayer: Player): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(intentData.uri, intentData.mimeType ?: "video/*")
            setPackage(targetPlayer.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Forward position if we have it
            intentData.position?.let {
                putExtra("position", it)
                putExtra("startOffset", it) // Just Player uses this
                // mpv uses seek_ms
                putExtra("seek_ms", it)
            }

            // Forward title
            intentData.title?.let { putExtra("title", it) }

            // Forward subtitle URL — Just Player supports this
            intentData.subtitleUrl?.let {
                putExtra("subtitleUrl", it)
                putExtra("subs", it)        // Some players use this key
                putExtra("subUrl", it)
            }
            intentData.subtitleLanguage?.let { putExtra("subtitleLanguage", it) }

            // Forward headers for auth'd streams
            if (intentData.headers.isNotEmpty()) {
                val headerBundle = android.os.Bundle()
                intentData.headers.forEach { (k, v) -> headerBundle.putString(k, v) }
                putExtra("headers", headerBundle)
            }
        }
    }

    /** Get user's preferred default player from SharedPreferences */
    fun getDefaultPlayer(context: Context): Player {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pkg = prefs.getString(PREF_DEFAULT_PLAYER, Player.JUST_PLAYER.packageName)
        return Player.fromPackage(pkg ?: "") ?: Player.JUST_PLAYER
    }

    fun setDefaultPlayer(context: Context, player: Player) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_DEFAULT_PLAYER, player.packageName)
            .apply()
    }

    /** Check which players are actually installed */
    fun getInstalledPlayers(context: Context): List<Player> {
        val pm = context.packageManager
        return Player.entries.filter { player ->
            try {
                pm.getPackageInfo(player.packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    const val PREFS_NAME = "stremio_bridge_prefs"
    const val PREF_DEFAULT_PLAYER = "default_player"
    const val PREF_AUTO_FORWARD = "auto_forward"
    const val PREF_LOG_INTENTS = "log_intents"
}
