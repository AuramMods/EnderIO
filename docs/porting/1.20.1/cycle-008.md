# Cycle 008 — Forge capability ownership and neighbor updates

## Changes

- Added `BoundCapabilityProvider`, `CapabilityUtil`, `NeighborCapability` and `ItemCapabilityRegistry` in EnderCore. They use real Forge `Capability`, `LazyOptional` and attachment events. Successful factories are retained per owner/capability/side; missing capabilities can be queried again. Native lifecycle invalidation clears the stored optionals and notifies consumers.
- Data/side refresh clears only EnderCore-owned providers, preserving other mods' attached providers. All owned provider caches are cleared before callbacks run, so a callback querying another provider observes updated data. Tracking uses weak owner keys and weak provider references. Native removal/unload still uses Forge's normal attachment invalidation listeners.
- Neighbor lookups do not load chunks. Neighbor tracking observes invalidation and polls for previously missing capabilities; closing a tracker removes its listener. Main conduit, solar-panel and capacitor-bank callers now refresh their derived target lists and close trackers when removed/unloaded. Full gameplay and actual chunk unload/reload validation of those callers remain pending.
- Ender IO capability keys are native Forge tokens with native interface registration. Forge identifies capabilities by interface type, so item/block soul aliases share a token. Block proxy/query APIs use a nullable `Direction`; NeoForge's separate generic context type is removed. Existing sided and unsided behavior is retained in the factories.
- Machine block-entity registrations use native `RegistryObject` values and the tested EnderCore attachment helper. Conduit bundles attach providers for their registered conduit types' exposed capabilities. Side changes refresh owned handlers. Main item and vanilla spawn-egg bindings now use native attachment events; existing item registrations and factory selection are retained.
- Converted corresponding energy, inventory and fluid interfaces to their genuine Forge types, provider factories to Java `BiFunction`, and native nullable item queries through `LazyOptional.orElse(null)`. Main world capability queries use the loaded-block lookup helper. Optional conduit APIs were adapted to the new block capability signature; their older dependency APIs still require further conversion.
- Added a supplemental compiler-argument export task at `scripts/porting/compiler-diagnostics.init.gradle`. It exports the actual main source list and resolved compile classpath for a direct Java 17 compiler run, avoiding the large-error-report transport failure from cycle 007. Annotation processing is deliberately disabled in this diagnostic run; normal Gradle compilation remains required and is also executed.

## Actual verification

The final command was:

```sh
./gradlew :endercore:check :enderio:compileJava --continue --console=plain
```

It exited **1 because main Ender IO compilation still fails**, at the normal 400-error reporting limit. EnderCore compilation and check passed. Final log: `build/port-evidence/cycle-008-check-main-compile-4.log`.

| Suite | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 36 | 36 | 0 | 0 |
| Headless native Forge GameTests | 16 | 16 | 0 | 0 |

The GameTest suite contains the previous seven serialization/synchronization tests plus nine capability tests. New evidence covers actual registered block placement, sided/unsided access, stable successful factories, late capability availability, reentrant refresh, block removal/replacement, closing/removing consumers, queries without chunk loading, copied item providers, and refresh preserving unrelated providers. Item refresh also verifies a callback sees updated data in a second owned provider. Fixtures live only in the test source set and are not shipped.

XML results are under `endercore/build/test-results/test/` and `endercore/build/test-results/gameTestServer/results.xml`; `build/port-evidence/cycle-008-tests.json` records names and counts. The final server log confirms all 16 required tests passed and the server saved/shut down cleanly. It used `nogui`, the existing headless profile and automatic test EULA acceptance. No client or audio subsystem was launched.

The supplemental diagnostic commands were:

```sh
./gradlew -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Export succeeded; the direct compiler exited **1 with 3,458 source errors and 100 reported warnings**, without a report-transfer OOM. Logs: `build/port-evidence/cycle-008-compiler-arguments-3.log` and `build/port-evidence/cycle-008-main-javac-final.log`. This is a supplemental source diagnostic, not a passing build or an independent-defect count.

Intermediate results are retained: the initial core check passed 36 unit/14 GameTests; the first combined run passed core and failed main compilation. Extracting shared item bindings briefly introduced a Java method-reference inference error, fixed by giving the attachment listener an unambiguous method name. The subsequent combined run passed 36 unit/16 GameTests. The argument-export task initially inherited the main compile task as a dependency and therefore failed with main compilation; it now depends on the compile classpath's build dependencies directly. An attempted direct compiler invocation before that correction exited 3 because no argument file had yet been written. These were tooling/source failures, not skipped successful tests.

`git diff --check` passed. All launched commands and test servers reached terminal states.

## Remaining work and next cycle

The main mod and bundled addon still do not build or run. Remaining source errors include data components/NBT, packet codecs, attachments/data maps, deferred item/block holders, recipes/inputs/serialization, rendering and optional integration APIs. No feature-parity row is promoted to runtime-verified by the helper tests.

Next, implement typed NBT-backed item state and native item/block registration helpers. **When a write replaces a value returned by an item capability factory (for example a filter snapshot), the NBT implementation must invoke `ItemCapabilityRegistry.refresh(stack)` after updating that data.** Live stack-backed energy/fluid views need their own native NBT implementation; the new attachment infrastructure alone does not port their storage or persistence. Avoid invalidating other mods' providers or treating metadata conversion as save compatibility proof.

LaserIO's provider registration and snapshot/live filter behavior still need adaptation, as do Mekanism's older chemical categories/capabilities and the other addon APIs. Actual conduit transfers, multiblocks, cache behavior across real chunk/world reloads, save compatibility, multiplayer, client rendering and packaged-jar validation remain required.

Reference: [Forge 1.20.1 capabilities and lifecycle](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/). Exact capability, attachment and block-entity APIs were also inspected in the resolved Forge 47.4.28 source jar and tested on its headless GameTest launcher.
