# Cycle 011 — native packet serialization and registry context

Status: progress, not completion. Cycle 010 made verified progress. The complete mod and addon still do not compile or run; the full parity and runtime requirements remain unchanged.

## Changes

- Replaced the main/addon references to Minecraft's newer `StreamCodec`, `ByteBufCodecs` and `RegistryFriendlyByteBuf` with the project's native `FriendlyByteBuf` packet serialization. Packet records, conduit settings, item/filter data, soul data, recipe serialization fields and menu constructors now use these types. Existing public record fields and collection limits are retained. `MassiveStreamCodec` and `DumbStreamCodec` remain project helper names with native implementations; their stateless factory behavior is preserved.
- Extended `PacketCodec` with one- through nine-field composites, optional values, unit validation, member writers, discriminated payloads and map factories. Collection decoding stays bounded and rejects duplicate keys. Added native variable integers, UUIDs, directions, dye colors, ingredients, required NBT, either values and resource keys. Required versus optional item stack codecs retain their empty-stack distinction and use Forge's item/NBT transport.
- Native registry codecs validate both directions, including registries/enums whose lookup otherwise falls back to a default value. Custom Forge registry suppliers resolve at packet processing time and encode registered resource locations. Enchantment recipe serialization now uses the native static enchantment registry; the remainder of the recipe serializer API is still pending.
- Added `RegistryPacketBuffer`, a project-owned view over the native buffer carrying explicit current-world `RegistryAccess`. Dynamic conduit holders encode registered keys and decode against the receiving context. Direct/unbound, stale-context, missing-context and unknown holders are rejected. No static global world/registry cache is introduced; the view does not retain or own its backing buffer.
- Conduit menu opening now uses native `NetworkHooks.openScreen`, wrapping its server/client extra-data buffers with the respective player's current world registry access. Other menu constructors accept the native buffer. Actual menu opening/gameplay is not yet runnable or verified.

## Actual verification

Final combined command:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar :enderio:compileJava --continue --console=plain
```

Exit **1**, solely because main compilation still fails at the normal **400-error cap**. EnderCore check, jar and reobfuscation pass. Log: `build/port-evidence/cycle-011-check-package-main.log`.

| Suite | Executed | Passed | Failures/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 39 | 39 | 0 | 0 |
| Native headless Forge GameTests | 51 | 51 | 0 | 0 |

Three new unit tests cover optional/either/unit behavior, nine-field wire order and discriminated payload selection/rejection. Four new GameTests cover distinct sending/receiving registries, rejection of stale/direct/unknown holders and missing context, native/Forge registry fallback rejection, enum bounds, native item NBT independence, required NBT/stack rejection and the actual main-module travel-coordinate record's dimension/position round trip.

The existing temporary main-source test bridge now compiles **eleven actual main source files** into test output, adding `CoordinateSelection` to cycle 010's ten files. No copied production implementation, fake platform class or test fixture enters the core jar. This does not establish that the main module compiles. Remove the temporary bridge in favor of normal main-runtime tests once available.

Test counts/names: `build/port-evidence/cycle-011-tests.json`. Current XML suites: `endercore/build/test-results/test/` and `endercore/build/test-results/gameTestServer/results.xml`. Historical `portFoundationTest` output belongs to earlier cycles and is not part of these counts. The isolated headless/`nogui` Forge server saved and shut down cleanly. No client, audio subsystem or desktop interaction was launched.

The first core unit run passed the prior 36 tests. An intermediate expanded check failed compilation of a new test due to generic inference in its `Either` fixture; explicit generic typing fixed it before the final run. No runtime test failures occurred in the final run.

Supplemental diagnostic command:

```sh
./gradlew -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Argument export passes. Final direct Java 17 compilation exits **1 with 2,095 errors and 100 reported warnings**, down from cycle 010's 2,966. The first diagnostic pass reported 2,104. These are supplemental counts with annotation processing disabled, not independent defects or normal-build success. Logs: `build/port-evidence/cycle-011-compiler-arguments.log`, `cycle-011-main-javac-initial.log` and `cycle-011-main-javac-final.log` in the same directory.

Intermediate core artifact, named from the pre-cycle base commit:

- `endercore/build/libs/endercore-8.2.14.10-1.20.1+5e98ee139a.jar`
- SHA-256: `bab1a3a21aeaeb058f78847b1ea65d29a837858577aeadf9366e52c5a47cb877`
- All classes are Java 17 version `61`; the registry buffer is packaged; no GameTest/main-source fixture classes are included.

Inspection record: `build/port-evidence/cycle-011-artifacts.json`. `git diff --check` passes. Every owned build/test command reached a terminal state.

## Remaining work and next cycle

Port the **main transport** next: its `CustomPacketPayload`, payload registration, handlers/context, packet distributors and client/server dispatch still use newer APIs. Connect the codecs to actual Forge `SimpleChannel` transport with a distinct protocol version. Decode dynamic-registry messages with current connection/world context on the appropriate main thread; never silently use a stale registry. Preserve packet directions, validate server authority/menu/world/range inputs and test actual dispatch. Codec tests alone do not establish working transport or synchronization.

Finish the conduit item holder NBT key and its registry-aware consumers. Recipe serializer methods, newer sized ingredient APIs, particle deserializers and Mekanism chemical serialization remain pending even where their surrounding codec declarations now use native types. Addon integration behavior is unverified. Main compilation still has attachments/data maps, item-state drop/place hooks, rendering/UI, newer vanilla APIs and resource/datagen blockers.

All final gameplay, persistence, migration, packaged client/server, multiplayer, visual/UI and optional-integration requirements remain outstanding. Do not interpret fewer compiler diagnostics or isolated native tests as feature parity.
