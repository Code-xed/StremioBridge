package dev.stremiobridge

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.stremiobridge.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentIntentData: IntentData? = null
    private var selectedPlayer: Player = Player.JUST_PLAYER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        selectedPlayer = PlayerRouter.getDefaultPlayer(this)
        setupButtons()

        // If we were launched with a video intent, process it immediately
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action
        val uri = intent.data

        // Check if this is a real video playback intent (not our own launcher)
        val isVideoIntent = action == Intent.ACTION_VIEW && uri != null

        if (isVideoIntent) {
            Log.i("StremioBridge", "Received video intent: $uri")

            val intentData = IntentData.from(intent)
            currentIntentData = intentData

            // Log to file and Logcat
            IntentLogger.log(this, intentData)

            // Show the debug screen
            displayIntentData(intentData)

            // Auto-forward if user has enabled it
            val prefs = getSharedPreferences(PlayerRouter.PREFS_NAME, Context.MODE_PRIVATE)
            val autoForward = prefs.getBoolean(PlayerRouter.PREF_AUTO_FORWARD, false)
            if (autoForward) {
                forwardToPlayer(intentData, selectedPlayer)
            }
        } else {
            // Launched from app drawer — show idle/home screen
            showIdleScreen()
        }
    }

    private fun displayIntentData(data: IntentData) {
        binding.layoutIdle.visibility = View.GONE
        binding.layoutIntentDump.visibility = View.VISIBLE

        // Header summary card
        binding.tvStreamUrl.text = data.uri?.toString() ?: "No URI"
        binding.tvMimeType.text = data.mimeType ?: "No MIME type"
        binding.tvTitle.text = data.title ?: "Unknown title"
        binding.tvPosition.text = if (data.position != null) {
            "${data.position / 1000}s (${data.position}ms)"
        } else "No position"
        binding.tvSubtitle.text = data.subtitleUrl ?: "No subtitle URL"
        binding.tvImdbId.text = data.imdbId ?: "—"

        // Episode info
        if (data.seriesTitle != null || data.season != null) {
            binding.tvEpisodeInfo.visibility = View.VISIBLE
            val ep = buildString {
                data.seriesTitle?.let { append(it) }
                if (data.season != null && data.episode != null) {
                    append(" S${data.season.toString().padStart(2,'0')}E${data.episode.toString().padStart(2,'0')}")
                }
                data.episodeTitle?.let { append(" – $it") }
            }
            binding.tvEpisodeInfo.text = ep
        } else {
            binding.tvEpisodeInfo.visibility = View.GONE
        }

        // Full raw dump
        binding.tvRawDump.text = data.toDisplayString()

        // Update forward button label
        updateForwardButton()
    }

    private fun showIdleScreen() {
        binding.layoutIdle.visibility = View.VISIBLE
        binding.layoutIntentDump.visibility = View.GONE
    }

    private fun setupButtons() {
        // Primary forward button
        binding.btnForwardPlayer.setOnClickListener {
            currentIntentData?.let { data ->
                forwardToPlayer(data, selectedPlayer)
            } ?: Toast.makeText(this, "No intent received yet", Toast.LENGTH_SHORT).show()
        }

        // Player selector
        binding.btnChangePlayer.setOnClickListener {
            showPlayerPicker()
        }

        // Copy raw dump to clipboard
        binding.btnCopyDump.setOnClickListener {
            currentIntentData?.let { data ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Intent Dump", data.toDisplayString())
                )
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        // Share raw dump
        binding.btnShareDump.setOnClickListener {
            currentIntentData?.let { data ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, data.toDisplayString())
                    putExtra(Intent.EXTRA_SUBJECT, "Stremio Bridge Intent Dump")
                }
                startActivity(Intent.createChooser(shareIntent, "Share Intent Dump"))
            }
        }

        // View log history (idle screen)
        binding.btnViewLogsIdle.setOnClickListener {
            showLogList()
        }

        // View log history (intent dump screen)
        binding.btnViewLogs.setOnClickListener {
            showLogList()
        }

        // Settings
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun forwardToPlayer(data: IntentData, player: Player) {
        Log.i("StremioBridge", "Forwarding to ${player.displayName} (${player.packageName})")

        val forwardIntent = PlayerRouter.buildIntent(data, player)

        try {
            startActivity(forwardIntent)
            Log.i("StremioBridge", "Forward successful")
            Toast.makeText(this, "▶ Forwarded to ${player.displayName}", Toast.LENGTH_SHORT).show()
        } catch (e: ActivityNotFoundException) {
            Log.e("StremioBridge", "${player.displayName} not found: ${e.message}")
            showPlayerNotFoundDialog(player)
        } catch (e: Exception) {
            Log.e("StremioBridge", "Forward failed: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPlayerPicker() {
        val players = Player.entries.toTypedArray()
        val installed = PlayerRouter.getInstalledPlayers(this)
        val names = players.map { player ->
            val status = if (installed.contains(player)) "✓" else "✗"
            "$status ${player.displayName} — ${player.description}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Target Player")
            .setSingleChoiceItems(names, players.indexOf(selectedPlayer)) { dialog, which ->
                selectedPlayer = players[which]
                PlayerRouter.setDefaultPlayer(this, selectedPlayer)
                updateForwardButton()
                dialog.dismiss()
                Toast.makeText(this, "Player set to ${selectedPlayer.displayName}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPlayerNotFoundDialog(player: Player) {
        AlertDialog.Builder(this)
            .setTitle("${player.displayName} Not Installed")
            .setMessage("${player.displayName} (${player.packageName}) is not installed.\n\nInstall it from the Play Store?")
            .setPositiveButton("Open Play Store") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=${player.packageName}")))
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=${player.packageName}")))
                }
            }
            .setNegativeButton("Pick Another Player") { _, _ ->
                showPlayerPicker()
            }
            .show()
    }

    private fun showLogList() {
        val logs = IntentLogger.getLogs(this)
        if (logs.isEmpty()) {
            Toast.makeText(this, "No saved logs yet", Toast.LENGTH_SHORT).show()
            return
        }
        val names = logs.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Saved Intent Logs (${logs.size})")
            .setItems(names) { _, which ->
                val content = IntentLogger.readLog(logs[which])
                showLogContent(logs[which].name, content)
            }
            .setNegativeButton("Clear All") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Clear all logs?")
                    .setPositiveButton("Yes") { _, _ ->
                        IntentLogger.clearLogs(this)
                        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
            .show()
    }

    private fun showLogContent(name: String, content: String) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(content)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Log", content))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun updateForwardButton() {
        val installed = PlayerRouter.getInstalledPlayers(this)
        val isInstalled = installed.contains(selectedPlayer)
        val status = if (isInstalled) "▶" else "⚠"
        binding.btnForwardPlayer.text = "$status Open in ${selectedPlayer.displayName}"
        binding.tvSelectedPlayer.text = selectedPlayer.displayName
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_clear_log -> {
                IntentLogger.clearLogs(this)
                Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
