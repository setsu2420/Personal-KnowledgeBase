#!/usr/bin/env bash
# Generate latest.json for Tauri 2 auto-updater
# Usage: ./scripts/generate-latest-json.sh [version]
#   version: optional, defaults to version from src-tauri/Cargo.toml
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CARGO_TOML="$ROOT_DIR/src-tauri/Cargo.toml"
OUTPUT_FILE="$ROOT_DIR/src-tauri/latest.json"

GITHUB_REPO="setsu2420/Personal-KnowledgeBase"
PRODUCT_NAME="Personal-KnowledgeBase"

# --- Resolve version ---
if [ -n "$1" ]; then
  VERSION="$1"
else
  VERSION="$(grep '^version' "$CARGO_TOML" | head -1 | sed 's/.*"\(.*\)".*/\1/')"
fi

if [ -z "$VERSION" ]; then
  echo "[Error] Could not determine version. Pass it as an argument or ensure Cargo.toml exists."
  exit 1
fi

# --- Current date in ISO 8601 ---
PUB_DATE="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

# --- Download URLs ---
BASE_URL="https://github.com/${GITHUB_REPO}/releases/download/v${VERSION}"

# --- Generate JSON ---
cat > "$OUTPUT_FILE" <<EOF
{
  "version": "${VERSION}",
  "notes": "Release v${VERSION}",
  "pub_date": "${PUB_DATE}",
  "platforms": {
    "darwin-x86_64": {
      "signature": "",
      "url": "${BASE_URL}/${PRODUCT_NAME}_x64.dmg"
    },
    "darwin-aarch64": {
      "signature": "",
      "url": "${BASE_URL}/${PRODUCT_NAME}_aarch64.dmg"
    },
    "linux-x86_64": {
      "signature": "",
      "url": "${BASE_URL}/${PRODUCT_NAME}_x86_64.AppImage.tar.gz"
    },
    "windows-x86_64": {
      "signature": "",
      "url": "${BASE_URL}/${PRODUCT_NAME}_x64-setup.exe"
    }
  }
}
EOF

echo "========================================="
echo "  latest.json generated successfully!"
echo "========================================="
echo ""
echo "  Version : ${VERSION}"
echo "  Pub Date: ${PUB_DATE}"
echo "  Output  : ${OUTPUT_FILE}"
echo ""
echo "========================================="
echo "  Next steps:"
echo "========================================="
echo ""
echo "1. Build the app for each platform (or collect artifacts from CI):"
echo "   - macOS x64  : ${PRODUCT_NAME}_x64.dmg"
echo "   - macOS ARM  : ${PRODUCT_NAME}_aarch64.dmg"
echo "   - Linux x64  : ${PRODUCT_NAME}_x86_64.AppImage.tar.gz"
echo "   - Windows x64: ${PRODUCT_NAME}_x64-setup.exe"
echo ""
echo "2. Sign each bundle with your Tauri updater private key:"
echo "   npx tauri signer sign -k ~/.tauri/myapp.key <bundle-path>"
echo "   Then copy the .sig content into the corresponding \"signature\" field in latest.json."
echo ""
echo "3. Upload bundles to GitHub Releases:"
echo "   gh release create v${VERSION} \\"
echo "     --repo ${GITHUB_REPO} \\"
echo "     --title \"v${VERSION}\" \\"
echo "     --notes \"Release v${VERSION}\" \\"
echo "     <path-to-bundles>"
echo ""
echo "4. Push latest.json to the repository main branch:"
echo "   git add src-tauri/latest.json"
echo "   git commit -m \"chore: update latest.json to v${VERSION}\""
echo "   git push origin main"
echo ""
echo "  The updater endpoint is configured as:"
echo "  https://raw.githubusercontent.com/${GITHUB_REPO}/main/latest.json"
echo "========================================="
