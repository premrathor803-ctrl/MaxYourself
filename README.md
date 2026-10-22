# Elevate Launcher

Android launcher app + a Wellness Hub (grooming, posture/growth, home-workout) —
built lightweight for Android 7.0 (API 24) and up, low-end devices.

Content notes:
- No facial/photo "attractiveness scoring" — intentionally left out, it's pseudo-scientific
  and can be harmful to self-image.
- Posture/height content is evidence-based: it improves posture and how tall you look,
  it does not claim to increase bone-level height after growth plates close.

## What's in this project
- `app/` — the Android app source (Kotlin, Views + ViewBinding, no heavy ML/Compose, minSdk 24)
- `.github/workflows/build-apk.yml` — builds a debug APK automatically on every push,
  and attaches it to a GitHub Release so it's a direct download link (no Android Studio needed)

## Getting an APK — easiest path (no computer needed)
1. On github.com, create a new **empty** repository (no README/license).
2. Use GitHub's web "Upload files" button (or the GitHub mobile app) to upload this whole
   folder's contents, keeping the folder structure (`app/`, `.github/`, the root gradle files).
3. Commit to the `main` branch. This automatically triggers the build workflow.
4. Go to the repo's **Actions** tab and wait for the "Build debug APK" run to finish
   (usually 5–15 minutes).
5. Once it's green, open the repo's **Releases** section (right sidebar on github.com) —
   you'll find `app-debug.apk` attached. Download it to your phone.
6. On your phone: Settings → allow "Install unknown apps" for your browser/file manager,
   then open the downloaded APK to install.

## Setting it as your home screen (optional)
After installing, go to **Settings → Apps → Default apps → Home app** and pick
**Elevate Launcher**. You can always switch back the same way. The app also works as a
normal app (via its icon) even if you never set it as default.

## Building locally instead (if you have a computer + Android Studio)
Open this folder in Android Studio — it will offer to generate the Gradle wrapper and
sync automatically. Then Run ▶ on a device/emulator, or Build → Build Bundle(s)/APK(s) → Build APK(s).

## Notes for whoever maintains this
- No local `gradlew` is checked in (the sandbox this was built in couldn't reach
  services.gradle.org to generate one). Android Studio will create it on first open,
  and the CI workflow installs Gradle directly — so this is not a problem for building.
- Package: `com.elevate.launcher`. App id/applicationId: `com.elevate.launcher`.
