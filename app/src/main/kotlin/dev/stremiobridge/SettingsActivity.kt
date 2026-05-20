package dev.stremiobridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.stremiobridge.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadSettings()
        setupListeners()
        showInstalledPlayers()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PlayerRouter.PREFS_NAME, Context.MODE_PRIVATE)
        binding.switchAutoForward.isChecked = prefs.getBoolean(PlayerRouter.PREF_AUTO_FORWARD, false)
        binding.switchLogIntents.isChecked = prefs.getBoolean(PlayerRouter.PREF_LOG_INTENTS, true)

        val defaultPlayer = PlayerRouter.getDefaultPlayer(this)
        binding.tvCurrentPlayer.text = "${defaultPlayer.displayName}\n${defaultPlayer.packageName}"
    }

    private fun setupListeners() {
        binding.switchAutoForward.setOnCheckedChangeListener { _, checked ->
            getSharedPreferences(PlayerRouter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PlayerRouter.PREF_AUTO_FORWARD, checked).apply()
            val status = if (checked) "enabled — bridge will forward immediately" else "disabled"
            Toast.makeText(this, "Auto-forward $status", Toast.LENGTH_SHORT).show()
        }

        binding.switchLogIntents.setOnCheckedChangeListener { _, checked ->
            getSharedPreferences(PlayerRouter.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PlayerRouter.PREF_LOG_INTENTS, checked).apply()
        }

        // Install links for players
        binding.btnInstallJustPlayer.setOnClickListener {
            openPlayStore(Player.JUST_PLAYER.packageName)
        }
        binding.btnInstallMpv.setOnClickListener {
            // mpv-android is not on Play Store, link to GitHub
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mpv-android/mpv-android/releases")))
        }
        binding.btnInstallVlc.setOnClickListener {
            openPlayStore(Player.VLC.packageName)
        }
    }

    private fun showInstalledPlayers() {
        val installed = PlayerRouter.getInstalledPlayers(this)
        val sb = StringBuilder()
        Player.entries.forEach { player ->
            val status = if (installed.contains(player)) "✓ Installed" else "✗ Not found"
            sb.appendLine("$status — ${player.displayName}")
        }
        binding.tvPlayerStatus.text = sb.toString().trim()
    }

    private fun openPlayStore(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
