#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SOURCE="$SCRIPT_DIR/dist/vectr-core-linux"
TARGET_DIR="$HOME/.local/bin"
TARGET="$TARGET_DIR/vectr-core"

if [ ! -f "$SOURCE" ]; then
  echo "Missing $SOURCE. Run npm run build:dist first." >&2
  exit 1
fi

mkdir -p "$TARGET_DIR" "$HOME/axon-inbox" "$HOME/axon/captures/notes/attachments" \
  "$HOME/axon/files/incoming" "$HOME/axon/files/outgoing" "$HOME/axon/inventory/photos"
cp "$SOURCE" "$TARGET"
chmod +x "$TARGET"

echo "VeCTR core installed at $TARGET"
echo "Start it with: vectr-core"
echo "Admin console: http://localhost:${PORT:-4101}"
