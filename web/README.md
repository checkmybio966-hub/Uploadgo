# UploadGo — Web Preview (Netlify)

A faithful, **dependency-free** browser preview of the UploadGo Android app:
same flow, same screens — Select → Preview → (ZIP) → Share → Queue → History →
Settings. No build step, no framework, no npm — just `index.html`,
`styles.css`, and `app.js`.

> This preview lets you *check* the app's UX before building the real Android
> APK. The Android app is in the parent folder (`app/`, Kotlin + Jetpack
> Compose) and shares files through the **native Android Sharesheet**; here the
> browser's **Web Share API** (or a Sharesheet-style dialog) is used instead.

## What works in the preview

- **Select Files** — native file picker (multi-select of JPG/PNG/WEBP/GIF,
  MP4/MOV/MKV, ZIP).
- **Grid / List** toggle, checkboxes, Select All / Clear All, `Selected: N`.
- **Image viewer** — full-screen, zoom (+/−, wheel, drag-pan, double-tap),
  prev/next swipe, filename + size.
- **Video viewer** — HTML5 player (play/pause/seek/duration/fullscreen).
- **ZIP preview** — real ZIP parsing & extraction in the browser
  (`DecompressionStream`): media thumbnails, per-file selection, "N unsupported
  files skipped", corrupted-ZIP error state.
- **Share** — Web Share API with real file attachments where supported,
  otherwise a Sharesheet-style dialog. Records history only — it never claims a
  remote app delivered the files.
- **Centered hero** — the "Select Files" card sits vertically centred on the
  empty home screen; the Home tab, Queue and History also have clean spacing.
- **Video previews** — video tiles show a live frame with a play badge, and
  tapping opens the full video player.
- **Queue / History / Settings** — history persists in `localStorage`; theme
  (System/Light/Dark) and ZIP/storage settings.

## Run locally

```bash
cd web
python3 -m http.server 8080
# open http://localhost:8080
```

## Deploy to Netlify

**Option A — Netlify Drop (fastest, ~30 seconds)**

1. Download this branch as a ZIP:
   `https://github.com/checkmybio966-hub/Uploadgo/archive/refs/heads/arena/01a0912b-uploadgo.zip`
2. Extract it and find the **`web`** folder inside.
3. Open https://app.netlify.com/drop
4. **Drag the `web` folder** onto the page (the folder itself, not the files
   inside).
5. Netlify deploys instantly and gives you a live URL like
   `https://something-random.netlify.app`.

**Option B — GitHub import**

1. In Netlify: *Add new site → Import an existing project → GitHub*.
2. Pick the `Uploadgo` repo.
3. Set:
   - **Branch** = `arena/01a0912b-uploadgo`
   - **Base directory** = `web`
   - **Build command** = *(leave empty)*
   - **Publish directory** = `.`
4. Click **Deploy**.

The `netlify.toml` in this folder already declares the publish directory and
security headers, so Option A needs zero configuration.

No environment variables or build command are needed — it's a pure static site.
