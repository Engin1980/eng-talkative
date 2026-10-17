# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

The repository is still at the specification stage. There is no source code, build system, test setup, or git history, so there are no build/lint/test commands yet. Update this file with real commands once the Android project is scaffolded.

Documents (all written in Czech; keep them in Czech):
- `assignment.md` — the authoritative product spec, with all decisions already incorporated. Read it before designing or coding anything.
- `todo.md` — deferred/future features (table with a `Priorita` column the product owner fills in; do not fill it yourself).
- There is currently no open-questions file. When questions arise, the workflow used so far was: create `guestions.md` (sic, that spelling was the owner's choice) with each question having a **Návrh** (proposal) and an empty **Odpověď** (answer) field; the owner fills in answers; fold them into `assignment.md`, move deferred items to `todo.md`, then rewrite the file with only the remaining open questions, and delete it once nothing is left.

## Product summary

Android AAC-style (communication aid) app "Talkative": the user assembles a sentence from predefined words and the app reads it aloud via TTS. Same app and same device for both roles; the app always starts in **client mode**, and the admin UI is entered by long-pressing (default 10 s) a hidden control. There is no PIN — it only guards against accidental entry.

- **Data model**: folders (nested arbitrarily) and words. Both have a title; words also have an optional image and an optional alternate audio (different TTS text, or a custom MP3). Each word has its own ID, titles need not be unique, words belong to exactly one folder and are never copied. Items are manually ordered by the admin (quick alphabetical sort uses a configurable collation). Store the dictionary under a `default` profile so multiple profiles can be added later without a model rewrite.
- **Client UI**: sentence bar at the top, paged grid of folders/words below, breadcrumbs plus large Back/Home buttons. Add words by tap or drag (configurable), remove by long-press or dragging off the bar, reorder by dragging within the bar (can be disabled). The in-progress sentence is not persisted across restarts.
- **Playback**: sequential; adjacent TTS words are merged into one utterance, an MP3 word interrupts TTS and then playback continues. Play button turns into Stop while playing. TTS and MP3 playback sit behind a common interface (e.g. `SpeechProvider`) so more voice providers can be added.
- **Admin**: CRUD for words/folders, multi-select move, ordering, export/import, per-device configuration. Deleting a non-empty folder is recursive with a confirmation showing the item count; there is no trash.
- **Configuration is local to each device and is NOT synced** (grid size, tap/drag mode, hold interval, play-on-tap, collation, hide captions in sentence, auto-clear after playback). Defaults are listed in `assignment.md`.

## Architecture decisions

- Native Android, **Kotlin** (Java was considered and rejected because Compose is Kotlin-only), Jetpack Compose, Material 3, minSdk 26 (Android 8.0). If a library needs a newer minSdk, stop and ask. Write simple, readable Kotlin — the owner knows Java.
- MVVM/MVI with clear UI / domain / data layers, Hilt, Coroutines + Flow. Priority is a clear, easy-to-follow architecture.
- Room for local storage; the folder tree is an adjacency list (each folder/word stores its parent folder ID, root has none). All sizes, fonts, and colors come from the theme.
- applicationId `cz.talkative.app` (can be changed later, but gets harder once Google sign-in/publishing exists). Target device is a generic Android tablet; support portrait and landscape.
- **Offline-first**: local storage (Room + files) is the source of truth; the app must work fully offline. Google Drive (hidden `drive.appdata` folder) stores everything (JSON metadata plus image/MP3 files) so a new device pulls the same data for the same account. Account is chosen via the system dialog on first run; until then the app is local-only.
- **Sync**: automatic after each admin change (when online), on app start, and via a manual button; retry later on network failure. Last-write-wins. Deletions are kept as timestamped tombstones; delete-vs-edit conflicts are won by the newer change. The app must tolerate multiple installs writing to the same Drive and recover from inconsistency rather than guarantee consistency. A warning mark (exclamation icon) on a word signals: missing image/MP3 file, reference to a nonexistent folder, or an unresolvable sync conflict.
- **Export/import**: ZIP with `manifest.json` (schema version) plus media directories, via the Storage Access Framework. Export contains folders, words, and media; configuration is an optional checkbox. Import only adds (new IDs), never overwrites, with an optional replace-all; partial import goes into a user-chosen target folder (default root).
- Image handling: images are downscaled when saved (e.g. 512 px); MP3s are limited to tens of seconds. Image sources are device gallery and camera.
- Accessibility: touch targets of at least 64 dp, high contrast, no complex gestures, no time-limited actions.
- Distribution is internal (APK / internal testing) for now. Tests should be written so they actually verify the required behavior: unit tests for domain and repositories, Compose UI tests for key client scenarios.
- Data stays on the device and in the user's private Drive; no custom servers.
