# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Rules

Keep everything inside this folder and /tmp. Do not make git commit. Do not build, ask the user to test the app instead.

## Project

Chora (`com.craftworks.music`, Gradle root project `MusicPlayer`) is a single-module Android app (`:app`). It streams music from Subsonic/Navidrome servers or plays from local storage. It supports phone, Android TV and Android Auto. It uses Kotlin, Jetpack Compose (Material 3, plus `androidx.tv.material3` for TV), Media3, Hilt with KSP, Ktor/Ktorfit, kotlinx.serialization and DataStore. It targets JDK 21, minSdk 24 and compileSdk/targetSdk 37.

For reference only (per the rule above, do not run these): CI (`.github/workflows/build.yml`) runs `./gradlew assembleDebug`. There are currently no unit or instrumentation tests under `app/src`. Translations are managed on Crowdin (`res/values-*`). Store metadata and changelogs live in `fastlane/metadata/android/`. The version is set by `versionCode`/`versionName` in `app/build.gradle.kts`.

## Architecture

All source code is under `app/src/main/java/com/craftworks/music/`.

**Media providers (the core abstraction).** `data/providers/media/MediaProvider.kt` is an abstract `@Serializable` class. Every backend operation goes through it: library listing via `MediaQuery.*Query` objects, playlists, favorites, ratings, scrobbling, lyrics, stream URLs, search and similar calls. Its implementations are `navidrome/`, `subsonic/` and `local/`. Each one has a `*Service.kt` (Ktorfit API) and `*Types.kt` (wire DTOs), and maps results into the shared `data/model/MediaModel` types (`Song`, `Album`, `Artist`, `Playlist`, `InternetRadioStation`, …). A provider declares what it supports through `featureFlags: EnumSet<ProviderFeature>` and its `supported*Sort` lists. The UI checks these flags instead of checking the provider type. To add a new provider, register it in the polymorphic `serializerModule` in the `MediaProvider` companion.

**Provider management.** `managers/MediaProviderManager` is a global `object`, not something injected by Hilt. It holds every configured provider and exposes `currentProvider` / `allProviders` as `StateFlow`s. It saves them to an encrypted DataStore (`providers.pb`, via `utils/EncryptedMediaProviderSerializer` + `CryptoData`). `ChoraApplication.onCreate` runs `MigrationManager.init` first and then `MediaProviderManager.init`.

**Repositories.** `data/repository/*Repository` are Hilt `@Singleton`s. They mostly delegate to `MediaProviderManager.currentProvider.value` and convert models to Media3 `MediaItem`s with `toMediaItem()`. Media items carry their provider and IDs in `MediaMetadata` extras, and `MediaMetadata.getProvider()` recovers the provider from them. Lyrics have their own source chain in `data/providers/lyrics/` (lrclib, NetEase, unison, binimum), wired in `data/di/DataSourceModule.kt`.

**Playback.** `player/MusicService.kt` (`ChoraMediaLibraryService`) is a Media3 `MediaLibraryService`. It owns the ExoPlayer and session, and its `LibrarySessionCallback.onGetChildren` builds the browse tree for Android Auto. On the UI side, `player/MediaController.kt` provides `rememberManagedMediaController()` / `rememberCurrentMetadata()` for Compose. `SongHelper` provides play, enqueue and play-next helpers. `managers/TranscodeManager` and `PlaybackSettingsManager` control the stream bitrate and format.

**UI.** `MainActivity.kt` sets up phone and TV in one activity. It branches on `isTv` (from `uiMode`) to pick a TV `NavigationDrawer` or the phone layout. `NavGraph.kt` defines the routes, which are listed in `data/model/Screen.kt` and `data/NavigationItems.kt`. Screens are under `ui/screens/`, with TV-specific versions in `ui/screens/tv/`, `ui/elements/tv/`, `ui/elements/dialogs/tv/` and `ui/playing/tv/`. A UI change usually needs a matching TV change. ViewModels are `@HiltViewModel` classes in `ui/viewmodels/` and `ui/playing/NowPlayingViewModel.kt`.

**Settings.** `managers/settings/*SettingsManager` are Hilt singletons. Each exposes Preferences DataStore values as `Flow`s with `suspend` setters, and writes them inside `withContext(NonCancellable)`.

**Migrations.** `MigrationManager` runs the ordered list of `migrations/Migration` classes. It tracks the version in the `MigrationStatus` SharedPreferences. When a stored-data format changes, append a new migration to that list. Never reorder the list.
