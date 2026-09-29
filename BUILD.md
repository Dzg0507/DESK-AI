# Building and shipping DeskAI

Releases are **debug builds signed with one fixed debug keystore**, so each update installs over the last one
on the owner's phone. A build signed with any other key can't update the installed app, and the phone would
have to uninstall it first, losing its data.

## What you need (not in git)

| File | Where it comes from | Goes to |
|---|---|---|
| `debug.keystore` | the owner's PC: `C:\Projects\DeskAI-source\debug.keystore`. Its SHA-256 certificate fingerprint starts `3315b429e7e2f1d5`. | repo root (gitignored) |
| `google-services.json` | the Firebase console (project `alwaysonagent-deskai`); on the owner's PC in `Downloads` | `app/google-services.json` (don't commit it) |
| `local.properties` | `sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk` | repo root (gitignored) |

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
