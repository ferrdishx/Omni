![Omni](omni.png)

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-GPL--3.0-EE4B2B?style=flat-square)](https://github.com/ferrdishx/Omni/blob/main/LICENSE)
[![yt-dlp](https://img.shields.io/badge/Engine-yt--dlp-FF0000?style=flat-square&logo=youtube&logoColor=white)](https://github.com/yt-dlp/yt-dlp)
[![Release](https://img.shields.io/github/v/release/ferrdishx/omni?style=flat-square&color=22c55e&label=latest)](https://github.com/ferrdishx/omni/releases)

---

## What is Omni?

I built Omni because every downloader app I tried either broke after a YouTube update, had obnoxious ads, or made you jump through hoops to get a decent quality file. I wanted something that just works: paste a link, pick a format, get the file.

It uses **yt-dlp** and **FFmpeg** under the hood, which means it supports [700+ sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md) and actually merges video and audio streams instead of giving you a muxed fallback. The engine updates itself automatically so YouTube changes don't randomly break everything.

No ads, no account, no nonsense.

---

## Screenshots

| Home | Library | Favorites | Video Options | Audio Options | Downloads | Settings |
|------|---------|-----------|---------------|---------------|-----------|----------|
| [Home](screenshots/home.png) | [Library](screenshots/library.png) | [Favorites](screenshots/favorites.png) | [Video Options](screenshots/options_video.png) | [Audio Options](screenshots/options_audio.png) | [Downloads](screenshots/downloads.png) | [Settings](screenshots/settings.png) |

---

## Features

**Downloading:** Supports YouTube, Instagram, TikTok, Twitter/X, SoundCloud, Vimeo, Reddit and hundreds more. Video and audio streams are merged via FFmpeg so you get the real highest quality. The format picker only shows options that actually exist for that video (no fake qualities). Audio downloads can embed the thumbnail directly into the file so your MP3s have cover art everywhere.

**Playback:** The built-in player uses ExoPlayer (Media3) with background audio, lock screen controls, Picture-in-Picture, and a MiniPlayer that stays visible while you browse the app. On Android 12+, PiP activates automatically when you leave the app mid-video.

**Interface:** Material You adapts the colors to your wallpaper. Fully edge-to-edge with dark/light themes and an option to reduce animations.

---

## Tech Stack

```
Language        Kotlin
UI              Jetpack Compose + Material Design 3
Architecture    MVVM (ViewModel + StateFlow)
Download Engine youtubedl-android (yt-dlp) + FFmpeg
Player          Media3 (ExoPlayer) + MediaSession
Database        Room
Preferences     DataStore
Images          Coil
```

---

## Download

[![Download APK](https://img.shields.io/badge/⬇_Download_APK-Latest_Release-22c55e?style=for-the-badge)](https://github.com/ferrdishx/omni/releases/latest)

> The APK is 30MB+ because it ships with a full FFmpeg build. It's only on GitHub releases, no Play Store.

### Automatic Updates via Obtainium (recommended)

Paste the repo URL into [Obtainium](https://obtainium.imranr.dev/) and it handles updates automatically:

```
https://github.com/ferrdishx/Omni
```

**Requirements:** Android 8.0+ · ARM64 / ARMv7 / x86_64

---

## How to Use

Copy a link from any app and paste it into Omni (or use the share menu). Pick your format, only real options show up. Hit **Start Download** and track progress in the Downloads tab. Files go to `Downloads/Omni`.

On first launch, Omni downloads the yt-dlp engine (~15MB). This happens once.

---

## Build from Source

```bash
git clone https://github.com/ferrdishx/omni.git
cd omni
```

Open in Android Studio Hedgehog or newer, sync Gradle, run. JDK 17 and Android SDK 35 required.

---

## Roadmap

- [x] Universal video/audio download
- [x] Real format & quality detection
- [x] Embedded thumbnail in audio files
- [x] ExoPlayer with background playback
- [x] Picture-in-Picture
- [x] MiniPlayer
- [x] Favorites & Library
- [x] Playlist download
- [x] Sleep timer
- [ ] SponsorBlock integration
- [ ] F-Droid release

---

## Credits

Omni is built on top of other open source work. A few things worth calling out specifically:

**[RetroMusicPlayer](https://github.com/RetroMusicPlayer/RetroMusicPlayer)** (GPL-3.0, h4h13 and contributors): I borrowed the Settings screen structure, the accordion sections with colored circular icons and the dynamic palette extraction from artwork as a blurred background tint in the player.

**[YtDlnis](https://github.com/deniscerri/ytdlnis)** (GPL-3.0, deniscerri): The download history data layer, `HistoryItem` entity structure, and the DAO query patterns for sorting and filtering by status came from here.

**Libraries:**

| Library | Purpose | License |
|---------|---------|---------|
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | yt-dlp wrapper | GPL-3.0 |
| [ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit) | FFmpeg for Android | LGPL-3.0 |
| [Media3 (ExoPlayer)](https://github.com/androidx/media) | Playback | Apache-2.0 |
| [Coil](https://github.com/coil-kt/coil) | Image loading | Apache-2.0 |
| [Room](https://developer.android.com/training/data-storage/room) | Local database | Apache-2.0 |
| [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | Preferences | Apache-2.0 |
| [Palette](https://developer.android.com/develop/ui/views/graphics/palette-colors) | Dynamic color extraction | Apache-2.0 |

---

## Legal

Omni is a tool. You're responsible for what you download with it. Only download content you have the right to.

No affiliation with YouTube, Google, or any platform it supports.

---

## Contributing

PRs welcome. Open an issue first for anything significant.

```bash
git checkout -b feat/your-feature
```

---

## License

GPL-3.0 · Built by [ferrdishx](https://github.com/ferrdishx)
