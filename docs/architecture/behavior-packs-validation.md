# Behavior Pack foundation validation

Validated on macOS Apple M2, Java 25, Minecraft 26.2, Fabric API 0.159.0 and Loader 0.19.3, 2026-10-13. No VN gameplay compatibility certification.

| Check | Result | Evidence |
|---|---|---|
| `:test :build` | Pass: 455 tests, zero failures/errors, one skipped | `/tmp/crossrock-behavior-final-build.log`; JUnit XML under `/tmp/crossrock-bp-verified/main/test-results/test` |
| Focused real client foundation test | Pass | `/tmp/crossrock-behavior-client-12.log` |
| Existing RP real client regression | Pass after user-interrupted run was rerun | `/tmp/crossrock-behavior-rp-regression-rerun.log` |
| Separate server-only JVM | Reached Done with 4 packs, 2 actors, 2 items | `/tmp/crossrock-behavior-headless-packs.log` |
| Installed client | Byte-for-byte verified, old JAR/database backed up | Fabric 26.2 Modrinth profile; content-store binding and SHA-1/size updated |

Focused test command: `CROSSROCK_BEHAVIOR_ONLY=1 CROSSROCK_TEST_EULA_ACCEPTED=1 ./gradlew -I /tmp/crossrock-build-location.gradle :runClientGameTest --console=plain`. The EULA flag was used only after explicit user acceptance for isolated local tests. Normal RP regression omits those environment flags.

The fixture exercises two BPs and paired RPs, actual Create World selection, offline existing-world edit and level.dat activation, dependency closure, server reload, invalid-definition rollback, logical summon/give, native actor mesh generation, item identity and client property synchronization, saved actor/world state after reopen, partial/full removal, integrated cleanup, dedicated loopback negotiation, disconnect and cached reconnect. The headless smoke binds loopback on an ephemeral port, uses a fresh test world and enables both BP IDs with `initial-enabled-packs`; shutdown uses SIGTERM and saves cleanly. It confirms common code loads without client classes.

A test-only mixin schedules the new IntegratedServer.halt cleanup instead of blocking on the Fabric client-test phaser. Thread dumps identified a test-framework deadlock. The shim is exclusively in the game-test source set and is absent from the installed production JAR. Production shutdown remains vanilla.

Build outputs were redirected outside the synced Documents directory using a temporary Gradle init script. Finder/iCloud duplicate generated class files in the original build directory caused invalid JUnit discovery; an isolated output tree avoids that environmental corruption. No runtime source was changed to mask it.

The init script sets every project buildDirectory beneath `/tmp/crossrock-bp-verified`, clientGameTest runDir to its `game` child, and server runDir to its `dedicated` child. Temporary logs are local evidence and may be deleted by the OS; the committed test sources reproduce the assertions. Expected platform warnings include Realms authorization for the test identity, OpenGL driver limitations and explicitly deferred gameplay declarations.

Installed JAR SHA-256: `f463e6aedaa10986efe84e83e58c1fffbad89c3e1d545e2cd99186a6c03f8f99`.

Remaining acceptance gaps: simultaneous remote clients, slow/hostile WAN and maximum-size stack performance, cross-version upgrades, per-viewer overrides, actual VN gameplay and all deferred Script API/component semantics. Fixture success demonstrates the foundation only.
