# Entity Event Features (EEF)

EEF is a generic, resource-pack-driven event runtime for Fabric. It captures Minecraft
events, exposes contextual data, evaluates declarative rules, selects an outcome, and
schedules client or server actions. EEF contains no pack-specific identifiers or logic.

Rules are JSON files below `assets/<namespace>/eef/`. Schema 2 has this shape:

```json
{
  "id": "example",
  "schema": 2,
  "rules": [{
    "id": "hurt_speech",
    "event": "entity_damaged",
    "reliability": ["CLIENT_INFERRED", "SERVER_REQUIRED"],
    "conditions": [
      {"path": "self.type", "operator": "eq", "value": "minecraft:villager"},
      {"path": "distance.observer_to_self", "operator": "lte", "value": 24}
    ],
    "chance": 0.75,
    "cooldown": [{"scope": "rule_entity", "ticks": 40}],
    "avoid_immediate_repeat": true,
    "outcomes": [{
      "id": "line_1",
      "weight": 3,
      "actions": [
        {"type": "play_sound", "sound": "example:dialogue.line_1", "at": "self", "radius": 24},
        {"type": "subtitle", "translation_key": "subtitles.example.line_1", "delay_ticks": 2}
      ]
    }]
  }]
}
```

Rules may declaratively replace `self` or `target` with an entity near an event role or
block position before conditions run. The platform resolver remains generic:

```json
"subjects": {
  "self": {
    "query": "nearby_entities",
    "origin": "event.position",
    "radius": 16,
    "preserve_as": "target",
    "exclude": ["target"],
    "line_of_sight": true,
    "retrigger": "after_exit",
    "exit_grace_ticks": 100,
    "conditions": [{"path": "type", "operator": "eq", "value": "minecraft:pig"}],
    "selection": "nearest",
    "limit": 1
  }
}
```

Supported origins are `event.position`, `self`, `target`, `actor`, and `observer`.
Selections are `nearest`, `random`, and `all`; `limit` bounds random/all fan-out. Query
conditions are relative to the bound role, while ordinary rule conditions can still
inspect the original `actor`, block, item, world, and other event context. The resolver
adds `query.distance`. Binding `self` makes positional actions, entity cooldowns, state,
and sequences belong to the selected entity.

For a chorus or broadcast reaction, use `"selection": "all"`. All-selection defaults to
a 256-entity safety bound when `limit` is omitted. Add `"synchronize": true` to treat the
fan-out as one occurrence: chance and frequency are evaluated once, rule/global cooldowns
do not suppress later members of the same batch, and every selected speaker's sound bundle
waits for the last occupied speaker channel before all bundles begin on the same tick.

`preserve_as` retains the entity previously occupying the bound role as `target`, `actor`,
or `subject`. `exclude` prevents any listed participant from being selected as its own
witness. `line_of_sight` requires an unobstructed view from candidate to observed entity.
The rebound context exposes `relationships.<from>_to_<to>` with `distance`, `line_of_sight`,
`same_entity`, ownership, attack-target, riding, and shared-vehicle predicates. Entity
contexts also expose living/mob classification, scoreboard tags, vehicle/passenger identities
and passenger count, tame owner, and current mob attack target when observable by the client.

Subject queries default to `retrigger: "cooldown"`. With `retrigger: "after_exit"`, the
first full match activates an encounter latch and further observation pulses are ignored,
regardless of cooldown expiry. The latch rearms only after the query produces no full match;
`exit_grace_ticks` also clears stale latches when the observed entity unloads. Candidate
changes do not retrigger while at least one eligible candidate remains nearby.

The runtime indexes rules by event kind, caches pack-derived proximity thresholds, and
deduplicates identical subject queries within one event dispatch. Conditions on a preserved
participant are evaluated before querying the world, and candidate predicates run before
line-of-sight checks. Entity contexts are cached for the current tick. State snapshots are
limited to entity registry types referenced by state-change rules unless a rule requires an
unrestricted type set.

## Context and predicates

The event envelope exposes `event.kind`, `event.tick`, `event.reliability`, and
`event.detail`. Captures add nested `self`, `target`, `observer`, `world`, `distance`,
`damage`, and `movement` objects when that information exists. Entity objects include
an id, registry type, position, health/state fields, and optional custom name.

When EEF is present on the logical server, `entity_damaged` and `entity_died` arrive as
`SERVER_REQUIRED` events with authoritative `attacker`, `direct_entity`, and `damage`
objects. Damage fields include `amount`, `base_amount`, `blocked`, registry `type`, vanilla
`message_id`, `direct`, `authoritative`, optional `weapon_type`, and `server_tick`. The
victim remains `self`. The client briefly holds its health-delta inference so the server
event replaces it without duplicate rules; if the server has no EEF bridge, the inferred
event is released after two ticks and client-only packs continue to work.

`entity_state_changed` is emitted from deterministic snapshot diffs. `change.path`
identifies the changed leaf, while `change.before`, `change.after`, `before_exists`, and
`after_exists` describe the transition. Complete snapshots are available as
`snapshot.before` and `snapshot.after`, and `self` contains the current value. Observed state
includes pose, sleeping, age, sprinting, effects and effect count, equipment and armor
count/types/materials, held-item durability, health ratio, movement, ground/gliding state, profession/level, tags,
vehicle and passenger count, living/mob classification, tame/owner state, shearing, bee
anger, and current mob target.

Server activity snapshots also expose villager inventory deltas as an authoritative
`entity_state_changed` event with `change.path = "inventory"`. `inventory_change.added`,
`added_types`, `removed`, and `removed_types` remain generic item/count data. Block contexts
include `replaceable`, allowing packs to distinguish solid block destruction from clearing
fire, short grass, and other replaceable blocks.

Condition operators are `exists`, `not_exists`, `eq`, `ne`, `gt`, `gte`, `lt`, `lte`,
`in`, `not_in`, `contains`, and `matches`. Numeric distance/position tests use the normal
comparison operators. Tags and other collections use `contains`.

Cooldown scopes are `global`, `rule`, `entity`, and `rule_entity`. Chance is in `[0,1]`;
positive outcome weights are relative. `avoid_immediate_repeat` remembers the last
outcome for each rule/entity pair.

Recurring events can use `activation` to turn predicates into transitions. `mode` is
`match` (default), `entered`, or `exited`. Entered fires once when all conditions become
true; exited fires once when a qualified match becomes false. `hold_ticks` requires a
continuous match before qualification. An optional `key` context path such as `target.id`
keeps activation state attached to the observed actor after subject rebinding.
`reset_after_ticks` defaults to 100 so lost observations cannot leave stale state forever.

Every captured event includes local `calendar` fields (`year`, `month`, `day`,
`day_of_week`, `hour`, `minute`, `weekend`, and `top_of_hour`). Entity context includes
`environment` with dimension, Y position, biome, base temperature, water/submersion,
sky visibility, rain at the entity position, block IDs at its feet and underneath, and a
normalized collision `surface_block` (so thin snow and full blocks use the same predicate).
`time_changed` additionally exposes `time.before`, `after`, absolute and cycle deltas,
`skipped`, `crossed_to_day`, and `crossed_to_night`.

## Actions and time

Implemented client resource-pack actions are `play_sound`, `subtitle`, and `actionbar`.
Every action accepts `delay_ticks`. `play_sound` accepts `sound`, `at` (any entity role bound
into the event context, including `self`, `target`, `actor`, or `observer`), `volume`, `pitch`,
`radius`, and `follow_entity`. Entity following is
enabled by default: the sound position updates every tick while its speaker moves. Set
`follow_entity` to `false` for a fixed snapshot of the selected entity's position. A sound can also declare `channel`,
`channel_scope` (`global`, `entity`, `rule`, `rule_entity`, or `conversation`), `duration_ticks`, and optional
`gap_ticks`. If that channel is occupied, EEF shifts the complete selected outcome—including
all relative subtitle cues—until the previous sound ends. Set `channel_mode` to `overlap` for
pack-authored chorus/interrupt events that must start immediately without occupying the normal
speaker queue. Subtitle/actionbar actions accept literal
`text` or a `translation_key`.

The `conversation` scope serializes speakers that share the same context key while leaving
unrelated entities independent. It reads `conversation.id` by default; `channel_key_path` can
select another context path. If that value is absent, it safely falls back to the speaking entity.

When enabled in Mod Menu, every subtitle cue also renders a projected speech bubble above its
speaking entity. The bubble uses the supplied 20×20 nine-slice and 9×9 tail sprites, wraps text
to the current client font, follows the entity in screen space, and expires with the cue's
declared duration.

The runtime also handles `set_state`, `clear_state`, `sequence`, and `cancel_sequence`.
A sequence has a `key`, `mode` (`replace` or `ignore`), and timed `steps`; each step has
`delay_ticks` and nested `actions`. Scheduled work, cooldowns, outcome memory, and state
are cleared atomically on resource reload. Transient cooldown, encounter, sequence, audio,
and state data is also reset automatically when entering a different world; loaded rules
remain available without requiring F3+T.

For `entity_damaged`, EEF automatically suppresses the nearby vanilla Minecraft hurt
sound when a matching, guaranteed rule (`chance: 1`) supplies `play_sound` in every
possible outcome. The replacement decision uses the same entity predicates as the rule;
it does not contain pack-, entity-, or dialogue-specific identifiers.

EEF subtitle cues use a dedicated bottom-center HUD layer and do not depend on the
vanilla title timer. When Mod Menu is installed, EEF exposes persistent subtitle and
speech-bubble controls, off-screen bubble range, per-trigger frequency/priority, and
add-on-style `Villager Chattiness` and `Rare Villager Lines` controls in
`config/entity_event_features.properties`.

Unknown events and condition operators invalidate their resource file. Unknown actions
are preserved for extension handlers and safely ignored by the built-in client handler.

## Developer command UI

EEF exposes resource-pack-driven manual playback through normal Minecraft entity selectors:

```text
/eef play <trigger> [variant]
/eef <targets> play <trigger> [variant]
/eef list [page]
/eef info <trigger>
```

The shorter `/eef play` form targets the entity currently under the player's crosshair.
For example, `/eef @n[type=minecraft:villager] play hear_a_bell_ring 2` plays the
second outcome of the uniquely matching trigger on the nearest villager. Full rule IDs
also work. Spaces and punctuation in a search are normalized, so a quoted trigger such as
`"hear a bell ring"` is equivalent. If `variant` is omitted, normal pack weights and
immediate-repeat avoidance select the outcome. Manual playback deliberately bypasses the
rule's natural event predicates, chance, frequency, and cooldown, but still uses its normal
positional sound, subtitle, speech-bubble, sequence, and audio-channel behavior.

`/eef list` pages through every rule in the active client resource packs. `/eef info`
shows the event and the one-based outcome numbers accepted by `play`. Partial names must
resolve to exactly one rule; ambiguous searches return matching full IDs instead of
silently choosing the wrong trigger.

After a client resource reload, EEF synchronizes its generic rule IDs and outcome counts
to the connected server. Brigadier then provides Tab completion for trigger IDs and only
the valid one-based variant numbers. No pack behavior, conditions, dialogue text, or sound
assets are transferred; execution still uses the invoking client's active resource packs.

## Current capture boundary

| Event | Reliability | Notes |
|---|---|---|
| `world_loaded`, `player_joined` | `CLIENT_OBSERVED` | Client world transition |
| `entity_loaded`, `entity_removed` | observed/inferred | Removal has no retained entity handle |
| `entity_observed` | `CLIENT_OBSERVED` | One pack-filterable observation pulse per second for visible entities, including the local player |
| `entity_state_changed` | observed/server-required | Synced entity snapshot leaf changes plus authoritative villager inventory deltas |
| `entity_damaged`, `entity_died` | `SERVER_REQUIRED` with client fallback | Server carries source/attacker/direct entity/weapon; client health observation is deduplicated or released after two ticks |
| player movement, flight, sneak, swim | `CLIENT_OBSERVED` | Jump is inferred |
| `riding_started`, `riding_stopped` | `CLIENT_OBSERVED` | Local player only |
| `time_changed`, `weather_changed` | `CLIENT_OBSERVED` | Clock deltas, skip/day/night crossing semantics, and weather transitions |
| `player_game_mode_changed`, `world_difficulty_changed` | `CLIENT_OBSERVED` | Before/after transition context and current difficulty |
| `entity_entered_range`, `entity_exited_range` | `CLIENT_OBSERVED` | Thresholds are derived from enabled rules |
| `entity_interacted`, block/item input events | `CLIENT_OBSERVED` | Actor, target, hand, item, and block context |
| `block_broken`, `block_placed` | `CLIENT_INFERRED` | Short-lived client block-state watches |

Transactions, authoritative block mutation, and multiplayer witness attribution remain
`SERVER_REQUIRED` gaps. Server-authoritative living-entity damage/death is implemented.
This remains a per-event boundary; resource packs and client-observed rules still work
without a server mod.

## Developer test pack

`dev-test-resource-pack/` is a standalone `eef_test` pack covering all 31 client event kinds,
the authoritative damage event, nearby-subject binding, and the major schema-2 runtime features. Its
`MANUAL-TEST-CHECKLIST.md` maps each in-game action to expected HUD feedback. It has no
external asset or content-pack dependency.
