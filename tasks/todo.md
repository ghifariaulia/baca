# Tasks: Automatic Update Check & Popup

- [x] Task 1: Add pure auto-update policy & `PositionStore` helpers
  - Acceptance: `isAutoUpdateEnabled`, `shouldCheckForUpdate`, `shouldShowUpdatePopup`, and `PositionStore` persistence helpers work for all boundary cases (null/default, within cooldown, past cooldown, skipped version).
  - Verify: `./gradlew :shared:jvmTest --tests "*AutoUpdatePolicyTest*"`
  - Files: `shared/src/jvmTest/kotlin/com/baca/shared/domain/AutoUpdatePolicyTest.kt`, `shared/src/commonMain/kotlin/com/baca/shared/domain/AutoUpdatePolicy.kt`

- [x] Task 2: Add `UpdateAvailableDialog` and Settings toggle with UI tests
  - Acceptance: Popup displays version info and invokes Download / Later / Skip callbacks; About/Settings shows toggle for automatic update checks and persists choice to `PositionStore`.
  - Verify: `./gradlew :composeApp:desktopTest --tests "*UpdateDialogTest*" --tests "*SettingsScreenTest*"`
  - Files: `composeApp/src/desktopTest/kotlin/com/baca/app/ui/UpdateDialogTest.kt`, `composeApp/src/desktopTest/kotlin/com/baca/app/ui/SettingsScreenTest.kt`, `composeApp/src/commonMain/kotlin/com/baca/app/ui/AboutScreen.kt`, `composeApp/src/commonMain/kotlin/com/baca/app/ui/SettingsScreen.kt`

- [x] Task 3: Wire startup check and popup into `App.kt` and update `docs/TECH.md`
  - Acceptance: `App.kt` runs startup check on `Dispatchers.IO` respecting cooldown and skipped version, shows `UpdateAvailableDialog`, and `docs/TECH.md` + `SPEC-auto-update-check.md` are updated.
  - Verify: `./gradlew :shared:jvmTest :composeApp:desktopTest :composeApp:compileAndroidMain`
  - Files: `composeApp/src/commonMain/kotlin/com/baca/app/App.kt`, `docs/TECH.md`, `SPEC-auto-update-check.md`
