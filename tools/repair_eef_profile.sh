#!/usr/bin/env bash
set -euo pipefail

# Repair Modrinth metadata after directly editing/replacing EEF files.
# This script does not build, copy, replace, or modify any mod/resource-pack file.

PROFILE_DIR="${EEF_PROFILE_DIR:-/Users/matanyah/Library/Application Support/ModrinthApp/profiles/Fabric 26.2}"
INSTANCE_ID="${EEF_INSTANCE_ID:-local:66fc74a9-de4a-48de-a3c7-b7c821f08522}"
DB_PATH="${MODRINTH_DB:-/Users/matanyah/Library/Application Support/ModrinthApp/app.db}"

DEFAULT_FILES=(
	"mods/entity-event-features-0.1.0.jar"
	"resourcepacks/eef-developer-test-pack.zip"
	"resourcepacks/villager-news-java.zip"
)

if [[ $# -gt 0 ]]; then
	FILES=("$@")
else
	FILES=("${DEFAULT_FILES[@]}")
fi

[[ -f "$DB_PATH" ]] || { echo "Modrinth database not found: $DB_PATH" >&2; exit 1; }

for relative_path in "${FILES[@]}"; do
	[[ "$relative_path" != /* && "$relative_path" != *".."* ]] || {
		echo "Expected a safe profile-relative path, got: $relative_path" >&2
		exit 1
	}
	[[ -f "$PROFILE_DIR/$relative_path" ]] || {
		echo "Profile file not found: $PROFILE_DIR/$relative_path" >&2
		exit 1
	}
	db_id="$(sqlite3 "$DB_PATH" "SELECT id FROM instance_files WHERE instance_id='$INSTANCE_ID' AND relative_path='$relative_path';")"
	[[ -n "$db_id" ]] || {
		echo "Modrinth has no index entry for: $relative_path" >&2
		echo "Import that file once, then rerun this repair script." >&2
		exit 1
	}
done

backup="$DB_PATH.bak-eef-repair-$(date +%Y%m%d-%H%M%S)"
cp "$DB_PATH" "$backup"

for relative_path in "${FILES[@]}"; do
	full_path="$PROFILE_DIR/$relative_path"
	sha1="$(shasum -a 1 "$full_path" | awk '{print $1}')"
	size="$(stat -f '%z' "$full_path")"

	sqlite3 "$DB_PATH" <<SQL
DELETE FROM store_instance_files
 WHERE file_id=(SELECT id FROM instance_files
   WHERE instance_id='$INSTANCE_ID' AND relative_path='$relative_path');
UPDATE instance_files
 SET sha1='$sha1', size=$size, missing=0, enabled=1, modified_at=strftime('%s','now')
 WHERE instance_id='$INSTANCE_ID' AND relative_path='$relative_path';
SQL

	echo "Repaired: $relative_path ($size bytes, $sha1)"
done

echo "Database backup: $backup"
echo "Done. Fully quit and reopen Modrinth before launching the instance."
