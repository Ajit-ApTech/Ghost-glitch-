#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR/mac-app"
./.build/arm64-apple-macosx/release/NotchBuddy
