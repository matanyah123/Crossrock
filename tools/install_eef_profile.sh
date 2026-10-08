#!/usr/bin/env bash
set -euo pipefail

# Build, validate, and install the current EEF artifacts into the local Fabric profile.
# This intentionally bypasses Modrinth's upload UI while keeping its local file index
# synchronized, so developer-built JARs do not get reported as needing repair.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROFILE_DIR="${EEF_PROFILE_DIR:-/Users/matanyah/Library/Application Support/ModrinthApp/profiles/Fabric 26.2}"
INSTANCE_ID="${EEF_INSTANCE_ID:-local:66fc74a9-de4a-48de-a3c7-b7c821f08522}"
DB_PATH="${MODRINTH_DB:-/Users/matanyah/Library/Application Support/ModrinthApp/app.db}"

cd "$ROOT_DIR"
./gradlew --no-daemon :eef:clean :eef:test :eef:build

JAR="$ROOT_DIR/eef/build/libs/entity-event-features-0.1.0.jar"
DEV_PACK="$ROOT_DIR/villager-news-java/villager-news-eef-generated.zip"
VN_PACK="$ROOT_DIR/villager-news-java/villager-news-eef-with-sounds.zip"
for path in "$JAR" "$DEV_PACK" "$VN_PACK"; do
	[[ -f "$path" ]] || { echo "Missing artifact: $path" >&2; exit 1; }
done
unzip -tq "$DEV_PACK"
unzip -tq "$VN_PACK"

mkdir -p "$PROFILE_DIR/mods" "$PROFILE_DIR/resourcepacks"
cp "$JAR" "$PROFILE_DIR/mods/entity-event-features-0.1.0.jar"
cp "$DEV_PACK" "$PROFILE_DIR/resourcepacks/eef-developer-test-pack.zip"
cp "$VN_PACK" "$PROFILE_DIR/resourcepacks/villager-news-java.zip"

[[ -f "$DB_PATH" ]] || { echo "Modrinth database not found: $DB_PATH" >&2; exit 1; }
backup="$DB_PATH.bak-eef-$(date +%Y%m%d-%H%M%S)"
cp "$DB_PATH" "$backup"

jar_sha="$(shasum -a 1 "$PROFILE_DIR/mods/entity-event-features-0.1.0.jar" | awk '{print $1}')"
jar_size="$(stat -f '%z' "$PROFILE_DIR/mods/entity-event-features-0.1.0.jar")"
dev_sha="$(shasum -a 1 "$PROFILE_DIR/resourcepacks/eef-developer-test-pack.zip" | awk '{print $1}')"
dev_size="$(stat -f '%z' "$PROFILE_DIR/resourcepacks/eef-developer-test-pack.zip")"
vn_sha="$(shasum -a 1 "$PROFILE_DIR/resourcepacks/villager-news-java.zip" | awk '{print $1}')"
vn_size="$(stat -f '%z' "$PROFILE_DIR/resourcepacks/villager-news-java.zip")"

sqlite3 "$DB_PATH" <<SQL
-- A developer replacement must not retain Modrinth's old reflink/content-store
-- binding. That binding is what causes the launch-time "needs repair" error even
-- when the visible SHA-1 and size have been updated.
DELETE FROM store_instance_files
 WHERE file_id=(SELECT id FROM instance_files
   WHERE instance_id='$INSTANCE_ID' AND relative_path='mods/entity-event-features-0.1.0.jar');
UPDATE instance_files SET sha1='$jar_sha', size=$jar_size, missing=0, enabled=1
 WHERE instance_id='$INSTANCE_ID' AND relative_path='mods/entity-event-features-0.1.0.jar';
UPDATE instance_files SET sha1='$dev_sha', size=$dev_size, missing=0, enabled=1
 WHERE instance_id='$INSTANCE_ID' AND relative_path='resourcepacks/eef-developer-test-pack.zip';
UPDATE instance_files SET sha1='$vn_sha', size=$vn_size, missing=0, enabled=1
 WHERE instance_id='$INSTANCE_ID' AND relative_path='resourcepacks/villager-news-java.zip';
SQL

echo "Installed and indexed EEF artifacts in: $PROFILE_DIR"
echo "Database backup: $backup"
sqlite3 "$DB_PATH" "SELECT relative_path,sha1,size,missing,enabled FROM instance_files WHERE instance_id='$INSTANCE_ID' AND relative_path IN ('mods/entity-event-features-0.1.0.jar','resourcepacks/eef-developer-test-pack.zip','resourcepacks/villager-news-java.zip');"
