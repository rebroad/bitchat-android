#!/bin/bash
# Parallel installation script for multiple Android devices

# Build the APK first (or use existing)
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# Check if APK exists and if source files are newer
NEEDS_BUILD=false

if [ ! -f "$APK_PATH" ]; then
    echo "APK not found. Building..."
    NEEDS_BUILD=true
else
    # Check if any source files are newer than the APK
    APK_TIME=$(stat -c %Y "$APK_PATH" 2>/dev/null || stat -f %m "$APK_PATH" 2>/dev/null)

    # Check Kotlin source files
    if find app/src/main/java -name "*.kt" -newer "$APK_PATH" 2>/dev/null | grep -q .; then
        echo "Source files are newer than APK. Rebuilding..."
        NEEDS_BUILD=true
    # Check resource files
    elif find app/src/main/res -newer "$APK_PATH" 2>/dev/null | grep -q .; then
        echo "Resource files are newer than APK. Rebuilding..."
        NEEDS_BUILD=true
    # Check manifest
    elif [ app/src/main/AndroidManifest.xml -nt "$APK_PATH" ] 2>/dev/null; then
        echo "Manifest is newer than APK. Rebuilding..."
        NEEDS_BUILD=true
    # Check build.gradle files
    elif find . -name "build.gradle*" -newer "$APK_PATH" 2>/dev/null | grep -q .; then
        echo "Build files are newer than APK. Rebuilding..."
        NEEDS_BUILD=true
    fi
fi

if [ "$NEEDS_BUILD" = true ]; then
    echo "Building APK..."
    ./gradlew assembleDebug || exit 1
    echo "Build complete."
else
    echo "Using existing APK: $APK_PATH"
fi

# Get list of devices with timeout
echo "Checking for connected devices..."
DEVICES=$(timeout 5 adb devices 2>&1 | grep -v "List" | grep "device$" | awk '{print $1}')

if [ $? -ne 0 ]; then
    echo "Error: ADB command failed or timed out. Is ADB running?"
    echo "Try: adb kill-server && adb start-server"
    exit 1
fi

if [ -z "$DEVICES" ]; then
    echo "No devices found!"
    echo "Make sure your device is connected and USB debugging is enabled."
    exit 1
fi

echo "Found $(echo $DEVICES | wc -w) device(s)"

# Install to each device in background
PIDS=()
MODELS=()
EXIT_CODES=()

# Convert DEVICES to array to iterate properly
DEVICE_ARRAY=($DEVICES)

SCRIPT_START=$(date +%s)

echo "Starting installations..."
for device in "${DEVICE_ARRAY[@]}"; do
    # Get model name for this device
    model=$(timeout 3 adb -s "$device" shell getprop ro.product.model 2>/dev/null || echo "$device")
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
    device="${DEVICE_ARRAY[$i]}"
    model="${MODELS[$i]}"
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
