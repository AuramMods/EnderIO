# Cycle 076 — Specialized Attractor mobs

## Scope

Verify Ghast pulls, Phantom and Slime targeting, and the default and opt-in Wither behavior on Forge. Release the temporary AI control on redstone, range, filter and lifecycle changes.

## Changes

- Give each controlled mob one Attractor owner in its server level. An overlapping machine can acquire the mob after the first releases it.
- Clear the fake target or restore a valid previous target on release. Keep a target that another controller has since assigned. Release targets on power loss, filter or range changes, block removal, chunk unload and level changes.
- Use removable goals to suppress Wither look and target behavior while the boss setting is enabled. Removing the Attractor's goals leaves the mob's existing control flags intact.
- Exercise collisions, target restoration, overlapping machines and the boss setting in four Forge GameTests. A read-only packaged server probe records actual entity positions, motion and targets. It is installed from the separate QA jar.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.75-1.20.1+3f16501b4c.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
python3 scripts/porting/packaged_attractor.py --name cycle-076-verified --port 25788 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --qa-jar "$port_qa"
```

## Results

- The original 129-test gameplay run reproduced stale Phantom and Slime targets after redstone deactivation and permanently suppressed Wither look and target behavior after disabling the boss option. The Ghast velocity and collision checks passed. See `build/cycle-076-reproduction.log` and the initial gameplay XML. The first fixed gameplay run passed all 129 tests before adding the overlap regression. See `build/cycle-076-fixed-gameplay.log`.
- The first packaged fixture used a Ghast with `NoAI` set. It received the one-block-per-tick pull velocity, and its position stayed fixed because that entity flag disables native flight travel. The final fixture uses normal mob AI and tracks per-entity tick counts alongside velocity and position. Separate runs for Phantom and Slime test redstone shutoff while each is still responding to its decoy. See `build/cycle-076-first.log` and `build/port-runtime/cycle-076-first/smoke-default.log`.
- The full build passed 79 unit tests and 452 required Forge GameTests with zero failures or skips. The native profiles ran 171 core, seven Mekanism, 117 registry, 130 gameplay and 27 addon cases. See `build/cycle-076-final-build.log` and the module test XML.
- The packaged Forge 47.4.28 run passed nine assertions. The Ghast's distance fell from 10.81 to 5.04 blocks under the one-block-per-tick pull. Phantom and Slime approached their decoy by 1.91 and 1.59 blocks respectively. Each lost the fake target after its own redstone shutoff. With the default boss setting, the Wither's horizontal position stayed at X 2235.50. After saving, enabling the setting and restarting, the Wither moved to X 2228.92 under the 2.25-block-per-tick pull. Both headless servers saved and exited with code 0 and zero runtime or registry errors. See `build/port-runtime/cycle-076-verified/attractor-results.json` and the two server logs.
- The observed release jar has SHA-256 `d08fa0c4fa7156ed06cdd1beee580810d7cd166ef4cd55f81ddf2d06cfdbd926`. Its classes target Java 17, and the observer is in the separate test jar. This cycle used headless Forge servers.

## Remaining verification

Audit carried state for the remaining obelisks and other supported targeting integrations. The boss fixture covers the built-in Wither. Mod-added bosses and alternative flight or target behavior need separate coverage.
