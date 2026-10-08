#!/usr/bin/env python3
"""Convert Villager News into a separate Java resource-pack project.

The source .mcaddon is never modified. Bedrock assets are retained under
assets/villager_news/bedrock for translators, while sounds and EEF rules are
usable by Java-side loaders immediately.
"""
from pathlib import Path
import json, re, shutil, sys, tempfile, zipfile

SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home()/"Downloads/Villager News 1.0 Add-On RP.mcaddon"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("villager-news-java")
NS = "villager_news"

if not SRC.is_file(): raise SystemExit(f"Missing addon: {SRC}")
if OUT.exists(): shutil.rmtree(OUT)
resources = OUT/"src/main/resources"
assets = resources/"assets"/NS
assets.mkdir(parents=True)

with tempfile.TemporaryDirectory(prefix="vn-convert-") as temp:
    with zipfile.ZipFile(SRC) as z: z.extractall(temp)
    root = Path(temp)
    dirs = [p for p in root.rglob("manifest.json")]
    rp = next((p.parent for p in dirs if json.loads(p.read_text(errors="ignore")).get("modules", [{}])[0].get("type") == "resources"), None)
    bp = next((p.parent for p in dirs if json.loads(p.read_text(errors="ignore")).get("modules", [{}])[0].get("type") == "data"), None)
    if rp is None: rp = root
    if bp is None: bp = root

    # Preserve all original resources for format-specific translators.
    for source, name in ((rp, "bedrock/rp"), (bp, "bedrock/bp")):
        target = assets/name
        shutil.copytree(source, target, dirs_exist_ok=True)

    # Java sound resources and a vanilla sounds.json index.
    sounds = {}
    for ogg in rp.rglob("*.ogg"):
        rel = ogg.relative_to(rp).with_suffix("")
        if "sounds" in rel.parts:
            idx = rel.parts.index("sounds")
            sound_parts = rel.parts[idx+1:]
            # Bedrock commonly stores namespace folders as sounds/<namespace>/<group>/<id>.
            # Java needs one namespace, so fold the first two path components together.
            namespace = "_".join(sound_parts[:2]) if len(sound_parts) > 1 else sound_parts[0]
            sound_path = sound_parts[2:] if len(sound_parts) > 2 else (sound_parts[1],)
            sound_id = namespace + "/" + "/".join(sound_path)
            target = assets/"sounds"/namespace/Path(*sound_path)
            target = target.with_suffix(".ogg"); target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ogg, target)
            sounds.setdefault(sound_id, {"sounds": [{"name": f"{NS}:{'/'.join(rel.parts[idx+1:])}", "stream": False}]})
    (assets/"sounds.json").write_text(json.dumps(sounds, indent=2, sort_keys=True)+"\n")

    script = "\n".join(p.read_text(errors="ignore") for p in bp.rglob("*.js"))
    event_names = sorted(set(re.findall(r"afterEvents\.([A-Za-z0-9_]+)", script)))
    script_ids = sorted(set(re.findall(r"oreville_vn_trigger:[A-Za-z0-9_]+", script)))
    kind_map = {"entitySpawn":"ENTITY_SPAWNED", "entityDie":"ENTITY_DIED", "entityHurt":"ENTITY_HURT",
                "entityLoad":"ENTITY_LOADED", "entityRemove":"ENTITY_REMOVED", "playerJoin":"PLAYER_JOINED",
                "playerLeave":"PLAYER_LEFT", "playerSpawn":"PLAYER_SPAWNED", "playerBreakBlock":"PLAYER_BREAKING_BLOCK",
                "playerPlaceBlock":"BLOCK_PLACED", "playerInteractWithBlock":"PLAYER_INTERACTED_WITH_BLOCK",
                "playerInteractWithEntity":"ENTITY_INTERACTED", "projectileHitBlock":"PROJECTILE_HIT_BLOCK",
                "projectileHitEntity":"PROJECTILE_HIT_ENTITY", "itemStartUse":"ITEM_USE_STARTED",
                "itemCompleteUse":"ITEM_USE_COMPLETED", "itemStartUseOn":"ITEM_USE_ON_BLOCK_STARTED",
                "effectAdd":"EFFECT_ADDED", "playerGameModeChange":"PLAYER_GAME_MODE_CHANGED",
                "buttonPush":"BUTTON_PUSHED", "scriptEventReceive":"SCRIPT_EVENT_RECEIVED", "worldLoad":"WORLD_LOADED"}
    rules = []
    for name in event_names:
        if name in kind_map:
            actions = [{"type":"bedrock_event", "event":name}]
            dialogue = {"entitySpawn":"Villager News: A villager has arrived!", "entityHurt":"Villager News: Someone attacked a villager!",
                        "entityDie":"Villager News: Breaking news from the village!", "playerJoin":"Villager News: Welcome to the village!",
                        "playerInteractWithEntity":"Villager News: The reporter is talking to you!"}.get(name)
            if dialogue: actions.append({"type":"actionbar", "text":dialogue})
            # Attach a real converted VN voice clip to each reaction. The original
            # script selects these from obfuscated dialog tables; retaining the
            # sound IDs keeps the reaction data-driven and Java-safe.
            if sounds:
                voice_ids = sorted(k for k in sounds if k.startswith("oreville_vn/"))
                if voice_ids:
                    actions.append({"type":"play_sound", "sound":NS + ":" + voice_ids[len(rules) % len(voice_ids)], "volume":1.0, "pitch":1.0})
            rules.append({"id": f"vn_event_{name}", "event": kind_map[name], "actions": actions})
    for sid in script_ids:
        rules.append({"id": "vn_script_"+sid.rsplit(":",1)[-1], "event":"SCRIPT_EVENT_RECEIVED", "detail":sid,
                      "actions":[{"type":"bedrock_script_event", "script_event":sid}]})
    eef = assets/"eef"; eef.mkdir()
    (eef/"villager_news.json").write_text(json.dumps({"id":"villager_news", "schema":1, "rules":rules}, indent=2)+"\n")
    report = {"source": str(SRC), "namespace": NS, "events_detected": event_names,
              "script_events_detected": script_ids, "mapped_events": sorted(kind_map.get(x) for x in event_names if x in kind_map),
              "unsupported_events": sorted(x for x in event_names if x not in kind_map),
              "notes":["Bedrock JavaScript is represented as EEF declarative events; arbitrary script logic remains unsupported and is reported."]}
    (OUT/"conversion-report.json").write_text(json.dumps(report, indent=2)+"\n")

(resources/"pack.mcmeta").write_text(json.dumps({"pack":{"pack_format":64,"description":"Villager News (EEF converted resources)"}}, indent=2)+"\n")
icon = next((p for p in (assets/"bedrock/rp").rglob("pack_icon.png")), None)
if icon: shutil.copy2(icon, resources/"pack.png")
(OUT/"README.md").write_text("# Villager News — Java conversion\n\nGenerated by `tools/convert_villager_news.py`. Original addon is unchanged. See `conversion-report.json`; EEF rules are in `src/main/resources/assets/villager_news/eef/villager_news.json`.\n")
print(f"Generated {OUT} ({len(rules)} EEF rules, {len(sounds)} sounds)")
