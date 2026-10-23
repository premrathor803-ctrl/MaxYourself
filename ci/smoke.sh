#!/usr/bin/env bash
# Emulator smoke test: install, open every screen listed in ci/activities.txt, take screenshots,
# run the monkey for random UI events, and collect crash logs into out/report.md.
set -u
mkdir -p out
APK=apk/app-debug.apk
PKG=com.elevate.launcher

adb wait-for-device
adb shell input keyevent 82 || true
adb install -r "$APK" 2>&1 | tee out/install.txt
adb root > /dev/null 2>&1 || true
sleep 3
adb wait-for-device
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb logcat -c

shot() { adb exec-out screencap -p > "out/$1.png" 2>/dev/null || true; }

: > out/starts.txt
while read -r name cls extras; do
  [ -z "${name:-}" ] && continue
  case "$name" in \#*) continue;; esac
  echo "== $name ($cls)" >> out/starts.txt
  # </dev/null: adb shell would otherwise swallow the rest of activities.txt from the loop's stdin
  adb shell am start -W -n "$PKG/$cls" --ez ci_skip_onboarding true $extras >> out/starts.txt 2>&1 < /dev/null
  sleep 4
  shot "$name"
done < ci/activities.txt

# Make our launcher the HOME app (best effort) and press Home, to test the HOME flow
adb shell cmd package set-home-activity "$PKG/$(grep -m1 '^home ' ci/activities.txt | awk '{print $2}')" >> out/starts.txt 2>&1 || true
adb shell input keyevent KEYCODE_HOME
sleep 3
shot home-after-keyhome

# Random UI events inside our package
adb shell monkey -p "$PKG" --throttle 150 -s 42 --pct-syskeys 0 -v 700 > out/monkey.txt 2>&1 || true
sleep 2
shot after-monkey

adb logcat -d -b crash > out/crash.txt 2>&1 || true
adb logcat -d -s AndroidRuntime:E ActivityManager:E > out/runtime.txt 2>&1 || true

{
  echo "Emulator smoke test (API 29) for ${GITHUB_SHA:-local}"
  echo
  echo "Install: $(tr '\n' ' ' < out/install.txt)"
  echo
  echo "Monkey summary:"
  echo '```'
  grep -E "CRASH|ANR|Events injected|Monkey finished|// Long Msg|// Short Msg" out/monkey.txt | head -20
  echo '```'
  echo "Crash buffer:"
  echo '```'
  head -c 6000 out/crash.txt
  echo '```'
  echo "AndroidRuntime errors:"
  echo '```'
  head -c 8000 out/runtime.txt
  echo '```'
  echo "Start results:"
  echo '```'
  head -c 4000 out/starts.txt
  echo '```'
} > out/report.md
