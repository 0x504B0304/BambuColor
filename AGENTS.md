# AGENTS.md

## Project overview
Single-module Android app (Kotlin, Jetpack Compose, Material 3). Reads NFC tag UIDs from Bambu Lab filament spools and binds them to consumable metadata.

## Build & test commands
```powershell
.\gradlew assembleDebug                  # build debug APK
.\gradlew test                           # unit tests (host JVM)
.\gradlew connectedAndroidTest           # instrumented tests (device/emulator)
.\gradlew test --tests "*ClassName*"     # single unit test
.\gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.TestClass
```

## Architecture
- **Single Activity**: `MainActivity` — everything lives in Compose composables.
- **3 screens**: HOME (tag read/bind), CONFIG (consumable + binding management), ABOUT — navigated via `NavigationSuiteScaffold` bottom nav.
- **Room database**: stored externally at `Android/data/<appId>/files/bambu_color.db` (not internal db path). Uses `fallbackToDestructiveMigration()` — schema bumps silently wipe data.
- **NFC**: uses `enableReaderMode` (foreground dispatch), **not** intent-based tags. Only active while the app is in the foreground. NFC hardware is optional (`android:required="false"`).
- **JSON import**: reads `filaments_color_codes.json` from same external files directory. A default copy is bundled in `assets/` and auto-copied on first launch.

## Room / kapt
- Room annotation processor uses **kapt** (NOT KSP). Plugin `id("org.jetbrains.kotlin.kapt")` must be applied.
- `kapt { correctErrorTypes = true }` is set in the app module.

## External data paths
All app data lives under `context.getExternalFilesDir(null)`:
- Database: `bambu_color.db`
- JSON import: `filaments_color_codes.json`

Both paths fall back to `context.filesDir` if external storage is unavailable.

## NFC tag decoding
- `BambuTagDecoder` decrypts MIFARE Classic tags using HKDF-SHA256 key derivation from tag UID.
- Key derivation uses Bouncy Castle (`bcprov-jdk15to18`). Fixed master key from Bambu Research Group.
- 16 sector keys (6 bytes each) derived from UID via `HKDF-Parameters(ikm=UID, salt=MASTER_KEY, info="RFID-A\0")`.
- Authenticates each sector with KeyA, reads data blocks (skipping key-trailer blocks).
- Parses block layout as documented in `https://github.com/Bambu-Research-Group/RFID-Tag-Guide`.
- Falls back to UID-only mode if `MifareClassic.get(tag)` returns null (device doesn't support MIFARE Classic).
- Decoded filament data is auto-matched to consumable DB (by `type + colorValueArgb`) and auto-bound to UID.

## Bouncy Castle
- `bcprov-jdk15to18:1.79` used for HKDF key derivation. Lightweight crypto API only (no JCE provider registration).

## Dead code
- `UiBus.kt` — `StateFlow`-based event bus. Defined but never referenced anywhere.

## Version catalog
Dependencies managed via `gradle/libs.versions.toml`. New deps should be added there, not inline in `build.gradle.kts`.
