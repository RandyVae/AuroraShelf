#!/usr/bin/env bash
# Apply / restore the LOCAL-ONLY launch intro assets used by the Netflix-preview baseline build.
#
# The clip under dist/local-intro/ is third-party, copyright/trademark protected material.
# It is intentionally NOT tracked by git and must not be distributed. Use it only for private
# local preview builds; the shippable build (with the original intro) is produced without it.
#
# Usage:
#   scripts/local-intro.sh apply     # copy local assets into res/ (working tree only)
#   scripts/local-intro.sh restore   # restore the committed original assets
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCAL="$ROOT/dist/local-intro"
TARGET_VIDEO="$ROOT/app/src/main/res/raw/intro_film.mp4"
TARGET_POSTER="$ROOT/app/src/main/res/drawable-nodpi/intro_poster.jpg"
TARGET_START="$ROOT/app/src/main/res/drawable-nodpi/intro_film_start.jpg"

case "${1:-}" in
  apply)
    for f in intro_film.mp4 intro_poster.jpg intro_film_start.jpg; do
      [ -f "$LOCAL/$f" ] || { echo "missing $LOCAL/$f" >&2; exit 1; }
    done
    cp "$LOCAL/intro_film.mp4" "$TARGET_VIDEO"
    cp "$LOCAL/intro_poster.jpg" "$TARGET_POSTER"
    cp "$LOCAL/intro_film_start.jpg" "$TARGET_START"
    echo "local intro applied (working tree only)"
    ;;
  restore)
    git -C "$ROOT" checkout -- "$TARGET_VIDEO" "$TARGET_POSTER" "$TARGET_START"
    echo "original intro restored"
    ;;
  *)
    echo "usage: $0 apply|restore" >&2
    exit 1
    ;;
esac
