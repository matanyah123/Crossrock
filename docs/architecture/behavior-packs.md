# Behavior Pack foundation

Implemented on `feature/behavior-packs`, 2026-10-13. This is an integrated infrastructure layer, not implementation of Bedrock gameplay or Script APIs. No Villager News identifiers or special cases are present in the implementation.

## World ownership and discovery

`BedrockPackRepository` remains the single manifest/icon/language/archive discovery implementation. `PackCatalog` adds UUID identity, exact dependency versions, duplicate detection and transitive closure, including legal mutual BP/RP references. Both unpacked packs and `.mcpack`/`.mcaddon` archives, including nested `.mcpack` entries, use the existing content reader.

Installation-wide sources live in `<game>/addons`. A world can provide overrides in `<world>/datapacks/addons`. An override replaces the same UUID from the installation catalog; duplicate UUIDs within one catalog root are rejected. Invalid individual archive discoveries produce diagnostics. An active dependency that is missing or has a mismatched version fails preparation. Unknown Script API module dependencies remain recorded/deferred, not falsely resolved.

Each BP appears as a separate real Java datapack, `crossrock/bp/<uuid>`, with its own title/icon/description. Its nonautomatic `PackSource` is a stable shared object: vanilla pack identity includes that source, so constructing a new source on every scan would silently lose pending selections. Packs are disabled by default. World activation and ordering use Java's native `DataPacks.Enabled/Disabled` state in `level.dat`, not the installation's RP options.

Java datapack selection has separate **Java Datapacks** and **Bedrock Behavior Packs** tabs sharing one pending `PackSelectionModel`. Switching tabs does not commit. The BP tab supports icons, dependency activation, dependency checks when disabling, ordering and atomic import into the world's datapack source directory. Create World uses its normal datapack validation/reload flow. Edit World exposes **Datapacks & Behavior Packs**; it updates only the native datapack configuration while holding the world's storage lock.

Legacy standalone RP selection and its aggregate compatibility-pack ID remain available for existing installations. Server-required RPs are individual world/connection packs; this foundation does not migrate or discard existing user RP choices.

## Reload transaction and definition graph

`BehaviorPackSource` opens immutable content snapshots at datapack reload time. `BehaviorReload` participates in the Java server-data resource transaction. `BehaviorProfile` validates the selected dependency closure, constructs ordered registries, prepares registered subsystem plans and creates a deterministic transfer bundle. Failed preparation leaves the previously active runtime/profile generation intact.

`BehaviorDefinitions` preserves all raw artifacts, including scripts/functions, and indexes named entities/items/blocks and other named `minecraft:*` definitions, animation programs/controllers and arbitrary JSON documents. Bodies are copied on exposure. Later enabled packs override earlier definitions by kind and identifier. Unknown component fields are retained rather than discarded or interpreted as working gameplay.

`BehaviorSubsystem` supplies side-effect-free preparation and per-server sessions with open/tick/close lifecycle. Future complete API subsystems can consume the same definition graph, persistence and world owner. None of the Script, AI, component-event, command-function, recipe or trade engines is installed by this change. Module presence and successful infrastructure activation do not assert those semantics.

`BehaviorRuntime` is owned by a `MinecraftServer`, on its authoritative thread. It publishes a new session UUID and monotonically increasing reload revision after successful reload, reconciles host state, replaces subsystem sessions, negotiates clients, saves state and releases references at shutdown. Integrated and dedicated servers use the same common implementation. Networking state is also scoped by server instance, not global player UUID alone.

## Logical identities and persistence

Java static registries freeze before worlds load. Permanently registering every downloaded Bedrock ID into them would prevent safe per-world activation/removal and would require restarting for new content. Crossrock therefore registers one persistent entity host (`bedrock_compat:actor`) and item host (`bedrock_compat:item`) during common initialization. The world registry supplies each host's logical Bedrock identity. This is the permanent indirection layer, not a finite pool of placeholder registrations.

Entity identity, full typed properties and private dynamic state persist in entity save data. Declared defaults, bool/int/float/enum validation, ranges, and `client_sync` filtering work. Only client-visible properties travel in host entity data; the state envelope carries protocol version and world generation. Removing a pack clears visible state and leaves saved private identity/state available for re-enabling. The host has no invented AI goals. Physical/gameplay component semantics remain deferred.

Item identity and owning pack survive through vanilla item-stack custom data. The stack's `ITEM_MODEL` selects the translated model under its logical ID. Native equipment/attachable queries use the same logical item identity. Wearable/use/food/trade/stack-rule component semantics remain future subsystem work.

`BehaviorStateStore` provides namespaced world-owned JSON data at `data/crossrock/state.json`, with a version header and atomic replacement. It saves on explicit server saves, periodic saves and shutdown. This storage service is not an implementation of Script API `getDynamicProperty` on every Bedrock host type.

Administrative foundation commands are `/crossrock status`, `/crossrock summon <namespace:id>` and `/crossrock give <namespace:id>`. They require Java gamemaster permission and resolve active world registries. Bedrock `/summon`, selectors, functions and commands are not implemented by those administration tools.

## Networking and client resources

The `behavior_v1` channel uses a bounded versioned envelope: protocol version, session UUID, revision, message kind, bounded metadata and at most 32 KiB of bytes. Server offers include an ordered pack UUID list, bundle SHA-256 and size. Clients request missing content or validate a cached bundle, receive ordered chunks, verify the hash and actual inflated archive budgets/paths, validate manifests/dependencies, generate resources, then acknowledge only after resource reload completes. Incompatible protocols, rejected profiles and timed-out negotiations disconnect with a reason. Stale generations are ignored.

Transfers are limited to four chunks per player per tick, a 128 MiB total content/profile budget, 32 MiB per decoded entry, 100,000 entries, and a negotiation deadline. The cache is content-addressed under `.crossrock/server-packs/<hash>/profile.mcaddon`; downloaded scripts are not executed. Cached files remain reusable after disconnect; active state does not.

Each world RP becomes `crossrock/world_rp/<uuid>`, a fixed, required Java resource pack with its own metadata/icon. Ordered prefix translation differences give each layer the resources it changes while resolving shared atlases and definitions against lower layers. Existing translation infrastructure does all conversion. Per-actor native source ranks preserve resource ownership across multiple individual RPs. Fixed world-pack entries are excluded by vanilla from persisted user RP options.

Custom actor renderer lookup uses the logical Bedrock identity. Native property queries receive the declared synchronized host values only when their generation matches the acknowledged connection profile. Vanilla entity and item identities retain their existing paths. Native tracking handles host spawn/movement/equipment/property updates and removal; pack/profile negotiation is Crossrock's versioned protocol.

Disconnect cleanup handles both the Fabric disconnect signal and connection disappearance on client ticks. This covers integrated-world exit paths that bypass the signal. The client drops world definitions/transfers/resources and removes world-pack selections; a resource cleanup reload is deferred until the integrated server has stopped. A late disconnect from an older handler does not clear a newer connection.

## Deliberate remaining gaps

This foundation does not implement Script VM/module exports, Script API events/scheduling/DDUI, BP components/AI/sensors/groups/events, server animation/Molang commands, Bedrock command selectors/functions, recipes/trades/loot/worldgen semantics, or general per-viewer property overrides. Definition retention is not execution. Generic hosts do not make native `/give oreville_vn:…` resolve as static Java IDs. Bedrock engine/API-version compatibility, every resource schema and VN gameplay remain unverified.

Large-stack performance, more than one simultaneous remote client, hostile/slow WAN behavior and cross-version upgrades need broader acceptance testing. Pack transfer is direct, bounded and content-addressed; there is no external HTTP service. See [foundation coverage](../../BEDROCK_BP_COVERAGE.md) and [validation](behavior-packs-validation.md) for what was actually exercised.
