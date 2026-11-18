# Cycle 065 — verify bank replanting and preserve Athena overlays

The fixture charges a four-node array to 7,000,000 FE in a clean, packaged Forge world, removes its source and sets two sides on the basic bank bridging the array. A survival player mines that bridge with a normal diamond pickaxe interaction, picks up the actual drop and places it back over TCP. Server commands stage the player near the dropped item and the placement target. The pickup and placement both use vanilla gameplay paths.

The server verifies the source, drop and player inventory before placement, plus the bank and its exact graph after placement. The restart verifies both side settings and the full balance before an adjacent wired charger receives energy. The guarded client's model probe reads the exposed south face of the neighboring basic bank during the break and replant. This face can see the same-tier seam while remaining visible in both states.

Athena 3.1.2 bakes its own complete blockstate model when the optional pack is active. That bypasses the Forge composite child that draws the bank's I/O overlay. The client now wraps Athena's bank models after baking and adds the existing cutout overlay using the bank's synchronized model data. The probe checks for the actual disabled and pull sprites in the cutout layer alongside the connected faces in the solid layer.

## Reproduction

Use the [macOS hidden-client harness](client-harness.md) and fresh isolated profile names. The official Forge 47.4.28 libraries used here were installed in `cycle-047-legacy-discover`. The client profiles use copies of the installed official `libraries` and `versions` directories from `cycle-063-client`.

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.64-1.20.1+77d64eb3bb.jar
port_athena="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/athena-ctm/DULOQFj7/2acee24454af859988a691f0506271a465f68f7b/athena-ctm-DULOQFj7.jar"
for profile in cycle-065-client cycle-065-athena-client; do
  mkdir "build/port-runtime/$profile"
  cp -R build/port-runtime/cycle-063-client/libraries \
    build/port-runtime/cycle-063-client/versions "build/port-runtime/$profile/"
done
python3 scripts/porting/client/prepare_packaged.py --name cycle-065-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/prepare_packaged.py --name cycle-065-athena-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_athena"
printf '%s\n' 'resourcePacks:["mod/enderio:resourcepacks/athena"]' >> build/port-runtime/cycle-065-athena-client/options.txt
python3 scripts/porting/packaged_capacitor_banks.py --name cycle-065-core-overlay --port 25727 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --carry-launch build/port-runtime/cycle-065-client/launch.json
python3 scripts/porting/packaged_capacitor_banks.py --name cycle-065-athena-overlay --port 25726 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --carry-launch build/port-runtime/cycle-065-athena-client/launch.json
```

## Results

The first guarded run checked the TCP carry sequence before the model probe was added. It passed 19 server assertions and five client checks. The capture was 960×540, all ten sound categories were zero, and the narrator was off. The focus watcher recorded 1,582 samples with zero activation, foreground or visible-window events. The evidence is in `build/port-runtime/cycle-065-survival/capacitor-bank-results.json` and `build/port-evidence/cycle-065-survival-carry/`.

The first Athena probe expected every face quadrant to use a connected sprite. The actual model uses two ordinary quadrants on the outside edge and two connected quadrants next to the matching neighbor. Breaking the bridge exposed four ordinary quadrants. The repeated check uses these exact sets and verifies that the original shape returns after replanting. See `build/port-evidence/cycle-065-athena-textures-carry/result.json` for the initial observation.

The first successful Athena seam capture lacked the blue pull marker that was visible on the same replant in the plain Forge profile. Both captures represented a block whose client-side I/O settings had synchronized. Compare `build/port-evidence/cycle-065-athena-verified-carry/capacitor-bank-replaced.png` and `build/port-evidence/cycle-065-core-verified-carry/capacitor-bank-replaced.png`.

The fixed production jar passed 19 server assertions in each of the plain and Athena profiles. The dropped bridge held 500,000 FE, the two fragments held 500,000 and 6,000,000 FE, and replanting restored 7,000,000 FE. The two side settings persisted through restart. The array then supplied 128,000 FE to a wired charger, retaining 6,872,000 FE. All four server processes exited zero with no ERROR or FATAL lines. See `build/port-runtime/cycle-065-core-overlay/capacitor-bank-results.json` and `build/port-runtime/cycle-065-athena-overlay/capacitor-bank-results.json`.

The plain client passed 11 checks and the Athena client passed 12. Both produced the disabled and pull sprites in the cutout layer, and the visible south pull marker appears in the fixed Athena capture. All ten sound categories were muted, narrator was OFF, and the cursor stayed uncaptured. The two native focus reports recorded 1,635 and 2,108 samples respectively, with zero activation, foreground or visible-window events. Both clients exited zero with no ERROR or FATAL lines. See `build/port-evidence/cycle-065-core-overlay-carry/` and `build/port-evidence/cycle-065-athena-overlay-carry/`.

The refactored carry client still passed the solar panel scenario against the pre-overlay production jar, with 22 server assertions and five client checks. It restored the exact charge and Phantom soul after restart. See `build/port-runtime/cycle-065-solar-regression/solar-charging-results.json` and `build/port-evidence/cycle-065-solar-regression-carry/`.

The final build passed 78 unit tests and 430 Forge GameTests with zero failures or skips. See `build/cycle-065-overlay-build.log` and the modules' `build/test-results/test/` XML. The production jar contains the model wrapper and keeps the QA probe and Athena dependency external.

| Artifact | SHA-256 |
| --- | --- |
| Production jar | `4a26e802727d0cf5e8d02e9dca80ccffcadb88042b448937ced6c89de604e184` |
| Test-only client probe | `ebe17becba578aeccce1822ec7f114db8cc93322b6ae55e712cdcddaa843ad1f` |
| Athena Forge 3.1.2 | `64eec9cffb8847a459536096ac396b135ffb07c7d9d13c6a47d9cd7b251276ab` |

## Remaining verification

The verified Athena seam is the basic bank's horizontal same-tier connection. Other layouts and renderer combinations remain open. The Soul Binder's packaged crafting, automation and menu also remain open, including producing the Phantom-bound panel through the machine.
