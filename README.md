# UploadGo — Select. Share. Done.

UploadGo is a modern Android media-picker, ZIP content viewer, and Android
Sharesheet helper. It lets you select photos, videos and ZIP archives, preview
them, and hand them to **any** compatible app (Telegram, WhatsApp, Google Drive,
Quick Share, …) through Android's standard share mechanism.

> **Important:** UploadGo has **no** Telegram Bot, **no** Telegram API, **no**
> login, **no** OTP, and **no** custom Telegram integration. It never uploads
> anything itself and never claims to know Telegram's upload progress or
> delivery status. The user's existing Telegram app performs the final send.

---

## Feature overview

| Area | What's implemented |
| --- | --- |
| File picking | Native Storage Access Framework picker (`ACTION_OPEN_DOCUMENT`), multi-select of JPG/JPEG/PNG/WEBP/GIF, MP4/MOV/MKV, ZIP |
| Multi-select | Checkboxes, Select All / Clear All, `Selected: N files` counter |
| Media grid | Adaptive lazy grid + list view, thumbnails (Coil for images, downscaled video frames), filename / type / size |
| Image preview | Full-screen viewer: pinch-zoom, pan, swipe between images, double-tap zoom, filename + size |
| Video preview | ExoPlayer/Media3 streaming: play, pause, seek, duration, full-screen, filename + size — never loads whole files into RAM |
| ZIP support | Secure streaming extraction to app cache, media thumbnails, per-file selection, Select All / Clear All, unsupported-file reporting |
| Sharing | `ACTION_SEND` / `ACTION_SEND_MULTIPLE` + `EXTRA_STREAM` + `FLAG_GRANT_READ_URI_PERMISSION`, native Sharesheet, `FileProvider` `content://` URIs |
| Queue | Prepared files, ZIP extraction tasks, recently shared batches — no fake upload progress |
| History | Persistent Room-backed log of "prepared / shared via Android Sharesheet" events, grouped by day |
| Settings | Default share behavior, auto-extract ZIP, show unsupported, temp-file cleanup, theme (System/Light/Dark), storage usage + clear |
| Design | Jetpack Compose + Material 3, deep-blue/indigo palette, light + dark + dynamic color, adaptive icons, splash screen, accessibility |

---

## Requirements

- **Android Studio** (Koala or newer recommended)
- **JDK 17**
- **Android SDK 34** (the project declares `compileSdk = 34`, `targetSdk = 34`,
  `minSdk = 26`)

No special storage, network, or phone permissions are requested. File access is
granted only for the files the user explicitly selects via the system picker.

---

## Building

### Option A — Android Studio (recommended)

1. Open this folder in Android Studio.
2. Let Gradle sync (it downloads AGP 8.5.2, Kotlin 1.9.24, and the dependencies
   listed below).
3. Run the `app` configuration on an emulator or device.

### Option B — Command line

```bash
./gradlew assembleDebug
# or install directly:
./gradlew installDebug
```

This repository ships `gradlew`/`gradlew.bat`. The wrapper scripts are
self-sufficient: if `gradle/wrapper/gradle-wrapper.jar` is present they behave
like a standard Gradle wrapper; otherwise they download the Gradle 8.7
distribution named in `gradle/wrapper/gradle-wrapper.properties` and run it
directly (requires `curl` or `wget`, plus `unzip`, on the build machine).

> Note: this snapshot was authored without the binary `gradle-wrapper.jar`
> (a binary artifact). Opening the project in Android Studio once will
> regenerate it automatically.

---

## The main flow

```
OPEN UPLOADGO
      ↓
SELECT FILES  (Android's native picker, multi-select)
      ↓
PREVIEW      (grid / list, image + video viewers)
      ↓
IF ZIP → VIEW CONTENTS  (secure extraction, select media)
      ↓
SELECT CONTENT
      ↓
SHARE        (native Android Sharesheet)
      ↓
TELEGRAM (or any other app)
      ↓
user selects channel & presses Send
```

UploadGo's job ends the moment the Sharesheet hands the `content://` URIs to the
receiving app. The user remains in full control of the destination and the send
action — this is intentional and is reflected in the UI copy
("Shared via Android Sharesheet").

---

## Sharing behavior

- **1 file** → `Intent.ACTION_SEND` + `EXTRA_STREAM`.
- **Multiple files** → `Intent.ACTION_SEND_MULTIPLE` +
  `putParcelableArrayListExtra(EXTRA_STREAM, …)` + `ClipData`.
- `Intent.FLAG_GRANT_READ_URI_PERMISSION` grants temporary read access to the
  receiving app.
- MIME types are chosen per batch: a single type when uniform, `image/*` or
  `video/*` for homogeneous batches, and `*/*` for mixed batches.
- Picked files keep their SAF `content://` URIs; extracted ZIP contents are
  served through `FileProvider` (`com.uploadgo.app.fileprovider`). Raw
  `file://` paths are **never** exposed.

When ZIP files are selected, three actions are offered:

- **Share ZIP Contents** — extract media and share it (plus any other selected
  files), never the ZIP itself.
- **Share Selected** — share the selected files exactly as-is (ZIPs included).
- **Share Original ZIP** — share only the ZIP archive(s).

---

## ZIP handling & security

- ZIPs are opened **read-only** via `ContentResolver.openInputStream` — the
  original archive is never modified or deleted.
- Extraction streams entries to `cacheDir/extracted/<zipId>/` inside the app
  sandbox.
- **Path traversal is blocked**: absolute paths, `..` components, backslashes
  and NUL bytes are rejected; every entry is resolved inside the extraction
  directory.
- Nothing from a ZIP is ever executed.
- macOS/junk entries (`__MACOSX`, `.DS_Store`, `._*`) are skipped.
- Entry count and total extracted size are bounded (20 000 entries / 4 GiB) to
  guard against zip bombs and storage exhaustion.
- Only JPG/JPEG/PNG/WEBP/GIF, MP4/MOV/MKV are extracted; other files are
  counted and reported as skipped (the UI shows "N unsupported files were
  skipped").
- Temporary files are cleaned only when safe: on app start for stale files, and
  a few minutes after sharing (configurable in Settings) so the receiving app
  can still read the URIs.

---

## Performance

- Thumbnails only — Coil downsampling for images, 512 px-capped frames for
  video; full-resolution media is never decoded into the UI.
- Video playback streams via ExoPlayer; videos are never re-encoded or loaded
  into memory.
- ZIP extraction, thumbnail generation, and history writes run on background
  coroutines (Dispatchers.IO) — the UI thread is never blocked.
- Lazy grids/lists handle 100+ selected files and ZIPs with hundreds of entries.

---

## Verifying the requested test scenarios

1. **3 images → Share**: pick 3 images, tap **Share**, confirm the Sharesheet
   opens, choose Telegram, and the three files are attached.
2. **2 videos → Share**: pick 2 videos, tap **Share** — the two videos are
   passed to the Sharesheet (no full decode into RAM).
3. **1 ZIP with 10 photos + 2 videos**: open the ZIP preview, confirm 12 media
   items appear, **Select All**, **Share Contents** — the Sharesheet receives
   all 12 extracted files.
4. **2 images + 1 ZIP (5 images + 2 videos)**: **Share ZIP Contents** prepares
   9 media files (2 + 5 + 2).
5. **Corrupted ZIP**: UploadGo shows a "Could not open ZIP" error with
   Try again / OK, and the app remains usable.
6. **Unsupported files inside a ZIP**: they are ignored and the UI shows
   "N unsupported files were skipped".
7. **Large video**: playback streams; the app never loads the full file into
   memory and does not crash.

---

## Project structure

```
app/src/main/java/com/uploadgo/app/
├── AppGraph.kt                 # process-scoped service locator
├── UploadGoApp.kt              # Application
├── MainActivity.kt             # splash + theme + edge-to-edge
├── data/
│   ├── SessionStore.kt         # selection + ZIP state (StateFlow)
│   ├── HistoryRepository.kt    # Room-backed history
│   ├── ShareService.kt         # share orchestration + cleanup
│   ├── db/                     # Room database + DAO
│   ├── media/                  # SAF metadata + video thumbnails
│   ├── model/                  # MediaItem, HistoryEntry
│   ├── prefs/                  # DataStore settings
│   ├── share/                  # Sharesheet intent builder
│   ├── temp/                   # temporary file management
│   └── zip/                    # secure ZIP extraction
├── ui/
│   ├── UploadGoApp.kt          # navigation + bottom bar
│   ├── components/             # thumbnails, share controller
│   ├── screens/                # Home, ZIP, viewers, Queue, History, Settings
│   └── theme/                  # Material 3 color scheme + typography
└── util/                       # MIME types, formatting, constants
```

---

## Dependencies

- **Kotlin 1.9.24**, **Android Gradle Plugin 8.5.2**, **Gradle 8.7**, **KSP**
- **Jetpack Compose** (BOM 2024.06.00) + Material 3 + icons-extended
- **Navigation Compose** 2.7.7
- **Activity Compose** 1.9.1 + core-splashscreen
- **Coil** 2.6.0 (image loading)
- **Media3 ExoPlayer** 1.4.0 (video)
- **Room** 2.6.1 (history)
- **DataStore** 1.1.1 (settings)

---

## License notes

UploadGo itself is provided as an example/reference project. Third-party
libraries retain their own licenses (see Settings → Open Source Licenses).
