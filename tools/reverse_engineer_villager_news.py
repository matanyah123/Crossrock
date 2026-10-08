#!/usr/bin/env python3
"""Extract a reproducible Villager News reverse-engineering catalog.

The input is the original .mcaddon (a ZIP containing a Bedrock resource pack
and behavior pack).  The script never changes the source archive.  It emits
lossless JSON/CSV catalogs plus a Java-resource-pack sound tree with stable,
semantic filenames.
"""

from __future__ import annotations

import argparse
import ast
import csv
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import zipfile
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def balanced_end(text: str, start: int, opening: str, closing: str) -> int:
    """Return the closing delimiter, ignoring delimiters inside JS strings."""
    depth = 0
    quote: str | None = None
    escaped = False
    index = start
    while index < len(text):
        char = text[index]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        else:
            if char in "'\"`":
                quote = char
            elif char == opening:
                depth += 1
            elif char == closing:
                depth -= 1
                if depth == 0:
                    return index
        index += 1
    raise ValueError(f"Unbalanced {opening!r} at byte {start}")


def js_string(token: str) -> str:
    try:
        return ast.literal_eval(token)
    except (ValueError, SyntaxError):
        # The source only uses ordinary quoted strings in the extracted data.
        return token[1:-1].replace(r"\'", "'").replace(r'\"', '"').replace(r"\\", "\\")


STRING = r'("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')'


def scalar(block: str, name: str) -> Any:
    match = re.search(rf"\b{re.escape(name)}\s*:\s*({STRING}|-?\d+(?:\.\d+)?)", block)
    if not match:
        return None
    token = match.group(1)
    if token[0] in "'\"":
        return js_string(token)
    return float(token) if "." in token else int(token)


def object_slice(source: str, assignment: str) -> str:
    match = re.search(rf"\b{re.escape(assignment)}\s*=\s*\{{", source)
    if not match:
        raise ValueError(f"Object {assignment!r} not found")
    start = match.end() - 1
    return source[start : balanced_end(source, start, "{", "}") + 1]


def parse_lang(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if "=" not in line or line.lstrip().startswith("#"):
            continue
        key, value = line.split("=", 1)
        values[key] = value.removesuffix("\t#")
    return values


def parse_simple_string_object(source: str, name: str) -> dict[str, str]:
    block = object_slice(source, name)
    result: dict[str, str] = {}
    for match in re.finditer(rf"\b([A-Za-z_$][\w$]*)\s*:\s*({STRING})", block):
        result[match.group(1)] = js_string(match.group(2))
    return result


def parse_dialogues(source: str, language: dict[str, str]) -> list[dict[str, Any]]:
    dialogues: list[dict[str, Any]] = []
    needle = "Dialog.kidjht("
    position = 0
    while (start := source.find(needle, position)) >= 0:
        end = balanced_end(source, start + len(needle) - 1, "(", ")")
        call = source[start : end + 1]
        dialogue_id = scalar(call, "id")
        before = source[max(0, start - 100) : start]
        assignments = list(re.finditer(r"([A-Za-z_$][\w$]*)\s*=\s*$", before))
        symbol = assignments[-1].group(1) if assignments else None
        array_match = re.search(r"\bslhkqn\s*:\s*\[", call)
        if not dialogue_id or not array_match:
            raise ValueError(f"Malformed dialogue call at byte {start}")
        array_start = array_match.end() - 1
        array_end = balanced_end(call, array_start, "[", "]")
        array = call[array_start + 1 : array_end]
        variants: list[dict[str, Any]] = []
        cursor = 0
        while cursor < len(array):
            while cursor < len(array) and array[cursor] in " \t\r\n,":
                cursor += 1
            if cursor >= len(array):
                break
            if array[cursor] != "{":
                raise ValueError(f"Unexpected dialogue variant in {dialogue_id!r}")
            variant_end = balanced_end(array, cursor, "{", "}")
            variant_source = array[cursor : variant_end + 1]
            cues = []
            cue_pattern = rf"([0-9.]+)\s*:\s*\{{\s*ysyeto\s*:\s*({STRING})"
            for cue in re.finditer(cue_pattern, variant_source):
                key = js_string(cue.group(2))
                cues.append({"time_seconds": float(cue.group(1)), "translation_key": key, "text": language.get(key)})
            transcript = " ".join(cue["text"] or f"[{cue['translation_key']}]" for cue in cues)
            variants.append(
                {
                    "index": len(variants) + 1,
                    "sound_id": scalar(variant_source, "soundId"),
                    "animation_id": scalar(variant_source, "animationName"),
                    "duration_seconds": scalar(variant_source, "duration"),
                    "weight": scalar(variant_source, "weight") or 1,
                    "cooldown_tags_source": re.findall(r"\b([A-Za-z_$][\w$]*)\s*:\s*(?:qigkgd|mcfdvm|qajxxg)\b", variant_source),
                    "subtitle_cues": cues,
                    "transcript": transcript,
                }
            )
            cursor = variant_end + 1
        line = source.count("\n", 0, start) + 1
        dialogues.append({"dialogue_id": dialogue_id, "source_symbol": symbol, "source_line_minified": line, "variants": variants})
        position = end + 1
    return dialogues


def parse_guides(source: str) -> dict[str, dict[str, Any]]:
    generic_titles = parse_simple_string_object(source, "lkryzq")
    guides: dict[str, dict[str, Any]] = {}
    pattern = re.compile(r"\[uxyuyr\.([A-Za-z0-9_]+)\]\s*:\s*\{")
    for match in pattern.finditer(source):
        start = match.end() - 1
        block = source[start : balanced_end(source, start, "{", "}") + 1]
        body = scalar(block, "body")
        if not body or "Trigger" not in body:
            continue
        title = scalar(block, "title")
        if title is None:
            title_ref = re.search(r"\btitle\s*:\s*lkryzq\.([A-Za-z0-9_]+)", block)
            title = generic_titles.get(title_ref.group(1), title_ref.group(1)) if title_ref else match.group(1)
        clean = re.sub(r"§[0-9A-FK-ORa-fk-or]", "", body)
        trigger_match = re.search(r"Trigger\s*(.*?)\s*Reaction\s*(.*)", clean, re.S)
        guides[match.group(1)] = {
            "dialogue_id": match.group(1),
            "title": title,
            "short_title": scalar(block, "bkltkm") or title,
            "trigger": trigger_match.group(1).strip() if trigger_match else "",
            "reaction": trigger_match.group(2).strip() if trigger_match else "",
            "handbook_body": clean,
        }
    return guides


def parse_categories(source: str) -> tuple[dict[str, str], dict[str, str]]:
    category_keys = parse_simple_string_object(source, "juuyxy")
    section_keys = parse_simple_string_object(source, "hbkffr")

    category_names: dict[str, str] = {}
    for match in re.finditer(rf"\[juuyxy\.([A-Za-z0-9_]+)\]\s*:\s*({STRING})", object_slice(source, "pyveoi")):
        category_names[match.group(1)] = js_string(match.group(2))
    section_names: dict[str, str] = {}
    for match in re.finditer(rf"\[hbkffr\.([A-Za-z0-9_]+)\]\s*:\s*({STRING})", object_slice(source, "eawipt")):
        section_names[match.group(1)] = js_string(match.group(2))

    section_to_category: dict[str, str] = {}
    for match in re.finditer(r"\[juuyxy\.([A-Za-z0-9_]+)\]\s*:\s*\[", object_slice(source, "negxgt")):
        start = match.end() - 1
        block = object_slice(source, "negxgt")
        # Match offsets are relative to block, so obtain the array from that block.
        local_match = re.search(rf"\[juuyxy\.{re.escape(match.group(1))}\]\s*:\s*\[", block)
        if not local_match:
            continue
        array_start = local_match.end() - 1
        array = block[array_start : balanced_end(block, array_start, "[", "]") + 1]
        for section in re.findall(r"hbkffr\.([A-Za-z0-9_]+)", array):
            section_to_category[section] = category_names.get(match.group(1), match.group(1))

    dialogue_categories: dict[str, str] = {}
    dialogue_sections: dict[str, str] = {}
    block = object_slice(source, "qoimcn")
    for match in re.finditer(r"\[hbkffr\.([A-Za-z0-9_]+)\]\s*:\s*\[", block):
        section = match.group(1)
        array_start = match.end() - 1
        array = block[array_start : balanced_end(block, array_start, "[", "]") + 1]
        for dialogue_id in re.findall(r"uxyuyr\.([A-Za-z0-9_]+)", array):
            dialogue_categories[dialogue_id] = section_to_category.get(section, "")
            dialogue_sections[dialogue_id] = section_names.get(section, section)
    return dialogue_categories, dialogue_sections


def parse_subscriptions(source: str) -> list[dict[str, Any]]:
    subscriptions = []
    pattern = re.compile(r"\.afterEvents\.([A-Za-z0-9_]+)\.subscribe\(")
    for index, match in enumerate(pattern.finditer(source), 1):
        start = match.end() - 1
        end = balanced_end(source, start, "(", ")")
        call = source[match.start() : end + 1]
        namespaces = sorted(set(re.findall(r"namespaces\s*:\s*\[([^]]*)\]", call)))
        filters = {
            "entity_types": sorted(set(re.findall(r"entityTypes\s*:\s*\[([^]]*)\]", call))),
            "event_types": sorted(set(re.findall(r"eventTypes\s*:\s*\[([^]]*)\]", call))),
            "namespaces_source": namespaces,
        }
        subscriptions.append(
            {
                "index": index,
                "bedrock_event": match.group(1),
                "source_line_minified": source.count("\n", 0, match.start()) + 1,
                "filters_source": filters,
                "handler_source": call,
            }
        )
    return subscriptions


def ffprobe_duration(path: Path) -> float | None:
    try:
        output = subprocess.check_output(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(path)],
            text=True,
        ).strip()
        return round(float(output), 6)
    except (FileNotFoundError, subprocess.CalledProcessError, ValueError):
        return None


def slug(value: str, limit: int = 72) -> str:
    value = value.lower().replace("#", " number ").replace("'", "")
    value = re.sub(r"[^a-z0-9]+", "_", value).strip("_")
    return (value[:limit].rstrip("_") or "untitled")


def json_dump(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def csv_dump(path: Path, fieldnames: list[str], rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def manifest_type(path: Path) -> str | None:
    manifest = json.loads((path / "manifest.json").read_text(encoding="utf-8-sig"))
    modules = manifest.get("modules", [])
    return modules[0].get("type") if modules else None


def resource_inventory(root: Path) -> dict[str, Any]:
    files = [path for path in root.rglob("*") if path.is_file()]
    by_extension = Counter((path.suffix.lower() or "[none]") for path in files)
    by_top_level = Counter(path.relative_to(root).parts[0] for path in files)
    return {
        "file_count": len(files),
        "total_bytes": sum(path.stat().st_size for path in files),
        "by_extension": dict(sorted(by_extension.items())),
        "by_top_level_directory": dict(sorted(by_top_level.items())),
    }


def entity_catalog(rp: Path, bp: Path, language: dict[str, str]) -> list[dict[str, Any]]:
    behavior_by_id: dict[str, dict[str, Any]] = {}
    for path in sorted((bp / "entities").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))["minecraft:entity"]
        desc = data["description"]
        behavior_by_id[desc["identifier"]] = {"file": str(path.relative_to(bp)), "description": desc, "components": data.get("components", {}), "component_groups": data.get("component_groups", {}), "events": data.get("events", {})}
    result = []
    for path in sorted((rp / "entity").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))["minecraft:client_entity"]["description"]
        identifier = data["identifier"]
        result.append(
            {
                "identifier": identifier,
                "display_name": language.get(f"entity.{identifier}.name", identifier),
                "resource_file": str(path.relative_to(rp)),
                "behavior": behavior_by_id.get(identifier),
                "materials": data.get("materials", {}),
                "textures": data.get("textures", {}),
                "geometry": data.get("geometry", {}),
                "animations": data.get("animations", {}),
                "render_controllers": data.get("render_controllers", []),
                "spawn_egg": data.get("spawn_egg"),
            }
        )
    return result


def model_catalog(rp: Path) -> list[dict[str, Any]]:
    result = []
    for path in sorted((rp / "models" / "entity").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for geometry in data.get("minecraft:geometry", []):
            desc = geometry.get("description", {})
            bones = geometry.get("bones", [])
            result.append(
                {
                    "source_file": str(path.relative_to(rp)),
                    "identifier": desc.get("identifier"),
                    "texture_width": desc.get("texture_width"),
                    "texture_height": desc.get("texture_height"),
                    "visible_bounds": {key: value for key, value in desc.items() if key.startswith("visible_bounds")},
                    "bone_count": len(bones),
                    "bones": [bone.get("name") for bone in bones],
                    "cube_count": sum(len(bone.get("cubes", [])) for bone in bones),
                }
            )
    return result


def animation_catalog(rp: Path) -> list[dict[str, Any]]:
    result = []
    for path in sorted((rp / "animations").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for identifier, animation in data.get("animations", {}).items():
            bones = animation.get("bones", {})
            result.append(
                {
                    "identifier": identifier,
                    "source_file": str(path.relative_to(rp)),
                    "animation_length_seconds": animation.get("animation_length"),
                    "loop": animation.get("loop", False),
                    "override_previous_animation": animation.get("override_previous_animation", False),
                    "bone_count": len(bones),
                    "bones": sorted(bones),
                    "sound_effects": animation.get("sound_effects", {}),
                    "particle_effects": animation.get("particle_effects", {}),
                    "timeline": animation.get("timeline", {}),
                }
            )
    return result


def controller_catalog(rp: Path, directory: str, root_key: str) -> list[dict[str, Any]]:
    result = []
    for path in sorted((rp / directory).glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for identifier, definition in data.get(root_key, {}).items():
            result.append({"identifier": identifier, "source_file": str(path.relative_to(rp)), "definition": definition})
    return result


def attachable_catalog(rp: Path, language: dict[str, str]) -> list[dict[str, Any]]:
    result = []
    for path in sorted((rp / "attachables").glob("*.json")):
        definition = json.loads(path.read_text(encoding="utf-8-sig"))["minecraft:attachable"]["description"]
        identifier = definition["identifier"]
        result.append(
            {
                "identifier": identifier,
                "display_name": language.get(f"item.{identifier}.name", identifier),
                "source_file": str(path.relative_to(rp)),
                "materials": definition.get("materials", {}),
                "textures": definition.get("textures", {}),
                "geometry": definition.get("geometry", {}),
                "animations": definition.get("animations", {}),
                "render_controllers": definition.get("render_controllers", []),
            }
        )
    return result


def behavior_artifacts(bp: Path) -> dict[str, Any]:
    recipes = []
    for path in sorted((bp / "recipes").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        recipe_key = next((key for key in data if key.startswith("minecraft:recipe_")), None)
        recipes.append({"source_file": str(path.relative_to(bp)), "recipe_type": recipe_key, "definition": data.get(recipe_key, {})})
    trades = []
    for path in sorted((bp / "trading").rglob("*.json")):
        trades.append({"source_file": str(path.relative_to(bp)), "definition": json.loads(path.read_text(encoding="utf-8-sig"))})
    functions = []
    for path in sorted((bp / "functions").rglob("*.mcfunction")):
        lines = [line for line in path.read_text(encoding="utf-8-sig").splitlines() if line.strip() and not line.lstrip().startswith("#")]
        functions.append({"source_file": str(path.relative_to(bp)), "commands": lines})
    return {"recipes": recipes, "trades": trades, "functions": functions}


def file_manifest(root: Path, label: str) -> list[dict[str, Any]]:
    result = []
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        result.append(
            {
                "pack": label,
                "path": str(path.relative_to(root)),
                "bytes": path.stat().st_size,
                "extension": path.suffix.lower(),
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            }
        )
    return result


def item_catalog(bp: Path, language: dict[str, str]) -> list[dict[str, Any]]:
    result = []
    for path in sorted((bp / "items").glob("*.json")):
        item = json.loads(path.read_text(encoding="utf-8-sig"))["minecraft:item"]
        desc = item["description"]
        identifier = desc["identifier"]
        result.append(
            {
                "identifier": identifier,
                "display_name": language.get(f"item.{identifier}.name", identifier),
                "source_file": str(path.relative_to(bp)),
                "menu_category": desc.get("menu_category"),
                "components": item.get("components", {}),
            }
        )
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("addon", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    addon = args.addon.resolve()
    output = args.output.resolve()
    if not addon.is_file():
        raise SystemExit(f"Missing add-on: {addon}")

    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="villager-news-re-") as temporary:
        extracted = Path(temporary)
        with zipfile.ZipFile(addon) as archive:
            archive.extractall(extracted)
        manifests = [path.parent for path in extracted.rglob("manifest.json")]
        rp = next(path for path in manifests if manifest_type(path) == "resources")
        bp = next(path for path in manifests if manifest_type(path) == "data")

        script_path = next((bp / "scripts").rglob("*.js"))
        source = script_path.read_text(encoding="utf-8-sig")
        language = parse_lang(rp / "texts" / "en_US.lang")
        dialogues = parse_dialogues(source, language)
        guides = parse_guides(source)
        categories, sections = parse_categories(source)
        # The handbook renders real-world day entries through a special submenu
        # (`tqhsxc`) instead of the ordinary `qoimcn` category arrays.
        day_words = ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "weekend")
        for dialogue_id, guide in guides.items():
            if not categories.get(dialogue_id) and any(word in guide["title"].lower() for word in day_words):
                categories[dialogue_id] = "World & Time"
                sections[dialogue_id] = "Real-World Days"
        sound_definitions = json.loads((rp / "sounds" / "sound_definitions.json").read_text(encoding="utf-8-sig"))["sound_definitions"]

        for dialogue in dialogues:
            guide = guides.get(dialogue["dialogue_id"], {})
            dialogue.update(
                {
                    "title": guide.get("title"),
                    "category": categories.get(dialogue["dialogue_id"]),
                    "section": sections.get(dialogue["dialogue_id"]),
                    "trigger": guide.get("trigger"),
                    "reaction": guide.get("reaction"),
                }
            )

        sound_rows: list[dict[str, Any]] = []
        sound_by_id: dict[str, dict[str, Any]] = {}
        alternate_sound_uses = {
            match.group(1): match.group(3)
            for match in re.finditer(
                rf"soundId\s*:\s*({STRING})\s*,\s*yomnxm\s*:\s*({STRING})",
                source,
            )
        }
        alternate_sound_uses = {js_string(key): js_string(value) for key, value in alternate_sound_uses.items()}
        for sound_id, definition in sound_definitions.items():
            sound = definition.get("sounds", [{}])[0]
            bedrock_name = sound if isinstance(sound, str) else sound.get("name")
            path = rp / f"{bedrock_name}.ogg"
            row = {
                "sound_id": sound_id,
                "bedrock_path": f"{bedrock_name}.ogg",
                "bytes": path.stat().st_size if path.exists() else None,
                "duration_seconds": ffprobe_duration(path) if path.exists() else None,
                "category": definition.get("category"),
                "min_distance": definition.get("min_distance"),
                "subtitle_key": definition.get("subtitle"),
                "non_dialogue_animation": alternate_sound_uses.get(sound_id),
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None,
            }
            sound_rows.append(row)
            sound_by_id[sound_id] = row

        # The shipped script contains one namespace/identifier typo.  The only
        # unreferenced clip whose measured duration matches 3.58 seconds is beo.ogg.
        typo_id = "orevillele_vn:uookqp.17"
        typo_recovery = "oreville_vn:lekufv"
        variant_rows: list[dict[str, Any]] = []
        used_sound_ids: set[str] = set()
        java_sounds: dict[str, Any] = {}
        java_lang: list[str] = []
        java_root = output / "java-resource-pack"
        voice_root = java_root / "assets" / "villager_news" / "sounds" / "voice"
        voice_root.mkdir(parents=True, exist_ok=True)
        for dialogue in dialogues:
            title_slug = slug(dialogue.get("title") or dialogue["dialogue_id"], 48)
            for variant in dialogue["variants"]:
                original_id = variant["sound_id"]
                resolved_id = typo_recovery if original_id == typo_id else original_id
                definition = sound_by_id.get(resolved_id)
                if definition:
                    used_sound_ids.add(resolved_id)
                transcript_slug = slug(variant["transcript"], 64)
                semantic_name = f"{title_slug}/{variant['index']:02d}_{transcript_slug}"
                java_event = f"dialogue.{dialogue['dialogue_id']}.{variant['index']:02d}"
                if definition:
                    source_path = rp / definition["bedrock_path"]
                    target_path = voice_root / f"{semantic_name}.ogg"
                    target_path.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(source_path, target_path)
                    java_sounds[java_event] = {
                        "subtitle": f"subtitles.villager_news.{java_event}",
                        "sounds": [{"name": f"villager_news:voice/{semantic_name}", "stream": False}],
                    }
                    java_lang.append(f"subtitles.villager_news.{java_event}={variant['transcript']}")
                variant["resolved_sound_id"] = resolved_id
                variant["recovered_typo"] = original_id == typo_id
                variant["bedrock_audio_path"] = definition["bedrock_path"] if definition else None
                variant["semantic_audio_path"] = f"assets/villager_news/sounds/voice/{semantic_name}.ogg" if definition else None
                variant["java_sound_event"] = f"villager_news:{java_event}" if definition else None
                variant_rows.append(
                    {
                        "dialogue_id": dialogue["dialogue_id"],
                        "title": dialogue.get("title") or "",
                        "category": dialogue.get("category") or "",
                        "section": dialogue.get("section") or "",
                        "variant": variant["index"],
                        "weight": variant["weight"],
                        "duration_seconds": variant["duration_seconds"],
                        "sound_id_original": original_id,
                        "sound_id_resolved": resolved_id,
                        "bedrock_audio_path": variant["bedrock_audio_path"] or "",
                        "semantic_audio_path": variant["semantic_audio_path"] or "",
                        "java_sound_event": variant["java_sound_event"] or "",
                        "animation_id": variant["animation_id"],
                        "transcript": variant["transcript"],
                    }
                )

        # Preserve the 17 short hurt vocalizations selected outside the main
        # dialogue scheduler, plus five shipped-but-unreferenced legacy clips.
        for sound_id, definition in sound_by_id.items():
            if sound_id in used_sound_ids:
                continue
            local_id = sound_id.split(":", 1)[-1]
            animation = alternate_sound_uses.get(sound_id)
            semantic_name = f"hurt/{animation.replace('.', '_')}" if animation else f"legacy_unused/{local_id}"
            source_path = rp / definition["bedrock_path"]
            target_path = voice_root.parent / f"{semantic_name}.ogg"
            target_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_path, target_path)
            java_event = f"hurt.{animation.replace('.', '_')}" if animation else f"legacy_unused.{local_id}"
            java_sounds[java_event] = {
                "sounds": [{"name": f"villager_news:{semantic_name}", "stream": False}],
            }

        java_assets = java_root / "assets" / "villager_news"
        json_dump(java_assets / "sounds.json", java_sounds)
        lang_path = java_assets / "lang" / "en_us.json"
        lang_path.parent.mkdir(parents=True, exist_ok=True)
        lang_path.write_text(json.dumps(dict(line.split("=", 1) for line in java_lang), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        # Verified from the workspace's Minecraft 26.2 DetectedVersion:
        # resource pack format 88.0 (data pack format 107.1).
        json_dump(java_root / "pack.mcmeta", {"pack": {"pack_format": 88, "description": "Villager News dialogue audio for Entity Event Features"}})
        shutil.copy2(rp / "pack_icon.png", java_root / "pack.png")

        trigger_rows = []
        for dialogue_id, guide in sorted(guides.items(), key=lambda item: (categories.get(item[0], ""), sections.get(item[0], ""), item[1]["title"])):
            matching = next((dialogue for dialogue in dialogues if dialogue["dialogue_id"] == dialogue_id), None)
            trigger_rows.append(
                {
                    "dialogue_id": dialogue_id,
                    "title": guide["title"],
                    "short_title": guide["short_title"],
                    "category": categories.get(dialogue_id, ""),
                    "section": sections.get(dialogue_id, ""),
                    "trigger": guide["trigger"],
                    "reaction": guide["reaction"],
                    "variant_count": len(matching["variants"]) if matching else 0,
                    "source_symbol": matching["source_symbol"] if matching else "",
                }
            )

        subscriptions = parse_subscriptions(source)
        entities = entity_catalog(rp, bp, language)
        models = model_catalog(rp)
        items = item_catalog(bp, language)
        animations = animation_catalog(rp)
        animation_controllers = controller_catalog(rp, "animation_controllers", "animation_controllers")
        render_controllers = controller_catalog(rp, "render_controllers", "render_controllers")
        attachables = attachable_catalog(rp, language)
        behavior = behavior_artifacts(bp)
        files = file_manifest(rp, "resource_pack") + file_manifest(bp, "behavior_pack")
        json_dump(output / "catalog" / "dialogue_groups.json", dialogues)
        json_dump(output / "catalog" / "event_subscriptions.json", subscriptions)
        json_dump(output / "catalog" / "entities.json", entities)
        json_dump(output / "catalog" / "models.json", models)
        json_dump(output / "catalog" / "items.json", items)
        json_dump(output / "catalog" / "attachables.json", attachables)
        json_dump(output / "catalog" / "animations.json", animations)
        json_dump(output / "catalog" / "animation_controllers.json", animation_controllers)
        json_dump(output / "catalog" / "render_controllers.json", render_controllers)
        json_dump(output / "catalog" / "behavior_artifacts.json", behavior)
        csv_dump(output / "catalog" / "files.csv", list(files[0]), files)
        json_dump(output / "catalog" / "behavior_pack_inventory.json", resource_inventory(bp))
        json_dump(output / "catalog" / "resource_pack_inventory.json", resource_inventory(rp))
        csv_dump(output / "catalog" / "triggers.csv", list(trigger_rows[0]), trigger_rows)
        csv_dump(output / "catalog" / "dialogue_variants.csv", list(variant_rows[0]), variant_rows)
        csv_dump(output / "catalog" / "sounds.csv", list(sound_rows[0]), sound_rows)

        summary = {
            "source_archive": str(addon),
            "source_sha256": hashlib.sha256(addon.read_bytes()).hexdigest(),
            "dialogue_groups": len(dialogues),
            "dialogue_variants": len(variant_rows),
            "subtitle_cues": sum(len(variant["subtitle_cues"]) for dialogue in dialogues for variant in dialogue["variants"]),
            "named_handbook_triggers": len(guides),
            "bedrock_event_subscriptions": len(subscriptions),
            "bedrock_event_types": dict(sorted(Counter(item["bedrock_event"] for item in subscriptions).items())),
            "sound_definitions": len(sound_definitions),
            "used_dialogue_sounds": len(used_sound_ids),
            "runtime_hurt_sounds_outside_dialogue_scheduler": len(alternate_sound_uses),
            "runtime_used_sounds_total": len(used_sound_ids | set(alternate_sound_uses)),
            "truly_unreferenced_legacy_sounds": sorted(set(sound_definitions) - used_sound_ids - set(alternate_sound_uses)),
            "recovered_broken_reference": {"shipped": typo_id, "resolved": typo_recovery, "reason": "declared 3.58 s; orphan beo.ogg is 3.588771 s"},
            "entities": len(entities),
            "geometry_models": len(models),
            "items": len(items),
            "animations": len(animations),
            "animation_controllers": len(animation_controllers),
            "render_controllers": len(render_controllers),
            "textures": len(list((rp / "textures").rglob("*.png"))) + len(list((rp / "textures").rglob("*.tga"))),
            "attachables": len(attachables),
            "recipes": len(behavior["recipes"]),
            "trade_tables": len(behavior["trades"]),
            "functions": len(behavior["functions"]),
            "archive_files": len(files),
        }
        json_dump(output / "summary.json", summary)

        # Preserve exact source packs for audits and Java-side translators.
        shutil.copytree(rp, output / "source" / "resource_pack", dirs_exist_ok=True)
        shutil.copytree(bp, output / "source" / "behavior_pack", dirs_exist_ok=True)

    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
