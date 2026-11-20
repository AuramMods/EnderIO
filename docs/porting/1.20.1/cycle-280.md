# Cycle 280 — Match Auram's Forge and JEI versions

Auram's instance uses Minecraft 1.20.1 and Forge 47.4.10. Ender IO now compiles against that Forge version, and each bundled module declares it as its minimum. The packaged test launchers use the same Forge pin.

Auram ships JEI 15.20.0.129. The first isolated launch reached JEI's Forge plugin loader, which rejected the two Ender IO plugins because that JEI version lacks the newer subtype interface. The plugins now use the string subtype API shared by both versions. Souls match by entity type in recipe lookups and keep their entity data for ingredient identity. Conduit subtypes keep their registry IDs.

The client QA source set reads JEI 15.20's live recipe layouts and transfer buttons. Its SAG Mill reload check waits for the actual JEI lookup to update after the native recipe and tag packets arrive.

## Verification

- Temurin 17.0.18 runs `./gradlew build --continue :enderio:reobfClientSmokeJar` on Forge 47.4.10. After retargeting JEI, `./gradlew check --continue` passes all 79 unit cases, 548 native runs and 180 baseline method IDs. The main gameplay task executes and passes all 220 discovered cases. See `build/deploy-to-modpack/auram-47.4.10/{retarget-build.log,final-check.log,native-test-audit.json}`.
- A fresh packaged Forge 47.4.10 server passes sixteen assertions for tank transfers, datapack reload and save/restart. The packaged JEI 15.20.0.129 profile passes 22 guarded client checks and five dedicated assertions. It transfers a tagged ore from the native recipe viewer, displays the reloaded grinding ball and retains the processed output after restart. See `build/port-runtime/auram-47410-core-smoke/smoke-results.json` and `build/port-runtime/auram-47410-sag-jei-reload/sag-jei-results.json`.
- The passing JEI client captures show all ten volume categories at zero and narrator off. Its 1,974 native observations record zero activations, foreground samples or onscreen samples.
- The isolated Auram launch and the control without Ender IO both hit the same Evolved Mekanism exception in `EMItems.registerQIODrive`. The follow-up copy with that addon omitted reaches the title screen with Forge 47.4.10, Auram's JEI and Ender IO loaded. The full-pack test is separate from the focused compatibility checks. See `build/deploy-to-modpack/auram-47.4.10/control-focus.json.client.log` and the two isolated launch reports in that directory.

The Auram release uses the beta tag `v8.2.16-beta`. The earlier `v8.2.15-beta` tag identifies the Forge 47.4.28 build.
