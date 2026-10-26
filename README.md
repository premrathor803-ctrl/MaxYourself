# Elevate Launcher (V2)

An Android home-screen launcher with a built-in **Center**: offline habits, a home-workout
library with a guided player, grooming and skincare education, posture and mobility, optional
height and sleep trackers, goals and progress charts. Built to stay light on low-end phones
(Android 7.0 / API 24 and up). No account, no ads, no network permission.

## Install

1. Open the repo's **Releases** page and download `app-debug.apk` from the newest `build-N` prerelease.
2. Allow "Install unknown apps" for your browser or file manager, then open the APK.
3. Optional: Settings > Apps > Default apps > Home app > **Elevate Launcher**.

**Coming from V1?** This build is signed with a new, stable key, so Android will not update V1 in place.
Uninstall V1 once, then install V2. Later builds update normally.

## What is in it

**Launcher**
- Home pages with a persistent layout, dock, folders, edit mode, a Center panel page
- App drawer with search (apps, Center features, articles, exercises, plans)
- Themes: system / light / dark, pure-black option, 7 accent colours
- Grid size and spacing controls, performance profile, gestures, wallpaper toggle
- Removed apps are handled without crashing (shown as missing, can be cleaned up)
- Reset and delete controls for launcher settings and for Center data

**Center**
- Habits with distinct states (done, partial, skipped, missed, not scheduled), runs and best runs
- 41 exercises, 10 plans, a guided workout player (timer, rest, pause, resume, end early)
- Workout history and session details
- 25 grooming, skincare, posture and learning articles plus a glossary
- Optional height tracker and sleep tracker (off until you turn them on)
- Goals and a progress screen with charts that show plain empty states when there is no data

**Privacy (hidden apps)**
- Hide apps from Elevate's drawer, search and home screen
- Open the hidden list through a working **Calculator**: type your 4-8 digit code and press `=`
- Code is stored only as a salted PBKDF2 hash, with growing lock-outs after wrong tries
- Optional separate "Calculator" launcher icon
- Forgot your code: confirm with your phone's screen lock, then set a new one

**Icon packs**
- Reads packs made for ADW / Nova style launchers, applies them to apps the pack covers
- Per-app icon override (long-press an app > Change icon)

## Honest limits

- **Hiding is not security.** An app hidden in Elevate is still installed and still shows in Android
  Settings, in other launchers and in phone-wide search. It keeps casual eyes away, nothing more.
- If you clear Elevate's data or uninstall it, the code and the hidden list are gone.
- **Icon packs are only checked against the common appfilter format.** It has not been tried against every
  pack. Packs that need masks, icon backs or dynamic calendars use only their plain per-app icons.
- **No attractiveness scoring and no photo analysis**, on purpose. Looks scoring is unreliable and can hurt.
- **No height-increase claims.** Posture work can change how tall you stand and look. Nothing here claims to
  add bone length after growth plates close.
- The content is general education, not medical advice.
- Tested on an Android 10 emulator and by automated screenshots. It has not been measured on real low-end
  hardware, so no speed or battery numbers are claimed.

## Not in this version

Reminders and notifications, home-screen widgets, Gaming Mode, backup and restore, optional photo
progress analysis, Focus Mode, multiple profiles, and fingerprint / face unlock.

## Building

The APK is built by GitHub Actions on every push to `main` (`.github/workflows/build-apk.yml`):
build, prerelease with the APK, then an emulator smoke test that posts screenshots and a report.
Locally: open the folder in Android Studio, or run `gradle assembleDebug` with Gradle 8.7 and JDK 17.
No Gradle wrapper is committed.

- Package: `com.elevate.launcher`
- Stack: Kotlin, plain Android Views built in code, SQLite and SharedPreferences, AGP 8.5.2, minSdk 24, targetSdk 34
- Debug signing key: `app/elevate.keystore`. It is committed on purpose so every build can update the last one.
  It is a debug key and is not a secret.
