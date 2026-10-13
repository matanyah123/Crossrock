# Implementation follow-up: Behavior Pack foundation

The original inventory remains a historical audit of the initial code. This follow-up supersedes only infrastructure status; the VN requirements and raw inventory are unchanged.

- [x] Unified discovery, manifests, icons and exact dependency closure.
- [x] Individual per-world BP datapacks, separate selection tabs and paired individual RPs.
- [x] Server-owned registries, reload validation/rollback and atomic world state.
- [x] Generic logical actor/item hosts, saved identity, typed declared defaults and client_sync transport.
- [x] Versioned negotiation, bundle validation, cache, profile synchronization and cleanup.
- [x] Integrated lifecycle and dedicated loopback reconnect/removal fixture tests.
- [ ] VN component/AI/groups/events and command/function execution.
- [ ] Script VM, @minecraft/server and @minecraft/server-ui implementations.
- [ ] Complete per-viewer overrides, world interaction and gameplay contracts.
- [ ] VN gameplay and visual/audio/UI compatibility certification.

Raw scripts and arbitrary definitions are retained; retention does not execute them. Generic registration does not implement the 99 component kinds or 36 subscription endpoints inventoried by this audit. Existing native RP translation is reused, with logical host identities and per-pack source ranks. Existing RP checklist gaps remain open.

See [coverage](../../../BEDROCK_BP_COVERAGE.md), [architecture](../../architecture/behavior-packs.md) and [validation](../../architecture/behavior-packs-validation.md).
