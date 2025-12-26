#!/bin/bash

# FolderSync E2E Test Runner
# This script runs the end-to-end tests on a connected physical device

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Use JDK 17 for Android builds
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"

echo "================================================"
echo "FolderSync E2E Test Runner"
echo "================================================"
echo ""
echo "Using Java: $JAVA_HOME"
echo ""

# Check for connected devices
echo "Checking for connected devices..."
DEVICES=$(adb devices | grep -v "List" | grep "device$" | wc -l | tr -d ' ')

if [ "$DEVICES" -eq 0 ]; then
    echo "❌ No Android devices connected!"
    echo "Please connect a physical device with USB debugging enabled."
    exit 1
fi

echo "✓ Found $DEVICES connected device(s)"
echo ""

# List connected devices
adb devices -l | grep "device " || true
echo ""

# Build the app and test APK
echo "Building app and test APK..."
./gradlew assembleDebug assembleDebugAndroidTest

# Install the app
echo "Installing app..."
./gradlew installDebug

# Run the E2E tests
echo ""
echo "================================================"
echo "Running E2E Tests"
echo "================================================"
echo ""
echo "IMPORTANT: These tests require user interaction!"
echo "- When Google Sign-In appears, select your account"
echo "- When folder picker appears, select a folder"
echo ""
echo "The tests will wait for your input..."
echo ""

# Run the SimpleE2ETest (doesn't require Hilt runner)
adb shell am instrument -w \
    -e class "com.foldersync.e2e.SimpleE2ETest" \
    -e debug false \
    com.foldersync.test/androidx.test.runner.AndroidJUnitRunner

echo ""
echo "================================================"
echo "E2E Tests Complete"
echo "================================================"
