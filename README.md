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
- Grid size and spacing controls, performance profile, wallpaper toggle
- Gestures you can set yourself: swipe up/down, double tap, long press, pinch in/out, two-finger swipes. Actions include
  open drawer, search, notifications, Center, settings, edit mode, Gaming mode, flashlight, hidden apps and lock screen
- Five clock styles (classic, bold, stacked, compact, analog)
- Font: system, serif, monospace, condensed, or one `.ttf` / `.otf` file you import (kept privately, not in backups)
- Settings search, and "Change group" on any app in the drawer when the automatic group is wrong
- Widgets: an optional widgets page for normal Android widgets, plus a small "Elevate Today" widget
- Removed apps are handled without crashing (shown as missing, can be cleaned up)
- Reset and delete controls for launcher settings and for Center data

**Center**
- Habits with distinct states (done, partial, skipped, missed, not scheduled), runs and best runs
- 41 exercises, 10 plans, a guided workout player (timer, rest, pause, resume, end early)
- A stick-figure picture for every exercise (start and end position, animated, tap to pause). Drawn in code from joint
  angles, so no image files. It stays still when animations are off
- Reminders for habits and workouts (optional, off until you turn them on)
- Private photo journal (see below)
- Workout history and session details
- 25 grooming, skincare, posture and learning articles plus a glossary
- Optional height tracker and sleep tracker (off until you turn them on)
- Goals and a progress screen with charts that show plain empty states when there is no data

**Privacy (hidden apps)**
- Hide apps from Elevate's drawer, search and home screen
- Open the hidden list through a working **Calculator**: type your 4-8 digit code and press `=`
- Code is stored only as a salted PBKDF2 hash, with growing lock-outs after wrong tries
- Optional separate "Calculator" launcher icon
- Optional fingerprint or face (Android's own prompt) as a second way in, through a long-press on the Calculator display.
  It needs the code to be set first, and the code stays the main key
- Forgot your code: confirm with your phone's screen lock, then set a new one

**Photo journal** (optional)
- Take or pick progress photos, compare two of them side by side, add notes
- Pictures are copied into Elevate's private storage, shrunk and re-saved, which removes location and camera data
- Never in your gallery, never uploaded, never in backups, screenshots blocked on those screens
- No scoring, no ratings, no analysis of your face or body. The only hint is about the picture itself
  ("looks dark", "may be blurry") so photos are easier to compare. Camera permission is not requested: Elevate asks
  your camera app to take the picture

**Gaming mode**
- Pauses Elevate's reminders, groups your games, optional Do Not Disturb (priority only), optional break reminders,
  a Quick Settings tile
- It does **not** make games faster, free memory or change graphics

**Backup and restore**
- One JSON file with your home layout, settings, and (if you tick them) Center records and the hidden-apps list
- Checked before restoring; a bad file is rejected without changing anything
- Never includes the access code, photos, widgets or an imported font

**Icon packs**
- Reads packs made for ADW / Nova style launchers, applies them to apps the pack covers
- Per-app icon override (long-press an app on the home screen or in the drawer > Change icon)

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
- **Not tried on a real phone:** fingerprint unlock, the camera and gallery pickers, Do Not Disturb, the screen-lock
  gesture, and configuring third-party widgets. The emulator cannot do these, so they are written to Android's
  documented behaviour but not seen working.
- Reminders use approximate alarms so they are gentle on the battery. They can arrive a few minutes late, and some
  phone makers stop them unless the app is allowed to run in the background.
- The lock-screen gesture needs a one-time Accessibility setting and Android 9 or newer. Elevate's service only locks
  the screen; it does not read what is on it.
- Widgets are not part of backups. Add them again after restoring.
- Custom fonts change Elevate's own screens, not Android's Settings or system dialogs.

## Not in this version

RSS and weather (they would need the INTERNET permission, which goes against the offline-only design),
Focus Mode, and multiple profiles. Photo "analysis" of faces or bodies is left out on purpose.

## Building

The APK is built by GitHub Actions on every push to `main` (`.github/workflows/build-apk.yml`):
build, prerelease with the APK, then an emulator smoke test that posts screenshots and a report.
Locally: open the folder in Android Studio, or run `gradle assembleDebug` with Gradle 8.7 and JDK 17.
No Gradle wrapper is committed.

- Package: `com.elevate.launcher`
- Stack: Kotlin, plain Android Views built in code, SQLite and SharedPreferences, AGP 8.5.2, minSdk 24, targetSdk 34
- Debug signing key: `app/elevate.keystore`. It is committed on purpose so every build can update the last one.
  It is a debug key and is not a secret.
