# Cycle 177 — Run the optional integration stack together

The packaged server loads the pinned AE2, GuideME, Refined Storage, Mekanism, LaserIO, Patchouli, JEI, Jade, CC:Tweaked, Almost Unified, Inventory Sorter, FTB Ultimine, FTB Library, Architectury and Curios artifacts together. Client render profiles add Athena, Embeddium and Oculus. The [dependency record](dependencies.md) identifies the release pins. The installation and registry reports record the artifacts and digests actually used.

The first combined audit finds six stock Ender IO cooking recipe IDs removed by Almost Unified. Its duplicate dump names the retained Mekanism recipes. The audit now checks the original copper, gold and iron dust through both a real furnace and a blast furnace. It records a replacement only after the original powder is consumed, the exact vanilla ingot is produced and the machine's native `RecipesUsed` field identifies the retained recipe.

The separate tin test uses the packaged survival client, the actual JEI output search and its recipe transfer button. The server gives a stack of two explicitly tagged Mekanism tin ingots. The mill consumes each transferred ingot using 2,400 FE and retains 137 FE. Almost Unified prefers Mekanism tin dust initially, then a server datapack narrows the Ender IO recipe's output tag to Ender IO dust. Each pass checks the output shown by JEI, the tagged input in the live menu and the exact dust in the output hopper.

The first all-integrations client reaches the world, and a normal use packet for the SAG Mill crashes the dedicated server. The exported transformed `Player` class contains interface-method constants that refer to `Player.canReach` and `Player.canReachRaw` as interface calls. The Enderface reach mixin now computes Forge's same inclusive eye-to-block-center comparison from Forge's reach getters and keeps the remote Enderface allowance. The native test covers ordinary survival reach, supplied padding, the creative-only bonus and changed raw attributes.

The first run with safe reach showed that JEI used its local tag preference while the SAG Mill used the server's Almost Unified result. The SAG serializer now sends the resolved stack to the client and keeps the tag-backed output on the server. It uses the existing tag packet variant with a zero count when the server has no matching item, so the client keeps that result empty. The native packet regression covers available and unavailable tags, count, chance, optionality and fixed-stack NBT.

## Commands

The shell arrays contain the 15 shared artifacts and three render artifacts listed above. Recorded local paths are in `build/cycle-177-pins.zsh`. The guarded profiles are prepared as in [cycle 176](cycle-176.md), using the listed optional mods. The shared-only Enderface client uses the same guard without a shader pack. See `build/cycle-177-prepare-client.zsh` for the exact render profile preparation. The two earlier runs retained the exact jar used in their respective server profiles. Use fresh profile names when repeating a completed run.

```sh
source build/cycle-177-pins.zsh
port_jar=enderio/build/libs/enderio-8.2.14.176-1.20.1+daad82c66d.jar
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-177-tin-client.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-177-final-build.log 2>&1

python3 -u scripts/porting/packaged_registry_audit.py --name cycle-177-final-audit --port 26135 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  --experiments --aluminum-fixture --unified-furnaces "${port_mods[@]}"
python3 -u scripts/porting/packaged_unified_sag.py --name cycle-177-tin-final --port 26136 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  --launch "$port_runtime/cycle-177-tin-final-client/launch.json" "${port_mods[@]}"
python3 -u scripts/porting/packaged_facade_shader_passes.py --name cycle-177-verified-render --port 26132 \
  --java "$port_java" --forge-server "$port_forge" \
  --jar "$port_runtime/cycle-177-verified-render/mods/enderio.jar" \
  --launch "$port_runtime/cycle-177-verified-render-client/launch.json" "${port_mods[@]}"
python3 -u scripts/porting/packaged_enderface.py --name cycle-177-enderface-fixed --port 26134 \
  --java "$port_java" --forge-server "$port_forge" \
  --jar "$port_runtime/cycle-177-enderface-fixed/mods/enderio.jar" \
  --launch "$port_runtime/cycle-177-enderface-fixed-client/launch.json" "${port_mods[@]}"
```

## Results

- The corrected furnace fixture inserts the inputs through vanilla inventory operations so Minecraft initializes each cook duration. All six original Ender IO dusts cook through the exact recorded Mekanism recipes under Almost Unified's stock rules. The same audit passes its eight registry and aluminum assertions. See `build/port-runtime/cycle-177-verified-audit/registry-audit-results.json`.
- The first shader launch omitted the guarded profile's Forge early-window setting. The client stopped before login. The retry disables the early Forge window inside the isolated test profile, leaves Forge's automatic thread count enabled and passes 53 client checks and five server assertions across restart. See `build/port-runtime/cycle-177-verified-render/shader-pass-results.json`.
- The original tin test reproduces `IncompatibleClassChangeError` on the first normal use packet. The exact failure is in `build/port-runtime/cycle-177-tin-before/smoke-fresh.log`. The transformed method references are in `build/cycle-177-player-before.javap`.
- After the reach fix, the second tin job initially began consuming the first job's 137 FE remainder before the harness reset the energy budget. The saved world still contained the two expected dusts. The final harness zeros the mill's energy before accepting the second input so it can measure exactly 2,400 FE for each batch. See `build/cycle-177-tin-diagnostics.log`.
- The full build passes 79 ordinary unit tests and 536 native runs with zero failures or skips, including all 209 discovered gameplay cases. The native total includes 171 EnderCore, seven isolated Mekanism, 209 main gameplay, 117 main registry and 32 modded conduit cases.
- The final packaged jar has SHA-256 `fb51d3cba54bb6c6355f8efcb377a1f692334023d2ff9ad5c2d6ad67cd0eebc4`. The combined audit passes 15 server assertions, retains six proven cooking replacements, and reports no unresolved registry differences. See `build/port-runtime/cycle-177-final-audit/registry-audit-results.json`.
- The same jar passes 17 client and seven server assertions, including a dedicated restart. The client indexes and shows `mekanism:dust_tin` at login, then shows `enderio:powdered_tin` after the server reloads the tag. JEI transfers one tagged tin ingot in each case, and the outlet hopper collects the exact shown dust and keeps both through restart. The 15 shared optional jars match byte for byte between peers. See `build/port-runtime/cycle-177-tin-final/unified-sag-results.json` and `build/port-evidence/cycle-177-tin-final-client/summary.json`.
- The Enderface retest used the reach-only build with SHA-256 `a06c3694f351c7c140c643b388aa2c78c4ae306ea6493a733f47428bfad5f9e9`. It passes 29 client and eight server assertions over two sessions. The original remote chest stack, tank interaction and exact staff charges survive the shared integration stack. The fuller 53-check shader run used SHA-256 `4e14777bd9c01cfd4f572191736841119da448f39800a188835d2c1f5b0a2155`. The final tin run independently confirms the authored shader renders with the final jar.
- The guarded passing clients recorded zero activation, foreground and on-screen samples. The isolated client captures confirm muted volume categories and disabled narrator. The final tin session sampled 2,815 times.

## Remaining work

The combined installation verifies recipe integration, representative shader rendering and remote reach. The remaining feature variants and survival progression need their own gameplay evidence, along with the outstanding lifecycle and historical-save cases. Recheck the clean packaged profiles on the final release bytes.
