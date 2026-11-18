# Cycle 036: packaged Forge server and tank conservation

Starting commit: `5898f389b`. Destination: local `1.20.1`. The tree began clean, with no unmerged index entries or merge in progress. The historical remote ref and cycle-012 auto-stash/conflicted-tree recovery refs remain unchanged. No push, merge, stash reapplication or branch reset occurred. No on-disk manual-change protocol or CodeGraph index was present.

## Clean packaged runtime

The official Forge **1.20.1-47.4.28** installer was downloaded from the direct Maven link on the [Forge download page](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html). Its SHA-1 matches the published `585dd6071897becaed3b09bff4c3d9559e0cbc75`; SHA-256 is `d615397a6b2f1e99d4b454e2db2b4ca2e94be91d5e0243c8c7e023d965db39e7`. Installation uses actual Java 17 `--installServer` with headless AWT.

The new [packaged-server harness](packaged-server.md), `scripts/porting/packaged_server_smoke.py`, creates an isolated native Forge installation under ignored `build/port-runtime`. Its only top-level mod is the distributable main Ender IO jar. Forge loads its three nested dependencies; optional mods, test mods and the development classpath are absent. The server binds localhost, uses an isolated disposable world and `eula=true`, and runs with `nogui`/headless AWT. Muted/narrator-off options are seeded; no client or sound subsystem is launched.

Sixteen native console assertions span three server sessions and two disk-backed world restarts. Both tank variants preserve 1,500 mB of tagged water and seven stored diamonds. Separate tanks process one full bucket into exactly 1,000 mB plus one empty bucket, retain that state across restart, then process concrete powder using the saved water. A datapack reload succeeds; the final restart retains the concrete output and empty input/fluid state. All three sessions stop and save cleanly, with no server ERROR/FATAL lines. This is limited tank/world persistence evidence, not client interaction, automation-capability or multiplayer verification.

The harness compares actual `execute if/unless` results with success markers and fails when the expected behavior does not occur. It waits for a fresh resource-loading message after `reload`, not an earlier startup message. It always stops its owned subprocess. Input artifact bytes are captured once and used for both hashing and installation: the first successful run revealed that a concurrent Gradle build could replace the source jar during Forge installation. The original report is retained alongside `cycle-036-harness-digest-correction.json`; the final run uses the corrected snapshot behavior and final build artifact.

## Reproduced fluid duplication and fix

The first actual packaged server exposed a serious inherited bug in `InternalTankTasks.fillInternal`: it simulated draining a copied item, filled the tank, then kept the original filled container. A single water bucket eventually filled all 16,000 mB while remaining in the input. Inspection confirms this logic was also present in the fixed 1.21.1 source baseline; full operation still requires correcting it.

The helper now executes a bounded drain **on the copied single-item container**, limited to the destination tank's accepted amount. A bucket with less than 1,000 mB of available capacity returns no drain and changes neither side. A larger container keeps its remaining fluid/NBT. Only after verifying the drained fluid is accepted and the resulting item can be retained or inserted does the helper commit the tank and inventory changes. A blocked output discards the disposable copy and leaves the real input/tank unchanged. The same helper also serves the opt-in Niard; Niard-specific gameplay remains unverified.

Five new native scenarios each exercise both standard and pressurized tanks:

- Bucket emptying/refilling preserves exactly one bucket and 1,000 mB, including later ticks that previously duplicated fluid.
- Only 500 mB of free tank capacity leaves a full bucket untouched.
- An output occupied by diamonds prevents bucket/fluid consumption or duplication.
- An actual Ender IO tank item transfers 500 mB from 3,000 mB of tagged fluid into a nearly full tank, retaining 2,500 mB and matching native NBT.
- Three empty buckets with 3,000 mB drain only one bucket when its unstackable filled output blocks further processing, leaving two empty buckets and 2,000 mB.

Before the production fix, the corrected suite executed 67 cases and failed four new scenarios. The separate packaged harness failed its first bucket-processing assertion after two successful storage assertions. After the fix all 67 native cases and the packaged assertions pass. One preliminary test compile used the wrong `LazyOptional.orElseThrow` overload and was corrected; the tagged-fluid assertion was also corrected to compare against a tagged expected stack before the final reproduction. Both initial evidence and final reproduction are retained.

## Deterministic fire fixture

An initial expanded gameplay run also exposed an intermittent existing fire-crafting test failure. Native fire on granite/cobblestone can randomly burn out before the configured minimum age, invalidating the setup before its explicit extinguish step. The test-only datapack now adds those two recipe bases to the overworld infiniburn tag. Existing recipe timing, drops and block-change assertions remain. Cold-fire expiration still runs and passes, and production tags are unchanged. Recursive distribution inspection confirms the fixture tag is absent from release jars.

## Verification

Successful commands:

```sh
./gradlew :enderio:verifyGameplayTests :enderio:jar :enderio:reobfJar --console=plain
./gradlew check --continue --console=plain
./gradlew build --continue --console=plain
python3 scripts/porting/packaged_server_smoke.py \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.35-1.20.1+5898f389ba.jar \
  --name cycle-036-final
```

| Profile | Executed | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Core JUnit | 61 | 0 | 0 |
| Main CPU JUnit | 16 | 0 | 0 |
| Addon JUnit | 1 | 0 | 0 |
| Core native GameTests | 170 | 0 | 0 |
| Native Mekanism adapters | 7 | 0 | 0 |
| Main registry/Jupiter native profile | 112 | 0 | 0 |
| Main gameplay | 67 | 0 | 0 |
| Actual-mod addon GameTests | 18 | 0 | 0 |

Totals: **78 ordinary JUnit and 374 native GameTest executions**, without double-counting in-server Jupiter. Profile overlap is possible. All 58 enabled baseline gameplay cases remain, plus nine tank regressions from cycles 035–036. The packaged harness adds **16 actual state assertions over three dedicated-server processes**, separate from these suite totals.

Evidence under `build/port-evidence/`: `cycle-036-installer*.log`, initial `server-first`/`server-console` logs, `tank-reproduction*.log` and XML snapshots, `tank-fixed-package.log`, `check.log`, `build.log`, `packaged-reproduction.log`, `packaged-fixed-run.log`, `packaged-final.log`, `test-results.json`, `final-test-results/`, `artifacts.json` and `harness-digest-correction.json`, all prefixed `cycle-036-`. Full native installations/logs/worlds/results remain under `build/port-runtime/cycle-036-reproduction`, `cycle-036-fixed-run` and `cycle-036-final`.

Exploratory setup errors were confined to owned profiles: a wrong preparation working directory caused an initial empty Forge process to exit for missing EULA; the corrected profile accepted it. A noninteractive console launch could not accept stdin and was stopped by SIGINT after verifying its PID and working directory; its shutdown hook saved all worlds. An incomplete flat-world generator setting was corrected in the harness. A typo in a candidate jar filename failed before installation and was corrected. None of these were product defects. Final harness sessions use subprocess-owned stdin and `stop`, valid flat-world settings and the actual artifact path.

## Candidate artifacts and remaining work

Candidate version: `8.2.14.35-1.20.1+5898f389ba` (pre-cycle commit identifier). Own class counts remain 251 core, 1,260 main and 69 addon, all Java 17 major 61. Recursive audit finds no GameTest/registry-test classes, JUnit libraries, test recipes or fixture-only infiniburn tag in the main or nested jars.

| Artifact under its module's `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.35-1.20.1+5898f389ba.jar` | `1b950fc7e24d90009be357306f8cb8de508d9f473027d574120424685f5708d3` |
| `enderio-8.2.14.35-1.20.1+5898f389ba.jar` | `071ec6d60eff26d43e2322bd37f405ad03aad56818b87d29f9ef000f946382a2` |
| `enderio-modded-conduits-8.2.14.35-1.20.1+5898f389ba.jar` | `55d3aefbe5fdd5927c3898be74f1f2e06edf1d730b1f6ba2fbbd90082cd4a8d5` |

The port remains incomplete. No client, GUI/render, actual multiplayer or historical-world migration test has run. Complete feature/optional-integration parity, larger persistence scenarios, chemical execution-time insertion remainders, fluid conduit rate budgets and timestamped-manifest reproducibility remain unresolved. Datagen was not rerun for the transfer/test changes; cycle 033 remains its latest evidence. Next: address remaining conduit transfer accounting and broaden packaged integration/lifecycle coverage while establishing a directly verified non-activating client setup. Keep the full goal active.
