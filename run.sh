#!/usr/bin/env bash
# Wraps the Kotlin/Native executable in an .app, installs it on a new iOS simulator, runs the
# timeline once per rendering mode and prints the app's REPRO lines. Deletes the simulator at the end.
#   ./gradlew linkReleaseExecutableIosSimulatorArm64 && ./run.sh
set -euo pipefail
cd "$(dirname "$0")"
app=build/DialogLayerRepro.app
bundle_id=io.github.repro.dialoglayer
device_type=${DEVICE_TYPE:-com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro}

rm -rf "$app" && mkdir -p "$app"
cp build/bin/iosSimulatorArm64/releaseExecutable/DialogLayerRepro.kexe "$app/DialogLayerRepro"
cp Info.plist "$app/"
codesign --force --sign - "$app" >/dev/null 2>&1

udid=$(xcrun simctl create cmp-dialog-layer-repro "$device_type" ${RUNTIME:-})
trap 'xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true; xcrun simctl delete "$udid"' EXIT
echo "simulator $udid ($device_type)"
xcrun simctl boot "$udid"
xcrun simctl bootstatus "$udid" -b >/dev/null
xcrun simctl install "$udid" "$app"
for mode in "" serial; do
  xcrun simctl launch --console-pty --terminate-running-process "$udid" "$bundle_id" $mode 2>&1 \
    | grep --line-buffered '^REPRO' || true
done
