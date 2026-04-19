# Changelog

All notable changes to Omni are documented in this file.

## [1.1.0-beta]

### Added

- First-run onboarding flow for storage, media, and notification permissions.
- Favorites system backed by Room, with a dedicated tab, search, sorting, filters, and quick add/remove actions from the Library and player.
- Expanded download sheet with dedicated `Format`, `Options`, and `Advanced` tabs.
- New per-download controls for subtitles, auto-generated captions, chapters, metadata embedding, SponsorBlock, start/end trimming, audio normalization, and silence trimming.
- Advanced network and authentication options including browser cookies, rate limiting, proxy input, concurrent fragment tuning, filename templates, and custom `yt-dlp` arguments.
- Liquid bottom navigation and a draggable bottom-sheet player/miniplayer flow.

### Improved

- Player experience was reworked with better Picture-in-Picture handling, tighter mini-player integration, richer artist/artwork metadata, and smoother transitions into playback from Downloads, Library, and Favorites.
- Library browsing now surfaces author information, resolves local/audio thumbnails more reliably, and includes direct favorite actions in the media menu.
- Download reliability was hardened for stricter sites with updated `yt-dlp` request options, forced IPv4, extractor bypass tuning, and browser-cookie support.
- Build and platform compatibility were refreshed for Android SDK 36, KSP-based Room generation, and Compose strong skipping mode.

### Fixed

- Better recovery for local files that exist on disk but were missing or stale in the database.
- Better handling of sidecar and embedded cover art for downloaded audio.
- More consistent app naming for the beta line, including the launcher label and `1.1.0-beta` version metadata.

## [1.0-beta] - 2026-04-12

### Added

- First public beta release of Omni.
- Video and audio downloads.
- Playlist support.
- Smart quality selection.
- Internal media player for audio and video.
- Custom UI styles and fonts.
- Performance modes.
