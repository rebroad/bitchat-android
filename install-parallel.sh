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

# Install to each device in background
PIDS=()
MODELS=()
EXIT_CODES=()

# Convert DEVICES to array to iterate properly
DEVICE_ARRAY=($DEVICES)

SCRIPT_START=$(date +%s)

for device in "${DEVICE_ARRAY[@]}"; do
    # Get model name for this device
    model=$(adb -s "$device" shell getprop ro.product.model 2>/dev/null || echo "$device")
    MODELS+=("$model")

    # Run install in background, prefixing output lines with elapsed time and device model
    (
        set -o pipefail
        INSTALL_START=$(date +%s)
        stdbuf -oL -eL adb -s "$device" install -r "$APK_PATH" 2>&1 | while IFS= read -r line; do
            ELAPSED=$(($(date +%s) - INSTALL_START))
            echo "[${ELAPSED}s] $line [$model]"
        done
        exit ${PIPESTATUS[0]}
    ) &
    PIDS+=($!)
done

# Wait for all installations to complete and capture exit codes
FAILED=0
for i in "${!PIDS[@]}"; do
    wait ${PIDS[$i]}
    exit_code=$?
    EXIT_CODES+=($exit_code)
    if [ $exit_code -ne 0 ]; then
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
