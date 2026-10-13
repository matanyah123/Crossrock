> Infrastructure follow-up: [implemented foundation and remaining gaps](FOUNDATION_STATUS.md). Original audit statuses below describe the audited baseline; no VN compatibility claim is implied.

# Crossrock requirements audit: Villager News Bedrock add-on

Audit date: 2026-10-13. **Inventory only; no runtime code changed.**

**The inspected Crossrock tree does not run the complete VN add-on.** Relevant resource translation, entity rendering, animations, attachables and audio facilities exist, but the server behavior/script runtime, custom actor/item registration, VN state production, and their client synchronization are missing. This is a source audit, not a successful Bedrock or Java execution test. No compatibility percentage or end-to-end compatibility claim follows from this report.

## Reading the complete inventory

The report and its linked checklists form one audit:

- [Server checklist](SERVER_CHECKLIST.md): all entity/item components, AI goals, filters, typed properties, component groups, entity events, Script API imports, event endpoints, host-object members, functions, recipes and trades.
- [Client checklist](CLIENT_CHECKLIST.md): all client actors/attachables, Molang queries/math, animation/controller/geometry/material/PBR definitions, schema features and sound definitions.
- [Shared/networking checklist](SHARED_CHECKLIST.md): every pack file with hash; script identifier/endpoint literals; complete member-access backstop, including unresolved helper ownership.
- [Trigger checklist](TRIGGER_CHECKLIST.md): every named handbook trigger and every decoded dialogue variant, retaining shipped sound identifiers separately from dossier-inferred repairs.
- [Dependency closure](DEPENDENCIES.md): scoped definition/asset reference resolution, vanilla dependencies and server command/event edges.
- [Machine-readable evidence](inventory.json): complete definitions, all 289,076 JSON scalar values with JSON pointers, every Script call/argument/member occurrence, constructors, imports, and subscription handler/options. This carries exact nested parameters and dependency strings beyond the readable tables.
- [Counts](counts.json): counts independently recomputed from the actual archive and a JavaScript AST.

Each requirement row identifies its Bedrock spelling/identifier, purpose or occurrence, VN source, Crossrock state/gap, dependencies and priority. Repeated large content definitions use subsystem-level support assessments; this is explicitly **partial/unverified**, not a claim that every definition parses or renders. A checklist entry means “verify this requirement before claiming VN compatibility”; existing code does not automatically check it off.

**C** = confirmed source/declaration/call occurrence. **L** = bundled library/import surface with live VN reachability not established. **I** = implementation infrastructure inferred from confirmed behavior. Static occurrences do not establish that a branch executes, or that every shipped asset is reachable. Unknown obfuscated members and runtime-computed endpoints are retained instead of silently excluded.

P0 = foundations and linkage; P1 = execution, lifecycle and state; P2 = gameplay predicates/AI; P3 = player-visible rendering/audio/UI; P4 = fidelity and remaining verification. Priorities express dependency order, not permission to omit lower-priority shipped behavior.

## Provenance and scope

Actual archive inspected: `/Users/matanyah/Downloads/Villager News 1.0 Add-On by Dazero.mcaddon`, SHA-256 `d21f7a2eb6769d026b536cbe484aa8af479ab9a7ed6272259fe2c259d90883cc`. Both manifests declare add-on version **1.0.4**, manifest format **3**, minimum engine **1.26.10**, world scope. The user-facing “1.0” archive name is not its manifest version.

All 3,042 pack files were hashed against the repository's `villager-news-java/reverse-engineering/catalog/files.csv`. Only the BP and RP manifests differ, in attribution metadata; dependency UUIDs/versions and script entry remain the same. Therefore the readable deminified script and decoded catalogs are evidence for this archive's identical gameplay/resource payload. The dossier's original archive SHA `29e399…dd55e` is a different archive and is not substituted for the inspected archive's hash. Duplicated Finder files named `… 2.*` / `… 3.*` in the extracted repository tree are not counted as original pack entries; the actual ZIP entry inventory is authoritative.

Crossrock was inspected **as present in the working tree**, including pre-existing modified and untracked files. `src/main/java` and `src/client/java` are the active implementation evidence. The separate `eef/`, `villager-news-java/` conversion output, reverse-engineering catalogs and Java resource packs are not automatically Crossrock runtime support. Their repaired identifiers or generated Java assets are not the shipped Bedrock semantics.

| Confirmed archive surface | Count |
| --- | ---: |
| BP / RP files | 32 / 3,010 |
| Behavior / client entities | 9 / 9 |
| Custom item / attachable definitions | 6 / 6 |
| Distinct entity/item component kinds | 99 |
| Distinct server filter tests | 32 |
| Typed property declarations, counted per entity | 47 |
| BP animations / BP controllers | 8 / 27 |
| RP animations / RP animation controllers / render controllers | 2,512 / 54 / 26 |
| Geometry / material / texture-set definitions | 25 / 63 / 253 |
| Sound definitions / OGG files | 2,235 / 2,235 |
| Script subscriptions / distinct before-or-after endpoints | 125 / 36 |
| Handbook triggers / dialogue groups / voiced variants / subtitle cues (decoded dossier, matching script) | 491 / 523 / 2,213 / 3,743 |

The old dossier's 93 subscriptions across 26 event types counts only an earlier after-event inventory. It omits before-events and the AST inventory also captures an additional after-effect subscription. It is not a complete execution endpoint list. Server and client controller totals must likewise be kept separate.

## Crossrock support evidence and its limits

| Code evidence | What exists | What it does not establish |
| --- | --- | --- |
| `src/main/java/…/BedrockCompat.java` | Common initialization registers armor-stand compatibility/networking | A general BP loader, JavaScript engine, custom entity/item registry, event bus or VN server logic |
| `bedrock/resource/BedrockTranslationPipeline.java` | Runs resource/mixed translators and stack translators; generates client resources | Server execution of data modules; recognizing BP content for visuals is not gameplay execution |
| `client/resource/BedrockCapabilityAnalyzer.java` | Explicit diagnostics for unsupported scripts, custom behaviors, functions and gameplay data | Compatibility merely because packs can be selected or resource reload succeeds |
| `bedrock/format/BedrockManifestParser.java`, discovery/storage classes | Manifest/module/dependency parsing, pack discovery and selection/config persistence | Script API version resolution, server lifecycle activation, world-scoped gameplay persistence |
| `NativeEntityResourceTranslator`, `NativeEntityResources`, `NativeEntityRenderer` | Publishes/parses RP entity programs; renderer resolves definitions by registered Java entity identity or edition mapping | Creation of nine `oreville_vn:*` actors or a vanilla-villager-to-proxy gameplay relationship |
| `BedrockClientEntity`, `BedrockEntityInstance`, `BedrockAnimation`, `BedrockAnimationController`, `BedrockRenderController`, `Molang` | Native client definition, state-machine, pose, effects and expression infrastructure | Server animation command execution, script-forced playback networking or correctness for all VN programs |
| `NativeEntityFacts`, `EntityQueryContext`, `EntityFrameQueries`, `EntityEnvironmentQueries`, `NativePlayerInputs` | Many vanilla actor/frame/item/world query adapters | VN custom property values, server AI facts or per-player overrides |
| `client/entity/NativeMolangQueries.java` | Extension hook for externally synchronized immutable query observations | A producer, storage system, protocol or registered VN provider |
| `VanillaSoundTranslator`, `NativeSoundEvents` and renderer effect dispatch | OGG/event translation and native sound-event names | VN dialogue scheduler, stop semantics, exact listener/subtitle timing or authoritative audio dispatch |
| `NativeAttachableRenderer`, item asset translator | Item presentation and worn/held rendering infrastructure | The existence of six custom item stacks in Java registries, their wearable/use/trade behavior |
| `client/ui/json/NativeJsonUi.java` | Bounded RP JSON UI adapter for selected Java screens/HUD | `@minecraft/server-ui.CustomForm`, observable sessions or networked handbook callbacks |
| `entity/ArmorStandNetworking.java` | Armor-stand pose/preference payloads and tracking | General entity properties, dialogue, DDUI or animation messages |

Paths abbreviated above are under `src/main/java/dev/matanyah/bedrockcompat/` or `src/client/java/dev/matanyah/bedrockcompat/`; the checklists provide clickable absolute source links.

## Server

### Pack activation, registries and entity lifecycle

| Check / identifier or API | Purpose and confirmed VN use | Current support / missing functionality | Dependencies / priority |
| --- | --- | --- | --- |
| [ ] C `data`, `script`, entry `scripts/oreville/ebi.js` | Both BP module types must activate in the world | Manifest parsing exists; execution/module linkage absent | Pack pairing, world ownership, JS imports; P0 |
| [ ] C `@minecraft/server` **2.6.0**, `@minecraft/server-ui` **2.1.0** | Exact manifest dependencies; imported host types and UI observables | No host API/module implementation found | Version profile and unsupported-export diagnostics; P0 |
| [ ] C all nine `oreville_vn:*` behavior IDs | Seven named/special actors plus proxy/support roles; exact declarations in Server checklist | No custom Java actor registration or spawn factories | Definition IDs, serializer, client renderer binding, commands, spawn eggs; P0 |
| [ ] C `oreville_vn:villager` + `minecraft:villager_v2` | Script creates a custom proxy, links IDs through `ictvug`/`vlowzc`, mirrors profession/biome/age, names and state | Edition name mapping exists; proxy creation/pairing/state mirror absent | Spawn/load/remove/death, dimension/chunk bounds, persistent IDs and recovery; P1 |
| [ ] C `oreville_vn:vlgzoe` | Helper rideable actor, not an ordinary decorative asset | Registration and mount lifecycle absent | Passenger graph, interaction visibility, cleanup; P1 |
| [ ] C `minecraft:wandering_trader`, sheep, villagers and all observed vanilla entity IDs | Tick commands, transformations, trade/loot inheritance, animal and hostile-mob dialogue predicates | Java vanilla entities exist; Bedrock behavior/event/component identity bridge absent | Type/family and edition identifier mapping; P1/P2 |
| [ ] C `is_spawnable`, `is_summonable`, spawn egg definitions, names | Custom actors must be obtainable and correctly instantiated | RP egg visual facility is separate from actual registered spawning | Registry, creative inventory and spawn cause; P2 |
| [ ] C six custom items | Handbook `kfjmlk`, mayor hat `cryhjc`, microphone `dsojot`, moustache `odplew`, helmet `ufernq`, nose `qzhdgf` | Visual generation exists; registered items and gameplay absent | Inventory/equipment, tags, use duration, cosmetics and recipes/trades; P0/P2 |

Proxy evidence: deminified script lines 34200–34660. Renderer evidence: `NativeEntityRenderer.extract` chooses the actual registry ID, then `BedrockEntityIds.bedrock`; it does not select `oreville_vn:villager` for an ordinary vanilla villager. Simply enabling the RP cannot create VN's visual actors.

### Components, AI, sensors, events and world interaction

The Server checklist enumerates **all 99 component kinds**, **every component group and event** per actor, and **all 32 filter tests**. These require behavioral semantics, not just JSON parsing.

| Check / subsystem | Purpose and VN usage | Current support / missing | Dependencies / priority |
| --- | --- | --- | --- |
| [ ] C Component groups; `add`, `remove`, `sequence`, `randomize`, `filters`, property writes and queued commands | Actor spawn, age, cosmetics, variant/skin updates, scheduled state and despawn transitions | No BP event/group evaluator | Atomic/ordered transitions, weights, filters and deferred action phases; P1 |
| [ ] C `minecraft:type_family`, variant/mark_variant/skin_id, baby/sheared/dye/color flags | Script queries, AI filters and visual state | Some client vanilla facts exist; BP family/component mutation missing | Registration, component lifecycle and replicated values; P1 |
| [ ] C Physics, collision, pushability, navigation, movement, jump, preferred paths, climb/doors | Special actor movement and world collision | Vanilla Java mechanics are available; declared Bedrock parameters not applied | Attributes, collision dimensions, path costs and pathfinding; P2 |
| [ ] C All `minecraft:behavior.*` declarations | Avoid, panic, hide, look, stroll, sleep, trade, share/pickup, float, breed/play/follow, tempt, explore, drink, mount | No BP AI goal translation/execution | Priorities, arbitration, target filters, village POIs, inventory and schedules; P2 |
| [ ] C `environment_sensor`, `entity_sensor`, `target_nearby_sensor`, scheduler/timer | Weather/light/difficulty/health/equipment/family/distance predicates trigger events | No server sensor/filter evaluator | Tick ordering, spatial queries, filter subjects/operators/domains; P1/P2 |
| [ ] C `dweller`, home/restrictions, scheduled village routines, hide/celebrate, resupply | Bed/work/home/village behavior and related dialogue | Java POIs exist; parity/mapping absent | Village membership, day clock, trade state, beds and route restrictions; P2 |
| [ ] C Health, damage_sensor, hurt_on_condition, breathability, fire immunity, death/transformation | Damage/avoidance reactions, immunity, proxy death, custom transforms | No declared BP semantics or cancellable Script bridge | Damage causes, attacker/projectile, effects, exact pre/post phase, proxy cleanup; P1/P2 |
| [ ] C Riding/rideable, leashable/leashable_to, managed wandering trader, balloons | Mount tests, restoration of boat riding, actor interactions and support behavior | Client relations have some facts; BP passenger/leash mechanics absent | Seat capacity/offsets/family filters; lifecycle and persistence; P2 |
| [ ] C Interact, spawn_egg_interaction, wearable, tags, food/use modifiers | Cosmetic transfer, microphone use, handbook opening, custom animal interactions | No item/entity interaction executor | Cancellable before-events, inventory consumption, creative rules, equipment sync; P2 |
| [ ] C Inventory/shareables/equipment, recipes, item catalog, economy_trade_table | Storage, offers, custom cosmetics and handbook crafting | Asset translator does not implement gameplay | Containers, counts/slots, unlocks, rewards/XP, supply/prices/restock; P2 |
| [ ] C Loot, shear/eat events, offspring, breedable, transformations, spawn_entity | Custom sheep and villager/trader lifecycles | No BP executor; referenced vanilla tables must be adapted | Vanilla external tables, RNG, item IDs, interaction/event chains; P2 |
| [ ] C Persistent/despawn/instant_despawn/dimension_bound, visibility/bandwidth options | Keep actor state, remove helpers and control replication/visibility | General VN storage/replication absent | Save/load/chunk unload distinct from death/remove, client tracking; P1 |

### Script API, events and scheduling

There are **125 subscriptions to 36 distinct endpoints**, all listed with exact receiver, phase, source line and every handler/options payload. Before-events include startup/shutdown, entity hurt/remove/item pickup/effect add, player entity/block interactions, item use and leave. After-events include world/player/entity lifecycle, data-driven triggers, script events, damage/death/effects, block break/place/interact, item use/start/complete/pickup/drop, inventory changes, gamemode changes, buttons and projectile hit entity/block.

| Check / API | Purpose and VN usage | Current support / missing | Dependencies / priority |
| --- | --- | --- | --- |
| [ ] C `system.run/runTimeout/runInterval/runJob/clearRun/waitTicks/currentTick` | Dialogue deadlines, polling, generator jobs, delayed inventory/world actions and cleanup | No Script scheduler | Tick phases, Promise continuations, generator fairness, cancellation and errors; P1 |
| [ ] C `system.beforeEvents.startup/shutdown`, `world.afterEvents.worldLoad` | Command registration, deferred initialization and shutdown guards | No script world lifecycle | Per-world module instantiation, teardown/save order; P0/P1 |
| [ ] C `subscribe/unsubscribe` and subscription options | Entity/family/event/namespace filters and removable listeners | No Bedrock signals | Handler identity, valid object lifetime, cancel mutation and authoritative source; P1 |
| [ ] C `world.getDimension/getAllPlayers/getPlayers/getEntity`, `DimensionTypes.getAll` | Multi-dimension/player scans and persistent actor lookup | Client edition adapters do not provide these server APIs | Stable IDs, unloaded actors and dimension IDs; P1 |
| [ ] C Dimension `getEntities/getPlayers` options | Type/family/radius/closest and exclusion predicates throughout VN | No Bedrock query bridge | Spatial index, tag/component/identity rules, distance/order semantics; P1 |
| [ ] C Entity component access and health/item/inventory/equipment/ride/leash/projectile components | Typed component reads/mutations in dialogue predicates | Client facts exist for some equivalents; host objects absent | Optional components, `isValid`, instance identity, container operations; P1/P2 |
| [ ] C Entity position/head/view/velocity/rotation, teleport/lookAt/impulse, damage/kill/remove | Detect movement/world context, stop/focus speaking actors, synchronize proxy movement | No Script entity adapter | Unit/axis conversion, dimensions, physics, authoritative movement; P1/P2 |
| [ ] C Blocks/permutations/volumes/raycast/biomes/chunk loaded/standing blocks | Context triggers: workstations, crops, environment, gaze, blocks underfoot and nearby arrangements | Some client block queries exist; server API absent | Bedrock state/tag/ID mapping, dimension height/chunks, collision/ray options; P2 |
| [ ] C World time/day/gamerules/hardcore and JavaScript `Date` | Time-of-day, day-cycle fallbacks, weekday/calendar/hour/holiday triggers | No server script world/date profile | Separate game ticks/world day from UTC/local calendar methods; P2 |
| [ ] C Host object prototypes, `instanceof Entity/Player/Block`, class fields, optional chaining, Map/Set/WeakSet, generators, Promises, JSON, regex, Math | Bundled helpers extend Entity.prototype with temporary state methods; language runtime is transitive | No JS VM found | Modern ECMAScript or behavior-equivalent explicit Java port; P0 |

The import checklist distinguishes API exports that are merely linked from invoked functionality. For example `StructureSaveMode` and structure rotation imports occur in the bundled utilities; the scanned world receiver surface has no `structureManager` invocation. Do **not** add a mandatory structure save/load subsystem merely from those imports. Generic command utility wrappers for camera shake, fog and setblock are retained as L where no live call path is established.

### Commands, server animation and dialogue

| Check / identifier/API | Purpose and VN usage | Current support / missing | Dependencies / priority |
| --- | --- | --- | --- |
| [ ] C `functions/tick.json` → `oreville/vn/uaofpg` → `k85fz7` → `tnlgui` | Runs `playanimation` on vanilla villagers and wandering traders | No function runtime / Bedrock selector interpreter | Server tick + command context + `animation.oreville_vn.woikqt`; P1 |
| [ ] C `playanimation` and `Entity.playAnimation` | Vanilla suppression/scaling, dialogue lip/bone clips, player microphone animation, hurt variants | Client pose engine exists; server command/API playback route absent | Alias/full identifier resolution, start tick, controller/nextState/stopExpression/blendOutTime; P1/P3 |
| [ ] C BP animations/controllers: `/scriptevent`, `/tag`, `@s event` actions | Server controllers emit script triggers and actor events; 0.04-second looping clips included | Client Molang timeline parser is not a BP command executor | Server actor scope, tick-time semantics, event queue and source identity; P1 |
| [ ] C `system.afterEvents.scriptEventReceive`, namespace filters and source enums | 18 subscriptions connect controller/timeline commands to VN handlers | No server endpoint dispatch | `id`, `message`, `sourceType`, `sourceEntity` validation and ordering; P1 |
| [ ] C `CustomCommandRegistry.registerCommand/registerEnum`, permission/param/status enums | Bundled command router registers `oreville:ftpsbe_set`, `_gbqref`, `_qerisl`, `_override`, `_clear` variants (suffixes appended to `oreville:ftpsbe`); no live custom enum registration was found | No custom command registration bridge | Startup phase, typed arguments/selectors/enums, origin/permissions and results; P1 |
| [ ] C `execute unless entity @s[hasitem=…] run give …` | First-spawn handbook grant, guarded by `tmffhm` property | No Bedrock command/hasitem translation | Custom item registry, per-player persistent flag, initialSpawn; P2 |
| [ ] C `stopsound`, spatial `Dimension.playSound`, `Player.onScreenDisplay.setActionBar` | Start/interrupt voice and subtitle presentation for nearby eligible players | Audio assets exist; script playback/subtitle scheduler absent | Audience, entity location, sound identity and timed cancellation; P3 |
| [ ] C `Dialog` subsystem, 523 groups, 2,213 variants | Weighted lines, rare-line policy, chattiness, cooldowns, concurrency/interruption, listener eligibility and calendar/environment triggers | No Crossrock VN dialogue engine | Every named trigger plus unnamed/internal groups, sound/animation/text/time closure; P2/P3 |

Dialogue implementation must preserve duration and weights, not just play a random OGG. Script lines 1280–1455 schedule subtitle keys at `floor(20*time)`, start sound and animation together, and stop/clear them on interruption. Dialogue conditions include proximity and concurrency gating (e.g. nearby speaker restrictions), not only event notifications. The complete trigger/variant checklist exposes semantic titles, exact trigger text, source symbol, sounds, clips, weights and durations.

### Persistence

| Check / API/state | Purpose and VN usage | Current support / missing | Dependencies / priority |
| --- | --- | --- | --- |
| [ ] C `get/setDynamicProperty`, `getDynamicPropertyIds` on world/actors/players | World settings, per-player preferences/handbook grant, actor links and VN data | Pack configuration JSON exists; gameplay dynamic properties absent | Typed values, removal via undefined, ownership and save/load; P1 |
| [ ] C `__database__…`, numbered database chunks, JSON save/load/delete helpers | Script chunks persistent strings into 30,000-character segments | No compatible property/database backing | Reassembly, stale chunk removal, worldLoad, invalid entity safety; P1 |
| [ ] C Declared bool/int/enum entity properties, defaults/ranges/client_sync | Dialogue/appearance/cosmetics/sign/nose and state flags | Client `query.property` lookup exists; producer/validation absent | Per-type schema, transition visibility and save persistence; P1 |
| [ ] C Entity-prototype temporary stores and in-memory maps | Transient movement/dialogue flags and pairing caches | No JS host-instance semantics | Distinguish transient fields from persistent data; unload/reload cleanup; P1 |
| [ ] C Proxy recovery/removal/age/conversion state transfer | Reconstruct actor links on load; transfer properties/dynamic state/riding during recreation | No VN lifecycle bridge | Stable IDs, shutdown guard, chunk lifecycle, tombstones and orphan cleanup; P1 |

## Client

### Rendering, animation and Molang

| Check / surface | Purpose and confirmed VN usage | Current support / missing | Dependencies / priority |
| --- | --- | --- | --- |
| [ ] C Nine `minecraft:client_entity` definitions | Custom VN actors; each resolves its own aliases and render controllers | Native resource/parser/render paths exist; no matching registered gameplay actors | Server registry + tracked identity/state; P3 |
| [ ] C Six `minecraft:attachable` definitions | Hats/helmet/moustache/nose, handbook and held microphone visuals | Native held/worn infrastructure exists; actual custom equipment unavailable | Item registry, equipment slots/tags, owner context, hand/UI view; P3 |
| [ ] C Geometry/bones/cubes/UV/pivots/locators/bounds | VN body, faces, props and cosmetics | Native geometry pipeline exists; all VN shapes unverified | Mesh transforms, camera culling and attachment bindings; P3 |
| [ ] C Material inheritance/defines/states/samplers/blends | Masked villager overlays, UV animation, emissive/alpha/hidden support objects | Bounded native material implementation | Resolve each inherited capability; unsupported defines/blend factors need explicit handling; P3/P4 |
| [ ] C PNG/TGA textures, atlas aliases, texture arrays and PBR texture sets | Skin/profession/biome/cosmetic variants; RP declares `pbr` | PNG/TGA and texture composition paths exist; VN PBR parity unverified | Channel maps, atlas lookup, alpha/color-space and graphics mode selection; P3/P4 |
| [ ] C Initialize/pre_animation/animate/scale, variables and expressions | State setup, procedural animation, facial/cosmetic selection and motion | Client Molang/instance runtime exists | Ordered evaluation, scopes, numeric/boolean coercion, bone snapshots; P3 |
| [ ] C 2,512 RP clips / 54 controllers / 26 render controllers | Speech facial tracks, locomotion and appearance state machines | Native clip/controller pipeline exists; VN programs unverified | Query values, alias resolution, bone blend/interpolation/time/loop semantics; P3 |
| [ ] C `should_update_bones_and_effects_offscreen` | Authored on eight of nine client actors | `NativeEntityResources` explicitly reports unsupported; effects remain render-driven | Independent ticking/time continuity outside draw visibility; P3 |
| [ ] C Script/command-forced clips and microphone stopExpression/nextState | Spoken variants, hurt reactions and first-person player microphone animation | No matching general forced-animation transport/runtime API found | Per-actor playback layers, server start time, stops, custom item query; P3 |
| [ ] C Every query/math spelling in Client checklist | Expressions access player/actor/view/bone/world/custom state | Mixed implementation; see caveats below | Exact producer values and query ownership; P1 server / P3 client |

Specific confirmed gaps: `query.is_avoiding_mobs`, `query.is_transforming`, `query.facing_target_to_range_attack`, and `query.ride_body_y_rotation` have no literal implementation evidence in the scanned active Java code. This is a targeted unresolved/missing assessment, not proof about an unseen dynamic provider. `query.last_hit_by_player` has a constant-zero implementation in `MolangQueryFunctions`, which is insufficient for a server kill-reward predicate. `query.property/has_property` can evaluate a supplied map, but native facts currently populate selected vanilla properties rather than VN's `p:*` schema. `NativeMolangQueries` offers an integration boundary; it does not supply these values itself.

Other query code does exist: animation/state time and completion; frame/graphics/equipment queries; bone orientation/origin; native position/velocity/movement/baby/sleep/sheared/trade-tier facts; environment block tags. These remain conditional on having a correct VN actor and supplied state. A Java search hit alone is marked only partial/unverified in the per-query checklist. Player-only providers must not be assumed to work for arbitrary proxy actors.

The materials include `USE_ONLY_EMISSIVE` and nonstandard blend factors; `BedrockMaterials` supports a bounded defines/states set and warns for unknown ones. `graphics_mode_is_any` is a rendering selector, not proof of Bedrock PBR rendering. Rendering a fallback image is insufficient evidence for the authored texture-set appearance.

### Audio, subtitles, UI and presentation

| Check / API/resource | Purpose and VN usage | Current support / missing | Dependencies / priority |
| --- | --- | --- | --- |
| [ ] C `sounds/sound_definitions.json`, all OGG paths and `sounds.json` | Dialogue, hurt variants and event routing | Native sound-ID generation, copying/conversion and some contextual mappings exist | Full definition fields, attenuation/category/volume/pitch and listener routing; P3 |
| [ ] C 3,743 subtitle cues and `.lang` keys/rawtext | Timed translated dialogue text and handbook labels | Language translation exists; server timing/audience/actionbar sessions absent | Locale keys, rawtext formatting, interruption/clear and subtitle settings; P3 |
| [ ] C `CustomForm`, `ObservableBoolean/Number/String/UIRawMessage`, `DataDrivenScreenClosedReason` | Handbook pages/settings and reactive updates | No Script DDUI host binding or client bridge | Session identity, callback IDs, mutable values, subscriptions and close reasons; P3 |
| [ ] C DDUI button/header/label/divider/spacer/dropdown/toggle/slider/textField; visible/enabled/options | Book navigation, settings, text and bound input behavior | RP JSON UI adapter is a different surface | Input validation, focus/layout/scroll, callbacks and observable lifetime; P3 |
| [ ] C Form `.show/.close`, Promise completion and UserBusy/ServerClosed handling | Reopens/replaces screens and disposes bindings | No DDUI session lifecycle | Per-player connection, server lifecycle and client acknowledgements; P3 |
| [ ] C Handbook settings and player `setPropertyOverrideForEntity` | Global chattiness/rare lines, player-specific preferences/appearance | No per-viewer VN property transport/provider | Persistent player settings, tracked actor snapshots, reset on leave/reload; P1/P3 |

VN ships **no `ui/` JSON directory** and no particle definition directory. Its handbook is scripted DDUI, not a requirement to implement every Bedrock JSON UI screen. Its resource definitions are preserved in full; any effect/particle/vanilla asset names encountered must still resolve through the dependency closure. Calendar and UI preferences appearing in script source are not all active features: `vyzzln` is an empty function in this shipped script, so the associated preference is not evidence of implemented behavior requiring a new Crossrock visual subsystem.

Microsoft's current documentation confirms that [CustomForm is a DDUI form with observable bindings](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server-ui/customform?view=minecraft-bedrock-experimental). [Player property overrides](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server/player?view=minecraft-bedrock-stable) are a distinct per-player API. These references clarify the surfaces; their current documentation view does not prove that this archive runs on a particular Bedrock build or that Crossrock implements its declared API versions.

## Shared / Networking

VN uses engine-local APIs and commands; no HTTP/REST service, socket client, remote URL literal or external backend was found in the script. Here “endpoint” principally means a Bedrock event/API/command/UI integration point, not an invented web service. Fabric packet names below are **I** requirements; VN does not ship a Java network protocol.

| Check / identifier or integration | Purpose and VN use | Current support / missing | Dependencies / priority |
| --- | --- | --- | --- |
| [ ] C BP UUID `fa0c2c98-dd46-4822-b87d-fa5bacdecb5c` ↔ RP UUID `1c9c8504-4985-44c8-a019-ad77dd893338`, both 1.0.4 | Mutual pack dependencies | Metadata/selection diagnostics exist; compatible world activation profile absent | Version checks without treating cyclic pair as recursive execution; P0 |
| [ ] I Pack/profile negotiation and server resource availability | Client assets must match server actor/item/animation IDs | No general VN handshake/required pack transport | World enablement, server ownership, pack hash/version and registry agreement; P0/P1 |
| [ ] C/I Actor identity, custom spawn and tracking | Render proxy/special actors and resolve persistent links | Java networking available; no VN/custom actor registration/tracking schema | Registry IDs, numeric network IDs plus persistent identities, start/stop tracking; P1 |
| [ ] C/I Typed entity properties / variant / skin / cosmetics / sign/nose | Drive RP `query.property`, mark_variant and skin arrays | Client value hook exists; authoritative replication absent | Full initial snapshot + ordered deltas + removal/defaults; P1 |
| [ ] C/I `Player.setPropertyOverrideForEntity` | `p:pmpece` per-player appearance preference | No per-viewer property overlay | Base state + viewer override, join/spawn/load/retracking/dimension transitions; P1/P3 |
| [ ] C/I Dialogue playback, forced animation and stops | Keep actor mouth pose, spatial sound and subtitles aligned | Assets/client animation infrastructure exist; server presentation dispatch absent | Start tick/time, variant/clip/sound ID, interruption and listener selection; P3 |
| [ ] C/I DDUI open/update/input/close and observables | Handbook with server-owned callbacks and reactive preferences | No DDUI protocol | Session/observable IDs, clientWritable enforcement, permission/ownership and stale input handling; P3 |
| [ ] C/I Item/equipment/passenger/name/motion/health replication | Cosmetics, proxy following and mount state | Vanilla transport only for existing Java objects; VN integration absent | Custom registry and actor ownership; P1/P2 |
| [ ] I Lifecycle recovery | Prevent stale audio/UI/proxy state after reconnect, tracking changes, reload or removal | Armor-stand-specific transport exists; no general VN lifecycle | Snapshot recovery, teardown, idempotent cleanup and per-world state; P1/P3 |
| [ ] I Integrated and dedicated server ownership | Both must run authoritative gameplay rather than draw-time callbacks | Common initializer has no BP execution | Keep server logic independent of client resource reload/render visibility; P0/P1 |

Do not synchronize every dynamic property indiscriminately. The shipped `client_sync` declarations, explicit presentation events and per-viewer overrides define client needs; other database/transient state remains server-owned. This is a proposed boundary inferred from the confirmed APIs, not an existing Crossrock protocol.

## Transitive closure and source defects

Confirmed dependency paths that must remain connected:

1. World load → module imports → scheduler/event registration → first player spawn → persistent flag → command selector → custom handbook item → `itemUse` → DDUI → observable updates → world/player persistence → per-player actor property override → RP query/render selection.
2. Vanilla villager spawn/load → persistent proxy ID link → custom actor spawn → property/age/profession/biome copying → RP actor identity → clip/render aliases → geometry/material/texture → tracking and removal/death recovery.
3. BP scheduler/sensor/AI or animation-controller transition → `@s event` / `/scriptevent` / tag command → script handler → dialogue group/weighted variant → audience/timing → spatial sound + forced clip + translated subtitle cues → stop/clear and cooldown bookkeeping.
4. Cosmetic item acquisition through recipes/trades → equipment/interaction/cancellation/inventory changes → actor properties/equipment → entity overlay or attachable → first-person/world/UI view and persisted ownership.
5. Sheep/trader/villager custom behavior → external vanilla loot/trade table/type/POI/interaction semantics → Script event payload → trigger predicate. Vanilla Java presence does not close Bedrock identity/state differences.

[Dependency closure](DEPENDENCIES.md) records concrete scoped references and server command payloads. Twelve proxy sign-texture aliases resolve outside the shipped pack (`textures/entity/sign`, wood-specific signs including darkoak/mangrove/cherry/pale_oak/bamboo/crimson/warped); Crossrock must supply verified edition-path mappings or report the gap. The scoped resolver found no missing direct animation/geometry/render-controller definition references, which is only an asset-link result, not runtime compatibility. External vanilla data includes `loot_tables/entities/sheep.json`, `sheep_shear.json`, `sheep_sheared.json` and `trading/economy_trades/wandering_trader_trades.json`; these are not extra files shipped inside VN. Filter/tag/type names and vanilla block/item literals are retained in the shared checklist and exact JSON-pointer data.

The dossier records a shipped sound identifier `orevillele_vn:uookqp.17` and infers a replacement `oreville_vn:lekufv` by orphan-clip duration. The audit retains **original versus resolved** fields and does not credit Crossrock with that repair. Five legacy sound definitions are dossier-classified unreferenced; they are shipped assets, not confirmed live requirements. Runtime-selected identifiers, unused helper imports and branches whose reachability depends on world state remain explicitly unresolved. The full leaf/call index is an exhaustive static backstop, not a formal proof of runtime reachability or behavioral equivalence.

## Recommended implementation order and acceptance checklist

No implementation is performed by this audit. Either a compatible general BP/Script host or an explicit VN behavior port could satisfy the contract; accepting Java gameplay equivalents requires tests against the shipped conditions, payloads and timing rather than matching names.

1. [ ] **P0: lock the profile and ownership.** Resolve paired UUID/version manifests and imports; decide general host versus explicit port; activate per world on integrated/dedicated servers. Register nine actors and six items with stable persistence/network identities and client definition binding. Fail clearly on unsupported API requirements.
2. [ ] **P1: state and lifecycle.** Implement component/property storage, typed schemas/defaults, dynamic property/database backing, actor validity/lookup, spawn/load/unload/remove/death/shutdown semantics, proxy pairing/recovery and initial/delta tracking. Preserve transient versus persistent state and viewer overrides.
3. [ ] **P1: execution graph.** Implement tick/job/Promise scheduling, all 36 event endpoints with exact cancellation/payload/filter rules, entity event/group/filter evaluator, server Molang context/controllers, function tick chain, selector/command routing and script-event source identity. Verify controller-to-script chains before adding dialogue content.
4. [ ] **P2: gameplay and queries.** Close declared AI/navigation/sensors/village schedules, trades/recipes/inventories/items/equipment/mount/shear/breed/loot/transform semantics; bridge all actual world/block/player/component reads. Preserve calendar versus game-time conditions and vanilla identifier differences.
5. [ ] **P2/P3: dialogue engine.** Cover all 491 named triggers and unnamed/internal groups; validate weighted variants, rare/chattiness policies, proximity, cooldowns, concurrency, interruption, persistent flags and cue scheduling. Keep shipped defects visible pending an explicit repair decision.
6. [ ] **P3: presentation transport.** Bind actor properties and per-viewer overrides to queries, implement forced playback/stops/start time, spatial sound/audience and translated subtitle lifecycle; close first-person microphone and cosmetic attachable ownership.
7. [ ] **P3: handbook DDUI.** Implement CustomForm/observables, controls, callbacks, show/close reasons and session teardown; connect settings to persisted server state and viewer properties. Test two clients with different preferences.
8. [ ] **P3/P4: visual and audio fidelity.** Resolve every scoped definition/reference, fix unsupported query values/material semantics/offscreen animation; compare all relevant poses/props/professions/biomes/skins/noses/cosmetics/views, sound categories/attenuation and PBR/fallback appearance to Bedrock observations.
9. [ ] **Acceptance:** test real VN on isolated worlds with the matching Bedrock API profile and Java integrated/dedicated servers. Cover joining/rejoining, tracking range, dimension travel, chunk unload/reload, server restart, death/conversion/recreation, equipment/trade/crafting, cancel paths, concurrent speakers, missing references and resource reload. Check every linked inventory row before a full compatibility claim.

## Validation performed

Read-only archive extraction to `/tmp`; SHA-256 comparison of all pack entries to the dossier; independent JSON traversal; full JavaScript AST parse of the formatting-only script; import/member/call/constructor and before/after subscription extraction; active Java source inspection and targeted implementation searches; normalized associative JavaScript AST comparison against the actual minified entry script; checklist/data count and link consistency checks. No game execution, build, runtime tests or visual/audio comparison was performed, because this task requests inventory. No runtime code, generated game assets or existing working-tree changes were modified.
