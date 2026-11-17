# Cycle 004: port core networking and synchronization

Date: 2026-11-17. Starting commit: `7d4dbcaa2`.

## Changes

- Registered all six core messages on a genuine Forge `SimpleChannel`, with explicit directions, protocol `forge-1`, game-thread consumers and client-only dispatch isolation. Added native send helpers for server, player and chunk-tracking recipients.
- Added project-owned `PacketCodec`/`PacketCodecs` utilities over `FriendlyByteBuf`; native item/fluid NBT and particle serializers remain the platform's responsibility. No Minecraft or Forge classes were fabricated. Ported all core slot payload variants, including lists/pairs, with collection, byte-array, discriminator and nesting bounds.
- Ported menu construction and synchronization, recipe references (1.20.1 recipes own their IDs), legacy block-entity updates and registry-aware NBT codec calls. Block-entity save hooks now use 1.20.1 signatures.
- Restricted legacy client edits to an open, valid menu for the target block entity and explicitly editable slots. Marked all three existing editable slot declarations (machine I/O, redstone and travel-anchor configuration) accordingly. Remaining main-module consumers still require porting and runtime verification.
- Replaced unavailable NeoForge capability caches with direct native Forge provider queries. Neighbour queries avoid loading absent chunks and reacquire the current provider, rather than retaining a potentially stale neighbour reference. Runtime invalidation/revival behavior remains untested.
- Adapted fluid change detection to Forge equality, which ignores amount, and retained fluid NBT during quantity-only updates. Corrected inverted registry-slot/incremental-save change predicates encountered in this path. Regression tests for these registry-dependent behaviors are written but blocked by the bootstrap failure below.
- Extended the temporary scoped test task with packet tests and registry-dependent synchronization tests. It uses production sources and dependencies, retains normal build/test tasks, and now writes runtime logs under `build/`.

## Actual validation

| Command | Observed result |
| --- | --- |
| `./gradlew :endercore:compileJava --console=plain` | First attempt: 52 errors; after block-entity/API adaptations: 25 errors; final repeat: 25 errors. Remaining diagnostics concern GUI/render APIs and a Java 21 graph collection call |
| `./gradlew --no-configuration-cache -I scripts/porting/foundation-tests.init.gradle :endercore:portFoundationTest --console=plain` | Initial scoped compile exposed missing compile-only annotations and older JUnit/buffer method differences; corrected. Final test run: 18 test methods passed, plus one failed class-initialization result; task exits 1 |
| `git diff --check` | Passed |

The final XML reports contain 10 passing `PacketCodecTests`, eight passing `OrderedListCodecTests`, and one failed `SyncSlotTests.initializationError`, with zero reported skipped tests. **The seven methods in `SyncSlotTests` did not execute.** Do not interpret the 19 reported results as 19 executed test methods or call the task passing.

Packet tests exercise primitive/location values, nested lists/pairs, both menu packet directions, exact/empty collection bounds, invalid collection counts, invalid payload IDs, excessive nesting, truncated packets, maps/duplicate keys and symmetric byte-array bounds. Tests release their actual Netty buffers. This does not test delivery, connection negotiation, game-thread behavior or gameplay.

The actual Forge bootstrap failure is:

```text
Bootstrap.bootStrap -> NetworkHooks.init -> NetworkConstants
RuntimeException: Error computing listener list for net.minecraftforge.network.NetworkEvent
Caused by: NoSuchMethodException: net.minecraftforge.network.NetworkEvent.<init>()
```

Forge's event-class transformations have not run in a plain JUnit process. A proper transformed Forge test launch is needed; no test was disabled, bypassed or replaced with a fake platform implementation to hide this failure.

Local evidence (generated, ignored by git):

- `build/port-evidence/cycle-004-compile-{1,2,final}.log`
- `build/port-evidence/cycle-004-foundation-tests-{1,2,final}.log`
- `build/port-evidence/cycle-004-junit-bootstrap-logs/`
- `endercore/build/test-results/portFoundationTest/TEST-*.xml`
- `endercore/build/reports/tests/portFoundationTest/index.html`
- `endercore/build/port-foundation-tests/runtime/logs/`

## Next step and unresolved work

Finish core GUI/render and Java 17 compilation, then establish the transformed Forge test launch and port the remaining module consumers. All registry-dependent tests, actual packet delivery/authority checks, capability lifecycle, save/reload, datagen, integrations, packaging and gameplay remain incomplete. No game window or server was started. Focus-safe client instrumentation is still required before any client launch.
