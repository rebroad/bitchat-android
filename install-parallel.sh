#!/bin/bash
# Parallel installation script for multiple Android devices

# Build the APK first (or use existing)
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "Building APK..."
    ./gradlew assembleDebug
fi

# Get list of devices
DEVICES=$(adb devices | grep -v "List" | grep "device$" | awk '{print $1}')

if [ -z "$DEVICES" ]; then
    echo "No devices found!"
    exit 1
fi

echo "Installing to devices in parallel:"
echo "$DEVICES"
echo ""

# Install to each device in background
PIDS=()
for device in $DEVICES; do
    echo "Installing to $device..."
    adb -s "$device" install -r "$APK_PATH" &
    PIDS+=($!)
done

# Wait for all installations to complete
FAILED=0
for pid in "${PIDS[@]}"; do
    wait $pid
    if [ $? -ne 0 ]; then
        FAILED=1
    fi
done

if [ $FAILED -eq 0 ]; then
    echo ""
    echo "✓ Successfully installed on all devices"
else
    echo ""
    echo "✗ Some installations failed"
    exit 1
fi
