#!/usr/bin/env sh
set -eu

REPOSITORY="NRS-1605/Vectr"
SOURCE_URL="${VECTR_BINARY_URL:-https://github.com/$REPOSITORY/releases/latest/download/vectr-core-linux}"
TARGET_DIR="$HOME/.local/bin"
TARGET="$TARGET_DIR/vectr-core"
TEMP_FILE=$(mktemp "${TMPDIR:-/tmp}/vectr-core-linux.XXXXXX")

trap 'rm -f "$TEMP_FILE"' EXIT INT TERM

mkdir -p "$TARGET_DIR" "$HOME/axon-inbox" "$HOME/axon/captures/notes/attachments" \
  "$HOME/axon/files/incoming" "$HOME/axon/files/outgoing" "$HOME/axon/inventory/photos"
echo "Downloading VeCTR core…"
if command -v curl >/dev/null 2>&1; then
  curl --fail --location --silent --show-error "$SOURCE_URL" -o "$TEMP_FILE"
elif command -v wget >/dev/null 2>&1; then
  wget -qO "$TEMP_FILE" "$SOURCE_URL"
else
  echo "curl or wget is required to download VeCTR." >&2
  exit 1
fi
cp "$TEMP_FILE" "$TARGET"
chmod +x "$TARGET"

echo "VeCTR core installed at $TARGET"
echo "Start it with: vectr-core"
echo "Admin console: http://localhost:${PORT:-4101}"
