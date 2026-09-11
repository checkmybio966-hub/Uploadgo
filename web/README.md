# UploadGo — Web Preview (Vercel)

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
- **Queue / History / Settings** — history persists in `localStorage`; theme
  (System/Light/Dark) and ZIP/storage settings.

## Run locally

```bash
cd web
python3 -m http.server 8080
# open http://localhost:8080
```

## Deploy to Vercel

**Option A — drag & drop (fastest)**
1. Go to https://vercel.com/new
2. Drag this `web/` folder onto the page. Done — you get a live URL.

**Option B — Vercel CLI**
```bash
npm i -g vercel
cd web
vercel            # preview deploy
vercel --prod     # production deploy
```

**Option C — GitHub import**
1. Push this repository to GitHub.
2. In Vercel: *Add New → Project → Import* the repo.
3. Set **Framework Preset = Other** and **Root Directory = `web`**.
4. Deploy.

No environment variables or build command are needed — it's a pure static site.
