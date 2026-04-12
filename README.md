<div align="center">

<br/>

```
 ██████╗ ███╗   ███╗███╗   ██╗██╗
██╔═══██╗████╗ ████║████╗  ██║██║
██║   ██║██╔████╔██║██╔██╗ ██║██║
██║   ██║██║╚██╔╝██║██║╚██╗██║██║
╚██████╔╝██║ ╚═╝ ██║██║ ╚████║██║
 ╚═════╝ ╚═╝     ╚═╝╚═╝  ╚═══╝╚═╝
```

**The last video downloader you'll ever need.**

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-GPL--3.0-EE4B2B?style=flat-square)](LICENSE)
[![yt-dlp](https://img.shields.io/badge/Engine-yt--dlp-FF0000?style=flat-square&logo=youtube&logoColor=white)](https://github.com/yt-dlp/yt-dlp)
[![Release](https://img.shields.io/github/v/release/ferrdishx/omni?style=flat-square&color=22c55e&label=latest)](https://github.com/ferrdishx/omni/releases)

<br/>

</div>

---

## Screenshots

<div align="center">

| Home | Library | Download Options | Downloads | Settings |
| :---: | :---: | :---: | :---: | :---: |
| ![Home](screenshots/home.png) | ![Library](screenshots/library.png) | ![Options](screenshots/options.png) | ![Downloads](screenshots/downloads.png) | ![Settings](screenshots/settings.png) |


</div>

---

## What is Omni?

Omni is a **free, open-source Android media hub** that combines a universal video downloader with a modern built-in player. No ads. No tracking. No account required.

Powered by **yt-dlp** and **FFmpeg**, it downloads video and audio from hundreds of platforms at the highest available quality — including **4K 60fps** video and **320kbps** audio — and plays everything back with a full-featured ExoPlayer engine.

---

## Features

### 📥 Downloading

| Feature | Details |
|---------|---------|
| **Universal support** | YouTube, Instagram, TikTok, Twitter/X, SoundCloud and [700+ more sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md) |
| **4K 60fps video** | Automatically merges separate video + audio streams via FFmpeg |
| **High-quality audio** | MP3, AAC, FLAC, OPUS, M4A, WAV, OGG — up to 320kbps |
| **Real format detection** | Shows only the qualities actually available for each video |
| **Embed thumbnail** | Cover art burned into MP3/AAC/FLAC files automatically |
| **Auto-engine update** | yt-dlp updates itself on launch — downloads never break |
| **Per-download options** | Override quality/format for each individual download |
| **Concurrent downloads** | Up to 5 simultaneous downloads |
| **Share-to-download** | Share a link from any app directly into Omni |

### 🎬 Playback

| Feature | Details |
|---------|---------|
| **ExoPlayer (Media3)** | Smooth, hardware-accelerated playback for all formats |
| **Picture-in-Picture** | Floating window with auto-PiP on Android 12+ |
| **Background audio** | Music keeps playing with screen off or app minimized |
| **MiniPlayer** | Control playback while browsing your Library or Settings |
| **Lock screen controls** | Media session with album art on the notification shade |

### 🎨 Interface

- **Material You** — UI colors adapt to your wallpaper automatically
- **Edge-to-edge** design for a truly immersive feel
- **Animated interactions** — scale, fade, and slide transitions throughout
- **Reduce Animations** option for battery saving or accessibility
- **Dark / Light** theme follows system preference

---

## Tech Stack

```
Language        Kotlin
UI              Jetpack Compose + Material Design 3
Architecture    MVVM (ViewModel + StateFlow)
Download Engine youtubedl-android (yt-dlp) + FFmpeg
Player          Media3 (ExoPlayer) + MediaSession
Database        Room (history, queue, library)
Preferences     DataStore
Navigation      Compose Navigation
Images          Coil (thumbnails)
Background      WorkManager
```

---

## Download

> ⚠️ Omni is **not on the Play Store** by design — it will never be.
> Download the APK directly from GitHub Releases.

<div align="center">

[![Download APK](https://img.shields.io/badge/⬇_Download_APK-Latest_Release-22c55e?style=for-the-badge)](https://github.com/ferrdishx/omni/releases/latest)

</div>

**Requirements:** Android 8.0 (API 26) or higher · ~100MB · ARM64 / ARMv7 / x86_64

---

## How to Use

```
1.  Copy a video/audio link from any app
2.  Open Omni and paste it — or use Share → Omni directly
3.  Choose your format: Video or Audio
4.  Select quality (only real available options are shown)
5.  Hit Start Download
6.  Watch progress in the Downloads tab
7.  Find your file in Downloads/Omni on your device
```

> **First launch:** Omni will download the yt-dlp engine (~15MB) automatically.
> This happens once and only once — subsequent launches are instant.

---

## Build from Source

```bash
# Clone
git clone https://github.com/ferrdishx/omni.git
cd omni

# Open in Android Studio (Hedgehog or newer)
# Sync Gradle → Run on device or emulator
```

**Requirements:**
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35

---

## Supported Platforms (examples)

<div align="center">

| Platform | Video | Audio |
|----------|-------|-------|
| YouTube | ✅ Up to 4K 60fps | ✅ Up to 320kbps |
| Instagram | ✅ Reels, Stories, Posts | ✅ |
| TikTok | ✅ | ✅ |
| Twitter / X | ✅ | ✅ |
| SoundCloud | — | ✅ |
| Vimeo | ✅ | ✅ |
| Reddit | ✅ | ✅ |
| + 700 more | [See full list →](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md) | |

</div>

---

## Permissions

| Permission | Why |
|-----------|-----|
| `INTERNET` | Download content and fetch video info |
| `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` | Access downloaded files (Android 13+) |
| `WRITE_EXTERNAL_STORAGE` | Save files to Downloads folder (Android ≤ 9) |
| `POST_NOTIFICATIONS` | Download progress notifications |
| `FOREGROUND_SERVICE` | Keep downloads running in background |

---

## Roadmap

- [x] Universal video/audio download
- [x] Format & quality selector with real availability
- [x] Embedded thumbnail in audio files
- [x] Built-in ExoPlayer
- [x] Picture-in-Picture
- [x] Background playback
- [x] MiniPlayer
- [x] Persistent download queue (Room)
- [ ] Playlist/channel batch download
- [ ] F-Droid release
- [ ] Sponsorblock integration
- [ ] Sleep timer
- [ ] Equalizer

---

## Contributing

Pull requests are welcome. For major changes, please open an issue first.

```bash
# Fork → clone → create branch → commit → PR
git checkout -b feat/your-feature
```

---

## Legal

Omni is a tool. You are responsible for what you download.
Only download content you have the right to download.

This project is not affiliated with YouTube, Google, or any platform it supports.

---

## License

```
Copyright (C) 2026 ferrdishx

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

[GPL-3.0 License](LICENSE) · Built with ❤️ by [ferrdishx](https://github.com/ferrdishx)

---

<div align="center">

**[⬇ Download](https://github.com/ferrdishx/omni/releases/latest) · [🐛 Report Bug](https://github.com/ferrdishx/omni/issues) · [💡 Request Feature](https://github.com/ferrdishx/omni/issues)**

</div>
