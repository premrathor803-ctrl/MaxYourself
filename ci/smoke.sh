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

# ---- Center interaction checks (uses uiautomator to find buttons by their text)
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml < /dev/null > /dev/null 2>&1
  adb pull /sdcard/ui.xml out/ui.xml > /dev/null 2>&1
  python3 - "$1" <<'PY'
import re, sys
import xml.etree.ElementTree as ET
t = sys.argv[1]
try:
    root = ET.parse('out/ui.xml').getroot()
except Exception:
    sys.exit(0)
for n in root.iter('node'):
    if n.get('text') == t:
        m = re.findall(r'\d+', n.get('bounds') or '')
        if len(m) == 4:
            print((int(m[0]) + int(m[2])) // 2, (int(m[1]) + int(m[3])) // 2)
            break
PY
}
tap_label() {
  local xy
  xy=$(tap_text "$1")
  if [ -n "$xy" ]; then
    adb shell input tap $xy < /dev/null >> out/starts.txt 2>&1
    echo "tapped '$1' at $xy" >> out/starts.txt
  else
    echo "could not find '$1' on screen" >> out/starts.txt
  fi
}

adb shell am start -W -n "$PKG/.center.ui.CenterActivity" --es section player:beginner_full_a < /dev/null >> out/starts.txt 2>&1
sleep 3
shot c1-player-intro
tap_label "Start"
sleep 3
shot c2-player-work
tap_label "Done early"
sleep 2
shot c3-player-rest
tap_label "Skip rest"
sleep 2
shot c4-player-intro2
tap_label "End"
sleep 2
shot c5-player-end-dialog
tap_label "Save what I did and finish"
sleep 3
shot c6-after-save
adb shell am start -W -n "$PKG/.center.ui.CenterActivity" --es section history < /dev/null >> out/starts.txt 2>&1
sleep 3
shot c7-history-after-save
adb shell am start -W -n "$PKG/.center.ui.CenterActivity" < /dev/null >> out/starts.txt 2>&1
sleep 3
tap_label "Drink water"
sleep 3
shot c8-habit-detail

# Make our launcher the HOME app (best effort) and press Home, to test the HOME flow
adb shell cmd package set-home-activity "$PKG/$(grep -m1 '^home ' ci/activities.txt | awk '{print $2}')" >> out/starts.txt 2>&1 || true
adb shell input keyevent KEYCODE_HOME
sleep 3
shot home-after-keyhome

# ---- gesture checks on the home screen (drag & drop, folder, edit mode, long-press menu)
adb shell am start -W -n "$PKG/.ui.home.HomeActivity" --ez ci_skip_onboarding true --ei ci_page 1 < /dev/null >> out/starts.txt 2>&1
sleep 2
shot g0-home
# drag Calendar onto Email: should create a folder
adb shell input draganddrop 156 893 412 893 900 < /dev/null >> out/starts.txt 2>&1
sleep 2
shot g1-after-drag-folder
adb shell am start -W -n "$PKG/.ui.home.HomeActivity" --ez ci_folder true < /dev/null >> out/starts.txt 2>&1
sleep 2
shot g2-folder-dialog
adb shell input keyevent KEYCODE_BACK < /dev/null
sleep 1
adb shell am start -W -n "$PKG/.ui.home.HomeActivity" --ez ci_edit true < /dev/null >> out/starts.txt 2>&1
sleep 2
shot g3-edit-mode
adb shell am start -W -n "$PKG/.ui.home.HomeActivity" --ez ci_exit_edit true < /dev/null >> out/starts.txt 2>&1
sleep 1
# long press (no move) on Settings icon: quick menu
adb shell input swipe 412 630 412 630 1200 < /dev/null >> out/starts.txt 2>&1
sleep 1
shot g4-long-press-menu
adb shell input keyevent KEYCODE_BACK < /dev/null
sleep 1
# drag Settings to the dock area's neighbour page cell (empty cell on same page)
adb shell input draganddrop 412 630 668 1130 900 < /dev/null >> out/starts.txt 2>&1
sleep 2
shot g5-after-move

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
