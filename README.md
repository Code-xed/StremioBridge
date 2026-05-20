# Stremio Bridge

A lightweight Android middleware app that intercepts Stremio external playback intents, logs all metadata, and forwards playback to your preferred external player (Just Player, mpv-android, VLC, etc.).

```
Stremio → Stremio Bridge → Just Player / mpv / VLC
```

## Why

Stremio's built-in player uses ExoPlayer which lacks native EAC3/DTS/TrueHD decoding. External players like Just Player (FFmpeg-based) handle these codecs natively. This bridge:

- Makes the `Open with` dialog appear when Stremio launches external playback
- Dumps **every byte of metadata** Stremio sends (for investigation)
- Forwards the intent to your chosen player with all extras preserved
- Logs past sessions for comparison

---

## Quick Setup

### 1. Build the APK

**Option A — GitHub Actions (recommended, no Android Studio needed)**
1. Fork this repo on GitHub
2. Push any commit to `main`
3. Go to **Actions → Build APK → Artifacts** → download the APK
4. Sideload onto your Android device

**Option B — Android Studio**
```
File → Open → select StremioBridge folder
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

**Option C — Termux (on-device)**
```bash
# Install required packages
pkg install openjdk-17 gradle

# Clone your repo
git clone https://github.com/yourname/StremioBridge
cd StremioBridge

# Build
chmod +x gradlew
./gradlew assembleDebug

# APK location
ls app/build/outputs/apk/debug/
```

### 2. Install the APK
```bash
# via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# or just tap the APK file on your phone (enable Unknown Sources)
```

### 3. Configure Stremio
```
Stremio → ⚙ Settings → Player → External Player
→ Always → select "Stremio Bridge"
```

### 4. Test
Play any video in Stremio. The bridge opens, showing all intent data. Tap **Open in Just Player**.

---

## Project Structure

```
StremioBridge/
├── app/src/main/
│   ├── AndroidManifest.xml          ← Intent filters (critical)
│   ├── kotlin/dev/stremiobridge/
│   │   ├── MainActivity.kt          ← Core bridge + debug UI
│   │   ├── SettingsActivity.kt      ← Player config + auto-forward
│   │   ├── IntentData.kt            ← Parses ALL Stremio extras
│   │   ├── PlayerConfig.kt          ← Player list + routing logic
│   │   └── IntentLogger.kt          ← File + Logcat logging
│   └── res/
│       ├── layout/activity_main.xml
│       └── layout/activity_settings.xml
├── .github/workflows/android.yml    ← CI/CD APK builds
└── README.md
```

---

## Debugging Intent Contents

### Method 1: Debug Screen (UI)
The app shows everything received from Stremio on-screen. Use **Copy Dump** or **Share Dump** to export.

### Method 2: adb logcat
```bash
adb logcat | grep StremioBridge
```

Every received intent is logged with tag `StremioBridge`. You'll see all extras listed.

### Method 3: Saved Logs
The app saves every received intent as JSON to app-private storage. Access via **Log History** in the app.

---

## What Stremio Sends (Research Notes)

This is what we know and are investigating:

| Extra Key        | Type   | Notes                                      |
|-----------------|--------|--------------------------------------------|
| (URI)           | Uri    | The actual stream URL (http/https/magnet)  |
| (MIME)          | String | Usually `video/*` or specific type         |
| `title`         | String | Media title (may or may not be present)    |
| `position`      | Long   | Resume position in ms (unconfirmed)        |
| `subtitleUrl`   | String | External subtitle URL (SRT/WebVTT)         |
| `headers`       | Bundle | HTTP headers like User-Agent, auth tokens  |

**These key names are not officially documented.** The whole point of this bridge is to discover the real names. Run the app against Stremio and check the dump — report findings in Issues.

---

## Player Support Matrix

| Player         | Package                    | EAC3 | DTS | TrueHD | Subs | Notes                    |
|---------------|---------------------------|------|-----|--------|------|--------------------------|
| Just Player   | `com.brouken.player`      | ✓    | ✓   | ✓      | ✓    | FFmpeg — best codec pick |
| mpv-android   | `is.xyz.mpv`              | ✓    | ✓   | ✓      | ✓    | mpv core, most powerful  |
| VLC           | `org.videolan.vlc`        | ✓    | ✓   | partial| ✓    | libVLC, Dolby Vision ok  |
| MX Player     | `com.mxtech.videoplayer.ad`| ✓   | ✓   | ✗      | ✓    | Needs custom codec pack  |

**Recommendation:** Install Just Player first. It already bundles FFmpeg extensions and is open source.

---

## Forwarded Extras to Just Player

When forwarding, the bridge passes:

```kotlin
intent.setDataAndType(uri, mimeType)
intent.setPackage("com.brouken.player")
intent.putExtra("position", positionMs)      // resume position
intent.putExtra("startOffset", positionMs)   // Just Player key
intent.putExtra("title", title)
intent.putExtra("subtitleUrl", subtitleUrl)
intent.putExtra("headers", headersBundle)    // auth headers
```

---

## Settings

| Setting       | Default | Description                                          |
|--------------|---------|------------------------------------------------------|
| Auto-Forward  | OFF     | Skip debug screen, forward to player immediately     |
| Log Intents   | ON      | Persist each intent dump to app-private JSON file    |

Keep Auto-Forward **OFF** while investigating what Stremio sends. Enable it once you're satisfied the forwarding works correctly.

---

## Future Roadmap (Architecture Ready)

`PlayerConfig.kt → PlayerRouter.smartRoute()` is the hook for:

- **Codec-based routing** — detect EAC3/DTS from URL or metadata → route to Just Player
- **Content-based routing** — anime (via IMDB genre) → mpv
- **Dolby Vision** → VLC
- **Playback tracking** — report position back to Stremio's continue-watching
- **Next episode autoplay** — detect end-of-file, fire next episode intent
- **Subtitle preference memory** — remember language preferences per show
- **Intro skip** — detect chapter markers or fixed-time skip rules

None of these are active yet. The `IntentData` model already captures everything needed to implement them.

---

## Building a Release APK

The GitHub Actions workflow builds both debug and release (unsigned) APKs.

To sign for distribution, add secrets to your GitHub repo:
```
KEYSTORE_BASE64    ← base64-encoded .jks file
KEY_ALIAS
KEY_PASSWORD
STORE_PASSWORD
```

Then add signing config to `app/build.gradle`.

---

## Contributing

Found new extras Stremio sends? Open an issue with your Intent Dump. The main unknown right now is:
- Exact subtitle URL key name
- Exact position key name  
- Whether headers are sent as Bundle or JSON string
- Any episode/IMDB metadata keys

Every dump from a different Stremio version helps.
