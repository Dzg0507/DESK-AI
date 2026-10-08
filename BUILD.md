# Building and shipping DeskAI

> [!CAUTION]
> ### STRICT RULES FOR AI CODING ASSISTANTS (NEVER VIOLATE)
> 1. **DO NOT DOWNLOAD OR INSTALL GRADLE OR ANDROID SDK ON THIS MACHINE:**  
>    This host (the HP EliteDesk Mini) is the 24/7 AlwaysOnAgent server daemon. It does **NOT** build or compile Android APKs. APK builds and signing happen exclusively on the owner's main workstation where their private `debug.keystore` and `google-services.json` reside. Never attempt `scoop install gradle`, `choco install`, or any tool downloads here.
> 2. **NEVER BUMP `web_dist/version.json` AHEAD OF THE COMPILED `DeskAI.apk` BINARY:**  
>    `web_dist/version.json` is the live update manifest read by all installed phone apps. Bumping `versionCode` in `version.json` before a matching compiled `.apk` binary is committed to the repository causes an **infinite update loop**: the phone detects a newer version, downloads `DeskAI.apk` (which still contains the older build), installs it, and then immediately prompts the user to update again upon reopening.
> 3. **`web_dist/version.json` MUST MATCH THE COMMITTED `DeskAI.apk` BINARY EXACTLY:**  
>    Only update `web_dist/version.json` in the exact same commit where the newly compiled `DeskAI.apk` binary is placed into the repository.

Releases are **debug builds signed with one fixed debug keystore**, so each update installs over the last one
on the owner's phone. A build signed with any other key can't update the installed app, and the phone would
have to uninstall it first, losing its data.

## What you need (not in git)

| File | Where it comes from | Goes to |
|---|---|---|
| `debug.keystore` | the owner's PC: `C:\Projects\DeskAI-source\debug.keystore`. Its SHA-256 certificate fingerprint starts `3315b429e7e2f1d5`. **Never rotate it** (the phone would have to uninstall the app). | outside the repo: `%USERPROFILE%\.deskai\debug.keystore`, named in `local.properties` |
| `google-services.json` | the Firebase console (project `alwaysonagent-deskai`); on the owner's PC in `Downloads` | `app/google-services.json` (don't commit it) |
| `local.properties` | see below | repo root (gitignored) |

`local.properties` (never committed) holds the SDK path and the signing settings; `app/build.gradle.kts` reads
them, then the environment (`DESKAI_DEBUG_KEYSTORE`, `DESKAI_DEBUG_STORE_PASSWORD`, `DESKAI_DEBUG_KEY_ALIAS`,
`DESKAI_DEBUG_KEY_PASSWORD`), then falls back to `debug.keystore` in the repo root (gitignored) with the SDK's
default debug passwords:

```properties
sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
deskai.debug.keystore=C\:/Users/<you>/.deskai/debug.keystore
deskai.debug.storePassword=android
deskai.debug.keyAlias=androiddebugkey
deskai.debug.keyPassword=android
```

**Keys never go in git**, not even inside a zip (`.gitignore` blocks `*.keystore`, `*.jks`, `DeskAI-source.zip`).
Until 2026-10-07 `DeskAI-source.zip` and `web_dist/DeskAI-source.zip` (committed 2026-09-27) carried
`debug.keystore`; they were removed from tracking but stay in git history. The key wasn't rotated on purpose:
it only signs this sideloaded debug app, and a new key would force an uninstall.

Tools: JDK 17, the Android SDK (command-line tools, platforms `android-36` and `android-36.1`, build-tools
36.x), and Gradle 9.3.1. There's no Gradle wrapper in the repo, so use an installed Gradle; the AGP version is in
`gradle/libs.versions.toml`.

## Build

```powershell
$env:ANDROID_HOME = "C:\Users\<you>\AppData\Local\Android\Sdk"
gradle assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
```

A `NullPointerException ... ksp ... AWT-EventQueue` stack trace during the build is harmless noise from KSP.

## Check before shipping

```powershell
$bt = "$env:ANDROID_HOME\build-tools\36.1.0"
& "$bt\aapt2.exe" dump badging app\build\outputs\apk\debug\app-debug.apk | Select-String "versionCode"
& "$bt\apksigner.bat" verify --print-certs app\build\outputs\apk\debug\app-debug.apk   # SHA-256 3315b429…
```

Also check that the APK contains the Firebase app ID `1:229077577413:android:a1e4451858843d610f8aac` (in
`resources.arsc`). Otherwise push notifications stop working.

## Ship

1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`. **Always**: the updater only offers a
   higher `versionCode`.
2. Copy `app-debug.apk` to `DeskAI.apk`, `DeskAI-update.apk` and `web_dist/DeskAI.apk`.
3. Update `web_dist/version.json`: `versionCode`, `versionName`, `changelog` and `fileSizeBytes`.
4. Add a note at the top of `ALIGNMENT_DIALOG.md` saying what changed and who changed it.
5. Commit and push to `main`. Within a minute, the updater's check sees the new `version.json`.
