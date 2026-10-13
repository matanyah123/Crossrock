# Behavior Pack foundation coverage

Status: foundational infrastructure implemented; Bedrock gameplay compatibility is not certified. Villager News is not playable on this evidence.

| Area | Status | Evidence / limits |
|---|---|---|
| Discovery/import | Implemented | Directories, mcpack/mcaddon, icons, UUID versions and dependency closure; atomic bounded imports. |
| Per-world selection | Implemented | Individual virtual datapacks; separate native Java/BP tabs; Create World and offline Edit World selections in level.dat. |
| Server ownership | Implemented | Per-server immutable profiles, reload validation/rollback, subsystem prepare/open/tick/close, atomic namespaced world state. |
| Logical registration | Foundation implemented | Permanent actor/item Java hosts resolve world-owned logical Bedrock IDs; typed properties and item identity persist. Component semantics remain deferred. |
| Networking | Implemented | Versioned server-scoped offer/request/chunks/ack, hashes, bounded decoding, cache, generation checks, timeout and disconnect cleanup. |
| Client resources | Implemented | Individual required world RPs reuse translation pipeline; logical actor/item rendering and synchronized client properties. |
| Integrated/dedicated/multiplayer | Fixture tested | Integrated lifecycle and loopback dedicated connect/reconnect/removal tested; multi-client WAN and cross-version acceptance remain open. |
| Script API/gameplay | Deferred | No JavaScript VM, Script API endpoints, component/AI/event execution, Bedrock commands/functions, recipes/trades/loot or worldgen implementation. |

Evidence: `BehaviorInfrastructureTest`, `BehaviorInfrastructureChecks`, common `behavior` package and client `behavior` package. See [architecture](docs/architecture/behavior-packs.md) and [validation](docs/architecture/behavior-packs-validation.md).

Recommended next order: component lifecycle and entity physical semantics; event/groups and world interactions; commands/functions and persistence contracts; scripting host and scheduling; Script API subsystems; per-viewer state and broader multiplayer certification. Each requires its own behavioral tests.
