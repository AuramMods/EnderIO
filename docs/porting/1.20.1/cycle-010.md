# Cycle 010 — item state consumers, fluids and souls

Status: progress, not completion. The preceding cycle made verified progress. Main Ender IO and the addon still do not build or run; all feature families remain required.

## Changes

- Converted the saved keys in `init/EIODataComponents` to typed NBT keys, retaining their names and prior key aliases. The nonexistent 1.20.1 data-component registry registration was removed; native item NBT supplies persistence and item packet transport. The separate API conduit-holder key remains pending because its readers need dynamic registry context.
- Converted ordinary item reads/writes, filters and filter menus, soul bindings, capacitor lookups, paint/facade consumers, tool toggles, vial data, coordinate selections and relevant addon filter access to the typed key API. Machine block-to-item/drop/place hooks still use the newer component API and remain explicit blockers.
- Preserved item defaults through `withDefault` during item construction: capacitors, facade variants, machine soul defaults, redstone filter modes and tools. Live soul, redstone, energy and fluid views retain their providers during writes; snapshot filters refresh after replacement. The Mekanism chemical filter's key now uses NBT, but its older chemical APIs and integration remain unported.
- Added `NbtFluidHandlerItemStack`, using Forge's actual transfer implementation and the same typed fluid key as retained machine-item state. Strict fuel handlers, fluid tanks, void vials, cold-fire igniters and levitation staffs use it. Equivalent accepted fuels retain the existing strict-handler conversion behavior. Native `FluidStack` replaces `SimpleFluidContent` in the affected stored-value paths.
- Ported the actual main `Soul` codecs and packet serialization to 1.20.1. Required souls reject empty/null entity types; optional souls preserve empty-map/null support. Current and legacy soul formats remain readable. Unknown entity IDs are rejected explicitly because the native defaulted entity registry otherwise resolves them to its default entity. Optional decoding must not turn a malformed present ID into empty state.
- Ported the main single-soul item storage/binding classes and spawn-egg soul views. Spawn eggs read native `EntityTag`, honor its entity-type override, and copy the data before soul sanitization.
- Replaced component-based basic material glint/lore with native item methods, retained the creative icon's glint and exclusion, and used native sword construction/player-head NBT. Item filter NBT matching compares both directions while ignoring only `Damage`, which has its separate filter mode. Appearance is not yet visually verified.

## Actual verification

Final command:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar :enderio:compileJava --continue --console=plain
```

Exit **1**, due to main Ender IO compilation still failing at the normal 400-error cap. EnderCore check, jar and reobfuscation pass. Log: `build/port-evidence/cycle-010-check-package-main-final.log`.

| Suite | Executed | Passed | Failures/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 36 | 36 | 0 | 0 |
| Native headless Forge GameTests | 47 | 47 | 0 | 0 |

Fourteen additional GameTests cover fluid accounting, simulation, capacity, copy/save independence, equivalent-fuel conversion, six baseline soul codec behaviors, soul item views/binding rules, packet rejection, spawn-egg overrides and NBT filtering. Test names/counts are recorded in `build/port-evidence/cycle-010-tests.json`; XML results remain under `endercore/build/test-results/`.

The GameTest compilation deliberately includes **ten actual standalone main-module source files** (soul data/interfaces/item views, spawn-egg views and item NBT comparison) in the **test output only**. Their source remains in `enderio`; there is no copied or stubbed production implementation. This lets the native Forge fixture execute the changed state behavior before the rest of the main module compiles. It is not evidence of a successful full main build. The ordinary main compile is still run and reported as failing. Move this temporary fixture coverage into the main test runtime once that runtime builds.

The server ran in the existing isolated headless/`nogui` profile with accepted EULA and saved/shut down cleanly. No client, audio subsystem or desktop focus interaction was launched.

Supplemental diagnostics:

```sh
./gradlew -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Argument export passed. Final direct Java 17 compilation exits **1 with 2,966 errors and 100 reported warnings**, compared with 3,140 in cycle 009. Annotation processing is disabled in this supplemental run; diagnostic counts are not independent defect counts. Logs: `build/port-evidence/cycle-010-compiler-arguments.log` and `build/port-evidence/cycle-010-main-javac-final.log`.

Intermediate failures are retained and resolved within the cycle: a test-source compile exposed `Codec.EMPTY` shadowing `Soul.EMPTY` in the anonymous codec; the first expanded runtime found numeric unknown entity IDs falling back to the default entity; the next run caught the equivalent named-ID problem. Explicit validated decoding fixed these cases. The next run passed all 46 tests, before adding the spawn-egg test for the final 47-test suite. The initial core run passed the existing 33 tests. Every combined run continued to report main compilation failure. An intermediate direct source run reported 2,979 errors.

The intermediate core artifact is named from the pre-cycle base commit because packaging precedes the commit:

- Jar: `endercore/build/libs/endercore-8.2.14.9-1.20.1+7e9bd93db4.jar`
- SHA-256: `f075f7bdbefd0d0a2d98f2052d27db985a555e5db9e0555abc90ac54bca3fa8d`
- Every class is version `61` (Java 17). No test fixtures or main-module classes used by the temporary tests appear in the core jar. The new fluid handler is included.

Artifact inspection: `build/port-evidence/cycle-010-artifacts.json`. `git diff --check` passes. All launched test/build commands reached terminal states.

## Remaining work and next step

Main networking/packet codecs are now a large common blocker for item state records and the rest of the mod. Port their actual native serialization and transport next, preserving bounded decoding, registered IDs, direction and server authority. Complete conduit item data with current registry context; do not resolve dynamic holders against a guessed/global stale registry.

Continue replacing machine implicit-component hooks with actual native item drop/place NBT, and finish all item/filter/soul/paint/recipe serialization consumers. Definitions and helper tests alone do not prove block break/place, persistence, tooltip rendering, gameplay, GUI sync or optional integrations. Other remaining work includes attachments/data maps, recipe/loot/criterion APIs, blocks, render/UI APIs, datagen and addon APIs.

NBT key aliases and legacy soul-record tests do not establish historical Ender IO 6.x world migration. Audit and test that separately on disposable fixtures. The main feature matrix remains runtime-unverified; clean packaged client/server, single-player, multiplayer, chunk/world persistence, integrations present/absent and silent non-activating client testing remain required.
