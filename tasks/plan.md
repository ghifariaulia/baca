# Plan: Automatic Update Check & Popup

Spec: [SPEC-auto-update-check.md](../SPEC-auto-update-check.md)

## Components & Dependencies

1. **Pure Auto-Update Policy (`shared/domain/AutoUpdatePolicy.kt`)**
   - Constants: `KEY_AUTO_UPDATE_CHECK = "auto_update_check"`, `KEY_UPDATE_LAST_CHECKED_AT = "update_last_checked_at"`, `KEY_UPDATE_SKIPPED_VERSION = "update_skipped_version"`, `AUTO_UPDATE_COOLDOWN_MS = 24L * 60 * 60 * 1000`.
   - Pure functions:
     - `isAutoUpdateEnabled(storeValue: String?): Boolean` (defaults to `true` when null/unset, `false` when `"0"`).
     - `shouldCheckForUpdate(enabled: Boolean, lastCheckedAt: Long?, nowMs: Long, cooldownMs: Long = AUTO_UPDATE_COOLDOWN_MS): Boolean`.
     - `shouldShowUpdatePopup(status: UpdateStatus, skippedVersion: String?): UpdateStatus.Available?`.
   - `PositionStore` helpers: `loadAutoUpdateEnabled()`, `saveAutoUpdateEnabled(enabled)`, `loadLastUpdateCheckAt()`, `saveLastUpdateCheckAt(nowMs)`, `loadSkippedUpdateVersion()`, `saveSkippedUpdateVersion(version)`.

2. **Update Available Popup (`composeApp/ui/UpdateDialog.kt` or inside `AboutScreen.kt`)**
   - Reusable `UpdateAvailableDialog(available: UpdateStatus.Available, currentVersion: String, onDownload: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit)` using `AlertDialog`.

3. **Startup Check & Settings Toggle (`App.kt` & `AboutScreen.kt` / `SettingsScreen.kt`)**
   - `App.kt`: `LaunchedEffect(Unit)` checks `shouldCheckForUpdate(...)` on startup; if true, records `saveLastUpdateCheckAt(now)` and runs `checkForUpdate(APP_VERSION) { fetchLatestReleaseJson() }` on `Dispatchers.IO`. If `shouldShowUpdatePopup(result, store.loadSkippedUpdateVersion())` returns non-null, sets `pendingUpdate`.
   - `App.kt`: Renders `UpdateAvailableDialog` when `pendingUpdate != null`.
   - `AboutScreen.kt` / `SettingsScreen.kt`: Adds `"Automatically check for updates"` toggle (`ChoiceChip` / `Switch` row) wired to `store.loadAutoUpdateEnabled()` / `store.saveAutoUpdateEnabled(...)`.

4. **Documentation (`docs/TECH.md`)**
   - Update Section 10.4 (**Update check**) to document startup checks, 24h cooldown, popup actions, and Settings toggle.

## Implementation Order (TDD)
1. Write `AutoUpdatePolicyTest.kt` (RED) -> implement `AutoUpdatePolicy.kt` (GREEN).
2. Write UI tests in `UpdateDialogTest.kt` / `SettingsScreenTest.kt` (RED) -> implement `UpdateAvailableDialog`, `AboutScreen.kt` toggle, and `App.kt` startup check (GREEN).
3. Update `docs/TECH.md` and mark `SPEC-auto-update-check.md` implemented.
4. Verify `:shared:jvmTest`, `:composeApp:desktopTest`, `:composeApp:compileAndroidMain`.
