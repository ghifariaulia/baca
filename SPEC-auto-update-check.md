# Spec Delta: Automatic Update Check & Popup

Status: implemented

## Objective

Automatically check GitHub's latest release on app launch (subject to a cooldown and user preference) and show a non-intrusive popup dialog when a newer version of Baca is available, so users learn about new releases without having to manually open **Settings → About → Check for updates**.

## Current Behavior

Per [docs/TECH.md](docs/TECH.md) Section 10.4 (**Update check**):
- The only network request Baca makes is GitHub's latest-release API (`fetchLatestReleaseJson`), and **only** when the user taps **Check for updates** in **Settings → About** (`AboutScreen.kt`).
- `checkForUpdate` in `shared/src/commonMain/kotlin/com/baca/shared/domain/UpdateCheck.kt` compares the remote `tag_name` with `APP_VERSION` (`0.2.1`) and returns `UpdateStatus.UpToDate`, `UpdateStatus.Available(version, pageUrl)`, or `UpdateStatus.Failed(reason)`.
- Tapping **Download \<version\>** opens the release page in the system browser (`openExternalLink`); nothing is downloaded or installed in-app.

## New Behavior

1. **Automatic check on startup:**
   - When the app launches (in `App.kt`, off the main thread on `Dispatchers.IO`), if automatic update checks are enabled and the cooldown since the last check has elapsed (default: 24 hours), Baca runs `checkForUpdate`.
   - Network failures or unreadable responses fail **silently** during automatic checks (no error popup or snackbar). Manual checks in **Settings → About** continue to show errors as before.
2. **Update available popup:**
   - When an automatic check returns `UpdateStatus.Available(version, pageUrl)` and `version` has not been dismissed/skipped by the user, Baca displays an `AlertDialog` over the app:
     - Title: e.g., `"Update available"`
     - Body: `"Baca <version> is available (you have <APP_VERSION>)."`
     - Actions:
       - **Download** (primary): opens `pageUrl` via `openExternalLink` and closes the dialog.
       - **Later** (dismiss): closes the dialog for this session (will prompt again on the next eligible check).
       - **Skip this version**: records `version` in `settings_local` (`update_skipped_version`) so this version is not prompted automatically again (the user can still see and download it manually in **Settings → About**).
3. **Settings toggle & persistence (`settings_local`):**
   - Store all state in `settings_local` (no SQLite schema changes, not synced):
     - `auto_update_check`: `"1"` (default enabled) or `"0"` (disabled).
     - `update_last_checked_at`: epoch millis of the last automatic check attempt.
     - `update_skipped_version`: version string skipped by the user (e.g. `"0.2.2"`).
   - Expose a toggle in **Settings → App** (or **Settings → About**) allowing the user to enable/disable **Automatically check for updates**.

## Commands

- Shared unit tests: `./gradlew :shared:jvmTest`
- Desktop Compose UI tests: `./gradlew :composeApp:desktopTest`
- Android compilation check: `./gradlew :composeApp:compileAndroidMain`

## Project Structure

- `shared/src/commonMain/kotlin/com/baca/shared/domain/` — Pure decision logic (cooldown check, skip-version check, `UpdateCheck.kt` / `AutoUpdatePolicy.kt`) with no IO, Compose, or wall-clock reads (`now` injected as parameter).
- `composeApp/src/commonMain/kotlin/com/baca/app/` — `App.kt` startup trigger and `AlertDialog` popup; `SettingsScreen.kt` / `AboutScreen.kt` toggle.
- `shared/src/jvmTest/kotlin/com/baca/shared/domain/` — Unit tests for auto-update policy.
- `composeApp/src/desktopTest/kotlin/com/baca/app/ui/` — Compose UI tests for the update popup and settings toggle.
- `docs/TECH.md` — Update Section 10.4 (**Update check** & **Settings**) after implementation.

## Code Style

Follow existing KMP + Compose Multiplatform conventions in `shared/domain` and `composeApp/ui`:

```kotlin
fun shouldCheckForUpdate(
    enabled: Boolean,
    lastCheckedAt: Long?,
    nowMs: Long,
    cooldownMs: Long = 24L * 60 * 60 * 1000,
): Boolean {
    if (!enabled) return false
    if (lastCheckedAt == null) return true
    return nowMs - lastCheckedAt >= cooldownMs
}
```

## Testing Strategy

- **Unit tests (`:shared:jvmTest`):** Test `shouldCheckForUpdate` (enabled/disabled, null timestamp, within cooldown, past cooldown) and `shouldShowUpdatePopup` (new version vs skipped version vs newer-than-skipped version).
- **UI tests (`:composeApp:desktopTest`):** Test that the popup renders when an available update is passed, **Download** invokes the link callback, **Later** dismisses without skipping, and **Skip this version** records the skipped version; test toggling the auto-check setting.

## Boundaries

- **Always:**
  - Keep pure domain logic in `shared/domain` (inject `nowMs`, no direct `System.currentTimeMillis()`).
  - Run `:shared:jvmTest`, `:composeApp:desktopTest`, and `:composeApp:compileAndroidMain` before finishing.
  - Update `docs/TECH.md` Section 10.4 to reflect automatic update checks.
- **Ask first:**
  - Changing the default behavior from opt-out (enabled by default) to opt-in (disabled by default) if strict zero-network-on-launch privacy is preferred.
  - Adding any in-app APK/DMG downloader or installer (explicitly out of scope).
- **Never:**
  - Add new SQLite tables or alter existing table schemas (`settings_local` only).
  - Show error dialogs or interrupt reading when a background/startup check fails.
  - Auto-install or auto-download binaries inside the app.

## Success Criteria

- [ ] On launch, if `auto_update_check` is enabled and >= 24h have passed since `update_last_checked_at`, `checkForUpdate` runs on `Dispatchers.IO`.
- [ ] If a newer version is returned and `version != update_skipped_version`, the update popup is shown with **Download**, **Later**, and **Skip this version**.
- [ ] Tapping **Download** opens the GitHub release URL in the browser and dismisses the popup.
- [ ] Tapping **Skip this version** persists `update_skipped_version` so subsequent launches do not re-prompt for that same version, while still prompting if an even newer version ships later.
- [ ] Failed automatic checks produce no UI error and do not crash or block app startup.
- [ ] Users can toggle automatic update checks on/off in Settings, and disabling it prevents startup network requests.
- [ ] Unit tests in `:shared:jvmTest` and UI tests in `:composeApp:desktopTest` cover the policy, popup, and settings toggle.

## Out of Scope (Deferred)

- **In-app download & self-update:** Downloading or installing APK/DMG binaries directly inside Baca (deferred; requires platform install permissions and signature verification).
- **Release notes / changelog in popup:** Fetching and rendering full Markdown release notes inside the dialog (deferred; user can view them on the GitHub release page).
- **Pre-release / beta channel toggle:** Opting into pre-release tags (deferred; stable tags only).

## Open Questions

1. **Opt-out vs. Opt-in:** `docs/TECH.md` currently notes that the manual update check is *"the only network request Baca makes, and only when the button is pressed."* Should automatic update checking be **enabled by default** (with a Settings toggle to disable) or **disabled by default** (opt-in)?
2. **Popup timing while reading:** If a book is opened immediately on launch (e.g., via "Open with Baca" / `initialBookPath`), should the popup wait until the user returns to the Library, or appear immediately?
