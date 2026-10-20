# Baca

An e-reader for Android and desktop that keeps your place, highlights, notes and bookmarks in step across devices, without an account or a server. Sync goes through a folder you already share between your devices, such as one kept in sync by Syncthing, Dropbox or iCloud Drive.

Built with Kotlin Multiplatform and Compose Multiplatform. iOS is planned.

## Features

- **Formats:** EPUB, FB2 (including `.fb2.zip`), TXT and CBZ comics.
- **Reader:** paged layout with justified paragraphs and first-line indents; four reading themes (Paper, White, Dim, Black); font, size, line spacing and margins; contents and chapter jumps; arrow keys turn pages on desktop.
- **Notes:** highlights, notes and bookmarks, listed per book in the reader and across all books on the Notes and Bookmarks screens.
- **Sync:** each device writes its own JSON files into the shared folder; merges are conflict-free and order edits with Lamport clocks rather than wall time. Different files of the same EPUB edition are linked, so your place carries over even if each device has its own copy.
- **Backup:** one `.zip` with your library, positions, notes, settings and book files, restorable on any device.
- **App theme:** follow the system, or force light or dark.

Book files themselves don't sync. Import the book on each device; positions and notes then match up automatically.

## Build and run

Requires JDK 17 or newer. The Android build also needs the Android SDK (set `sdk.dir` in `local.properties`).

```bash
./gradlew :desktop:run                     # run the desktop app
./gradlew :android:assembleDebug           # build the Android debug APK
```

Tests:

```bash
./gradlew :shared:jvmTest                  # shared logic
./gradlew :composeApp:desktopTest          # Compose UI, headless
./gradlew :composeApp:compileAndroidMain   # Android code compiles
```

On desktop, the library database is `~/.baca/baca.db`, and imported books are stored in `~/.baca/books`.

## Project layout

| Module | Contents |
|---|---|
| `shared/` | Platform-independent logic: book parsers, pagination, reader session, sync merge and boundary, SQLDelight database, backup |
| `composeApp/` | Compose UI shared by Android and desktop, plus small platform pieces (file pickers, sync folder access) |
| `android/` | Android app entry point |
| `desktop/` | Desktop app entry point |

## Documentation

- [`docs/TECH.md`](docs/TECH.md): design reference covering formats, positions, sync rules, backup and the UI guide.
- [`AGENTS.md`](AGENTS.md): conventions and rules for contributors, human or AI.

## Fonts

Bundled reading fonts are Literata, DM Sans and [Libron](https://github.com/nicoverbruggen/libron), all under the SIL Open Font License 1.1. License texts are in [`docs/licenses`](docs/licenses).
