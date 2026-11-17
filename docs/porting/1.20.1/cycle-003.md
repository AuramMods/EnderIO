# Cycle 003: adapt core registration and serialization

Date: 2026-11-17. Starting commit: `7cf58a36d`.

## Changes

- Replaced core deferred-holder inheritance with project helpers backed by genuine Forge `DeferredRegister` and `RegistryObject`. Preserved item/menu/fluid/block-entity registration builders.
- Added native Forge block-entity capability attachment factories with sided `LazyOptional` caching and invalidation listeners. Actual attachment, state changes, removal and revival still require runtime coverage.
- Adapted core item energy and fluid access to `ForgeCapabilities`; converted the strict item-fluid handler to Forge's NBT-backed handler. Its main-module callers and save migration are still pending.
- Adapted shaped-recipe delegation and serialization to `CraftingContainer`, `RegistryAccess` and Forge 1.20.1 JSON/network serializers; adapted recipe datagen to `Consumer<FinishedRecipe>` and native advancement triggers.
- Replaced newer item/fluid save helpers and several unavailable resource/codec APIs. Namespace updates in remaining core files are intermediate; networking, rendering and other newer APIs still require substantive changes.
- Preserved ordered-list save representation and exact JSON assertions while replacing the newer DFU list bound API. Added boundary tests and a decoder guard after a real test exposed invalid partial indices from DFU 6.
- Removed the unnecessary NeoForge server fixture from the pure ordered-list tests. Added a temporary scoped Gradle init script that compiles/runs the real codec and tests against the actual Forge/Java 17 test classpath without altering the normal module compile/test tasks.

## Actual validation

| Command | Observed result |
| --- | --- |
| `./gradlew :endercore:compileJava --console=plain` | Failed after successive adaptations with 372, then 319 errors; final repeat after codec fix also fails with 319 errors and 18 warnings |
| `./gradlew --no-configuration-cache -I scripts/porting/foundation-tests.init.gradle :endercore:portFoundationTest --console=plain` | First run: 8 executed, 1 failed (`ArrayIndexOutOfBoundsException` for invalid partial index). After fix: 8 executed, 8 passed, 0 failed/errors/skipped, Java 17 |
| `git diff --check` | Passed |

Passing tests cover the original exact encode/decode format, sparse/out-of-order slots and defaults, exact capacity, oversized lists during encoding and decoding, negative/out-of-range indices, and zero capacity. This isolated result is **not** a full-module or gameplay test result. Remove the temporary test isolation once the normal module compilation permits the same tests to run normally.

Local evidence (generated, ignored by git):

- `build/port-evidence/cycle-003-compile-{1,2,final}.log`
- `build/port-evidence/cycle-003-foundation-tests.log` (initial failure)
- `build/port-evidence/cycle-003-foundation-tests-2.log` (passing rerun)
- `endercore/build/test-results/portFoundationTest/TEST-com.enderio.core.serialization.OrderedListCodecTests.xml`
- `endercore/build/reports/tests/portFoundationTest/index.html`

## Remaining work and next step

Port core network codecs, packets and synchronization to `FriendlyByteBuf`/Forge `SimpleChannel`, followed by block-entity lifecycle, recipe-input and GUI/render API adaptations. Main Ender IO and modded-conduit consumers still use the newer APIs and have not reached compilation. All full-suite tests, datagen, integration behavior, packaging, save migration and gameplay evidence remain incomplete. No client or server was launched, and no runtime capability behavior is claimed.
