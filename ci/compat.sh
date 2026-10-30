#!/usr/bin/env bash
# Older-Android check and before/after measurement, run on the emulator the workflow starts (API 24, the lowest Elevate supports).
#  1. Measures the previous release (build-24, before the height/gaming/accessibility work) and the current build on the SAME emulator:
#     APK size, cold start time of three screens (median of 5 runs after one warm-up run), memory after settling.
#  2. Opens the main screens of the current build on this Android version, runs the in-app self-tests and a short monkey run,
#     and collects crashes.
# These are emulator numbers. They are not a phone's numbers, and they are only comparable with each other within this run.
set -u
mkdir -p out
PKG=com.elevate.launcher
NEW=apk/app-debug.apk
OLD=old/app-debug.apk

adb wait-for-device
adb shell input keyevent 82 || true
adb root > /dev/null 2>&1 || true
sleep 3
adb wait-for-device
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
SDK=$(adb shell getprop ro.build.version.sdk < /dev/null | tr -d '\r')
REL=$(adb shell getprop ro.build.version.release < /dev/null | tr -d '\r')
echo "emulator: Android $REL (API $SDK)" > out/compat-env.txt
adb logcat -c

shot() { adb exec-out screencap -p > "out/$1.png" 2>/dev/null || true; }

cold_start() { # activity [extra args]: prints TotalTime in ms of a cold start, or nothing
  local act="$1"; shift
  timeout 45 adb shell am force-stop "$PKG" < /dev/null > /dev/null 2>&1
  sleep 2
  timeout 60 adb shell am start -W -n "$PKG/$act" --ez ci_skip_onboarding true "$@" < /dev/null 2>/dev/null | tr -d '\r' | awk '/^TotalTime:/ {print $2}'
}

median() { python3 -c 'import sys; v=sorted(int(x) for x in sys.argv[1:]); print(v[len(v)//2] if v else "n/a")' "$@"; }

mem_pss() { # total PSS in KB of the app process
  timeout 60 adb shell dumpsys meminfo "$PKG" < /dev/null 2>/dev/null | tr -d '\r' | python3 -c '
import re, sys
t = sys.stdin.read()
m = re.search(r"TOTAL PSS:\s+(\d+)", t) or re.search(r"^\s*TOTAL\s+(\d+)", t, re.M)
print(m.group(1) if m else "n/a")'
}

bench() { # label apk
  local label="$1" apk="$2"
  if [ ! -f "$apk" ]; then echo "$label|missing|n/a|n/a|n/a|n/a" >> out/bench.tsv; return; fi
  timeout 120 adb uninstall "$PKG" < /dev/null > /dev/null 2>&1
  timeout 180 adb install -r "$apk" < /dev/null >> out/install.txt 2>&1
  local size; size=$(stat -c %s "$apk")
  local res=()
  for entry in ".ui.home.HomeActivity" ".center.ui.CenterActivity" ".gaming.GamingActivity"; do
    cold_start "$entry" > /dev/null   # warm-up, not counted
    local v=()
    for i in 1 2 3 4 5; do
      t=$(cold_start "$entry"); [ -n "$t" ] && v+=("$t")
    done
    res+=("$(median "${v[@]}")")
  done
  cold_start ".ui.home.HomeActivity" > /dev/null
  sleep 8
  echo "$label|$size|${res[0]}|${res[1]}|${res[2]}|$(mem_pss)" >> out/bench.tsv
}

: > out/bench.tsv
: > out/install.txt
bench "build-24 (before)" "$OLD"
bench "current build (after)" "$NEW"

# ---- the current build on this Android version
timeout 120 adb uninstall "$PKG" < /dev/null > /dev/null 2>&1
timeout 180 adb install -r "$NEW" < /dev/null >> out/install.txt 2>&1
adb logcat -c
: > out/starts.txt
while read -r name cls extras; do
  [ -z "${name:-}" ] && continue
  case "$name" in \#*) continue;; esac
  echo "== $name ($cls)" >> out/starts.txt
  timeout 60 adb shell am start -W -n "$PKG/$cls" --ez ci_skip_onboarding true $extras >> out/starts.txt 2>&1 < /dev/null
  sleep 4
  shot "a$SDK-$name"
done < ci/compat.txt

adb shell monkey -p "$PKG" --throttle 150 -s 7 --pct-syskeys 0 -v 400 > out/monkey.txt 2>&1 || true
sleep 2
shot "a$SDK-after-monkey"
adb logcat -d -s ElevateCI:I > out/ci-log.txt 2>&1 || true
adb logcat -d -b crash > out/crash.txt 2>&1 || true
adb logcat -d -s AndroidRuntime:E > out/runtime.txt 2>&1 || true

{
  echo "Older-Android check and before/after measurement ($(cat out/compat-env.txt))"
  echo
  echo "Emulator numbers, not a phone's. Debug builds (not minified). Cold start = \`am start -W\` TotalTime, median of 5 runs after one warm-up. Memory = total PSS of the app after the home screen settled for 8 s."
  echo
  echo "| Build | APK size | Home cold start | Center cold start | Gaming cold start | Memory (PSS) |"
  echo "|---|---|---|---|---|---|"
  while IFS='|' read -r label size home center gaming mem; do
    if [ "$size" = "missing" ]; then echo "| $label | not available | | | | |"; continue; fi
    kb=$(python3 -c "print(round($size/1048576,2))")
    memmb=$(python3 -c "
m='$mem'
print(m if m=='n/a' else str(round(int(m)/1024,1))+' MB')")
    echo "| $label | ${kb} MB | ${home} ms | ${center} ms | ${gaming} ms | ${memmb} |"
  done < out/bench.tsv
  echo
  echo "Screens opened on this Android version with the current build (screenshots a$SDK-*): $(grep -c '^== ' out/starts.txt); start statuses:"
  echo '```'
  grep -E "^== |Status:" out/starts.txt | paste -sd' ' | sed 's/== /\n== /g' | head -40
  echo '```'
  echo "Crashes on this Android version:"
  echo '```'
  head -c 4000 out/crash.txt
  head -c 4000 out/runtime.txt
  echo '```'
  echo "Monkey summary:"
  echo '```'
  grep -E "CRASH|ANR|Events injected|Monkey finished" out/monkey.txt | head -10
  echo '```'
  echo "In-app self-checks on this Android version:"
  echo '```'
  grep -E "growth selftest|game_sessions" out/ci-log.txt | head -10
  echo '```'
} > out/report.md
