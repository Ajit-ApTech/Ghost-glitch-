#!/bin/bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR/mac-app"

echo "🔨 Building production release binary..."
swift build -c release

BIN_PATH="$DIR/mac-app/.build/arm64-apple-macosx/release/NotchBuddy"
APP_NAME="Notch Buddy.app"
DEST="/Applications/$APP_NAME"

echo "📦 Packaging $APP_NAME bundle..."
rm -rf "$DIR/$APP_NAME"
mkdir -p "$DIR/$APP_NAME/Contents/MacOS"
mkdir -p "$DIR/$APP_NAME/Contents/Resources"

cp "$BIN_PATH" "$DIR/$APP_NAME/Contents/MacOS/NotchBuddy"
chmod +x "$DIR/$APP_NAME/Contents/MacOS/NotchBuddy"

cat << 'EOF' > "$DIR/$APP_NAME/Contents/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>NotchBuddy</string>
    <key>CFBundleIdentifier</key>
    <string>com.ajit.NotchBuddy</string>
    <key>CFBundleName</key>
    <string>Notch Buddy</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>LSUIElement</key>
    <true/>
</dict>
</plist>
EOF

echo "🚀 Installing to /Applications/..."
rm -rf "$DEST"
cp -R "$DIR/$APP_NAME" "/Applications/"

echo "✅ 'Notch Buddy.app' installed successfully in /Applications!"
echo "   - You can launch it anytime via Spotlight (Cmd + Space -> 'Notch Buddy')"
echo "   - To start automatically at login: System Settings > General > Login Items > add 'Notch Buddy'"
