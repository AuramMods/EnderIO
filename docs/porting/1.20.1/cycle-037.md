# Cycle 037: fluid conduit rates and live filters

Starting commit: `9cdd47d46`. Branch: local `1.20.1`. The goal was reread; cycle 036 made verified progress. The tree was clean, with no CodeGraph index, on-disk manual-change protocol or active merge. No remote changes or client launches occurred.

## Reproduced rate failures

Six new native GameTests place actual Ender IO tanks and conduits, use real Forge capabilities, let network attachment settle, then seed fluid and run one valid scheduled network update in a single server callback. This prevents normal world ticks from interleaving with the measured transfer. The initial suite executed **73 tests and failed all six new cases**:

| Scenario | Required result | Observed before fix |
| --- | ---: | ---: |
| Basic conduit, one five-tick update | 1,000 mB | 200 mB |
| Pressurized conduit | 5,000 mB | 1,000 mB |
| Ender conduit with a 32,000 mB source | 32,000 mB | 8,000 mB |
| Mixed ender/basic/ender path | 1,000 mB | 200 mB |
| Partial first destination accepting 100 mB | 900 mB into the second branch | 200 mB |
| Test datapack rate 715,827,883 mB/t | All 32,000 mB available | 0 mB |

The source extraction budget already multiplied by the conduit tick interval, but each path's rate did not. Path limits now use the same five-tick interval. Each successive branch is bounded by the remaining extraction budget, and the simulated offer shrinks by the amount already transferred. This prevents the corrected path rates from reusing the original offer on later branches. Saturating integer multiplication prevents a valid large datapack rate from overflowing into a negative budget. The advanced raw-rate tooltip uses the same saturated calculation; its visual presentation remains untested.

All six cases pass after the changes. The branch case requires 100 mB into the first tank, 900 mB into the second and exactly 9,000 mB left from a 10,000 mB source. The ender-tier fixture is limited by the real tank's 32,000 mB contents, below that tier's 40,000 mB update budget; this test does not claim an observed 40,000 mB transfer.

## Live filter and routing evidence

Three further native scenarios pass, expanding the main gameplay suite to **76**:

- An actual basic fluid-filter item on extraction first rejects water via a lava allow list, then allows exactly one 1,000 mB transfer after editing the same stack's filter data, then rejects it after changing to a water deny list. Capability refresh applies edits to the next transfer.
- An insertion filter rejects mismatched fluid NBT, allows it when comparison is disabled, accepts matching NBT after another edit and rejects it on the deny list. The source retains exactly 3,000 mB and the destination receives 2,000 mB with the original NBT. These are existing saved NBT-match settings, which the basic filter UI warns about but cannot enable; no new filter variant or UI feature was added.
- A destination on a different color channel receives nothing; restoring the matching channel resumes transfer. Disabling extraction through redstone control stops it, and restoring always-active resumes it. Actual network routing caches reflect these changes.

The new large-rate conduit definition lives only in the test datapack and is listed by the normal native discovery mechanism. No production registration or baseline test was removed. The six rate cases first passed as a 73-case suite; all nine additions then passed as 76 cases and again during the full build.

## Actual validation

Commands:

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue --console=plain
python3 scripts/porting/packaged_server_smoke.py \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.36-1.20.1+9cdd47d46b.jar \
  --name cycle-037-final
```

The reproduction command fails as described above; the fixed targeted runs and full build pass. Final passing report counts:

| Profile | Tests | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Core JUnit | 61 | 0 | 0 |
| Main CPU JUnit | 16 | 0 | 0 |
| Addon JUnit | 1 | 0 | 0 |
| Core native GameTests | 170 | 0 | 0 |
| Native Mekanism adapters | 7 | 0 | 0 |
| Main registry/Jupiter native profile | 112 | 0 | 0 |
| Main gameplay | 76 | 0 | 0 |
| Actual-mod addon GameTests | 18 | 0 | 0 |

Totals: **78 ordinary JUnit and 383 native GameTest results**. Gradle reuses valid up-to-date unit outputs where applicable; native profiles execute during the build. In-server Jupiter is counted once; profile overlap remains possible. All 58 enabled baseline gameplay cases remain, along with nine prior tank regressions and these nine new conduit cases.

The final distributable passes the existing clean packaged Forge harness: **16 tank state assertions across three server sessions**, two disk-backed restarts and a datapack reload, without optional mods or the development classpath. Each server exits zero with no ERROR/FATAL lines. This revalidates packaging and the tank scenarios; the new conduit cases run in the native development GameTest profile and are not represented as packaged conduit/player tests.

Evidence under `build/port-evidence/`, prefixed `cycle-037-`: `fluid-reproduction.log` and `.xml`, `fluid-fixed.log`, `fluid-filters.log`, `build.log`, `packaged-final.log`, `test-results.json`, `final-test-results/`, `artifacts.json` and the commit scout. The native packaged installation, logs, world and `smoke-results.json` are in `build/port-runtime/cycle-037-final`. Test processes were stopped and their handles closed. All runs remained headless and silent.

## Candidate artifacts

Version `8.2.14.36-1.20.1+9cdd47d46b` identifies the pre-cycle base. Own classes remain Java 17 major 61: 251 core, 1,260 main and 69 addon. Recursive jar audit excludes gameplay/registry tests, JUnit, test recipe namespaces, the test infiniburn tag and the large-rate conduit definition.

| Artifact under its module's `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.36-1.20.1+9cdd47d46b.jar` | `1f0a4a43fdc6cb3a03f9d3daa5d134d887ca424a34009b71a8abefb6500df576` |
| `enderio-8.2.14.36-1.20.1+9cdd47d46b.jar` | `e475b311bab452e98a18401b805f8674ee502039cad146801a805c1112f09bae` |
| `enderio-modded-conduits-8.2.14.36-1.20.1+9cdd47d46b.jar` | `2180685e7ebb286196873dd442e865852401fcc69e133779f432865548090e8e` |

## Remaining work and next step

The fluid-rate audit is now covered by native behavior, but this is not complete conduit conservation. Chemical transfer still discards an execution-time insertion remainder after extracting from the source. Inspection of the actual pinned Forge `FluidUtil.tryFluidTransfer_Internal` shows the analogous fluid path replaces the drained amount with the amount accepted, without preserving a rejected remainder. These cases need reproduction and a durable conservation design; simply reinserting into an output-only source is insufficient. Node persistence, topology changes, conduit removal and item drops must be considered if buffering is introduced. This cycle does not claim those risks fixed.

No client, graphical interaction, actual multiplayer or historical-save migration has been tested. Optional integration parity, broader lifecycle/persistence and timestamped-manifest reproducibility remain incomplete. Datagen remains at cycle 033's verified output; no production data changed here. Next cycle should establish and directly verify the silent, non-activating client setup and begin actual client/runtime validation, while retaining the transfer-remainder and lifecycle work as mandatory unresolved tasks. The full port goal remains active.
