#!/usr/bin/env sh
# Builds a 1.12.2 resource pack with the OFFICIAL Mojang textures for this
# mod's items, extracted from YOUR OWN legally owned Minecraft client jar.
# Nothing Mojang-made is committed to the repo; run this locally only.
#
# Usage: ./tools/apply_official_textures.sh /path/to/1.21.x.jar
# The pack appears at OfficialArt/ -> copy it into your resourcepacks folder.
set -e
if [ $# -ne 1 ]; then
  echo "Usage: $0 /path/to/modern-client.jar (e.g. ~/.minecraft/versions/1.21.4/1.21.4.jar)" >&2
  exit 1
fi
JAR="$1"
MODDIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$MODDIR/OfficialArt/assets/spearmacebackport/textures/items"
mkdir -p "$OUT"
for tex in mace heavy_core breeze_rod diamond_spear; do
  if unzip -l "$JAR" "assets/minecraft/textures/item/$tex.png" >/dev/None 2>&1; then
    unzip -p "$JAR" "assets/minecraft/textures/item/$tex.png" > "$OUT/$tex.png"
    echo "extracted $tex.png"
  else
    echo "WARNING: $tex.png not in $JAR (needs a modern client jar, 1.21+)" >&2
  fi
done
cat > "$MODDIR/OfficialArt/pack.mcmeta" <<'EOF'
{
  "pack": {
    "pack_format": 3,
    "description": "Official Mojang art overlay for SpearMaceBackport (local use only, extracted from your own client)"
  }
}
EOF
echo "Pack ready at $MODDIR/OfficialArt — copy it to your 1.12.2 resourcepacks folder."
