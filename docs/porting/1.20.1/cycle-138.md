# Cycle 138 — Impulse Hopper multiplayer and survival placement

A guarded Forge client joined a dedicated server running the bundled port jar over loopback TCP. The server used a copy of the Forge 6.2 world from [cycle 137](cycle-137.md). The test exercised the shipped crafting and reset recipes, the actual Impulse Hopper screen, and ordinary placement of the archived Forge 6.2 drop. The client QA mod is remapped separately for the packaged test.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-138-impulse-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.137-1.20.1+3020a44475.jar
port_client=build/port-runtime/cycle-138-impulse-guarded-client
port_previous_client=build/port-runtime/cycle-138-impulse-menus-client
mkdir -p "$port_client/config"
cp -cR "$port_previous_client/libraries" "$port_previous_client/versions" "$port_client/"
sed 's/^earlyWindowControl = true$/earlyWindowControl = false/' \
  "$port_previous_client/config/fml.toml" > "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-138-impulse-guarded-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar "$port_jar" --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_impulse_menu.py --name cycle-138-impulse-guarded --port 26005 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar" \
  --launch "$port_client/launch.json" \
  --legacy-world build/port-runtime/cycle-137-impulse-legacy-verified/legacy/port-spawners \
  > build/cycle-138-impulse-guarded.log 2>&1
```

The first installed profile used the Forge splash and stopped at `glfwGetPrimaryMonitor` before Minecraft started. Its native observer recorded zero activations, foreground samples or on-screen windows before terminating the stalled process. The separate GLFW probe then rendered 100 frames with no focus or visibility events. The retry copied the installed Forge libraries and disabled the splash in the disposable client's `fml.toml`. The native guard and observer remained active. See `build/port-evidence/cycle-138-impulse-menus/summary.json` and `build/port-evidence/cycle-138-probe-after-launch-failure.json`.

## Results

- The build passed all 79 unit cases and 512 native runs across five Forge profiles, including all 190 discovered main gameplay cases.
- The packaged client passed 28 checks. The survival recipe consumes all nine ingredients for one clean hopper. The menu clears a template and copies two tagged ingots without consuming the cursor stack. The framebuffer shows both red indicators when the input is short and the output is blocked by a conflicting tag. The empty lane draws no status indicator. Shift-clicking supplies the matching inputs and recovers the conflicting stack. Both ready indicators turn green. Right-clicking the rendered south face sets it to Push. Scrolling the redstone control releases a two-ingot, one-redstone batch for exactly 30 FE and leaves one tagged ingot queued.
- The packaged servers passed 10 assertions. The shipped reset recipe clears a native carried drop with live charge, contents and settings. Ordinary survival placement consumes the archived stack and preserves its 980 FE, original sides, redstone mode, two templates, pending input, outputs and capacitor. A clean dedicated restart independently verifies those exact values, the edited machine and the reset item. Both servers exit normally with zero runtime or registry errors.
- Both ends loaded the production artifact with SHA-256 `b7b0fd8dd933c1d4b85b412a856164eed83def6d835eedd34902b3ee809b7573`. The guarded client exits normally with zero ERROR/FATAL lines and zero activation, foreground or on-screen samples across 1,447 native observations. The six 960×540 captures confirm the narrator is off and all ten volume categories are zero.

Client results and frames are in `build/port-evidence/cycle-138-impulse-guarded/`. Independent server assertions and exit reports are in `build/port-runtime/cycle-138-impulse-guarded/impulse-menu-results.json`.

## Next verification

Exercise actual storage insertion and extraction through the AE2 and Refined Storage conduit routes. [Cycle 053](cycle-053.md) established powered endpoints and restored network topology across Forge 6.2 conversion and restart.
