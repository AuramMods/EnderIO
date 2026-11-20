# Cycle 277 — Optional SAG Mill cards and grinding jobs

The dedicated server loads pinned Mekanism, Immersive Engineering and JEI with the packaged Ender IO runtime. The separate registry probe records the exact live input IDs, resolved output stacks, counts, chances, bonus modes and energy costs. The guarded JEI client compares its full SAG Mill catalog with that snapshot. The snapshot uses the server's resolved outputs, including recipes whose output tag can contain dust from several mods.

The survival player opens the cards for the active Mekanism and Immersive Engineering ingredients and transfers one marked item from each two-item stock. The native menu and server check the untouched surplus and the exact 600 FE partial payment. Each queued roll must follow the resolved quantities and chances shown in the matching JEI recipe. After restarting, each mill finishes its original roll and exports it to a hopper. A second restart checks the outputs and 137 FE reserve.

The Forge-only control runs the existing stock matrix with the same production archive and the updated registry probe.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:reobfClientSmokeJar

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=build/port-evidence/cycle-272-release/source-local/enderio/build/libs/enderio-8.2.14.271-dev+a00893d995.jar
port_art=$(rg --files --hidden "$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17" \
  | rg '/AutoRenamingTool-2\.0\.17-all\.jar$' | head -1)
port_probe="$port_runtime/cycle-277-sag-live-probe"
python3 -B scripts/porting/legacy_fixture/build.py --registry-probe \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --legacy-jar "$port_jar" \
  --art "$port_art" --output "$port_probe"
python3 -Bu scripts/porting/packaged_sag_stock.py --name cycle-277-sag-core-verified --port 26444 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe/registry-probe.jar"
zsh build/cycle-277-sag-metals-profile.zsh cycle-277-sag-metals 26443
```

The archived launcher prepares the guarded client and runs `scripts/porting/packaged_sag_metals_jei.py` with JEI `15.56.0.205`, Mekanism `10.4.16.80` and Immersive Engineering `10.2.0-183`.

## Results

- The first probe called Minecraft's inherited recipe ID method through the concrete class and hit a Forge runtime remapping error. The fixed probe uses the Minecraft recipe and serialization interfaces for those methods. Its SHA-256 is `88c971932c2fc1ddc8d64a4411e1c19698ac16ef7e025dd36c20fad21a675773`. The Java 17 QA build also passes and produces SHA-256 `1d92804882e4a295dee27704813c7f9d0a484cb675f60a13dc006c8536c20abd`. See `build/port-evidence/cycle-277-sag-metals-jei/{probe-build.log,probe-rebuild.log,qa-build.log}` and `build/port-runtime/cycle-277-sag-core/smoke-fresh.log`.
- JEI matches all 86 live recipe IDs, including 15 that activate in the optional profile. All 30 focused cards and transfers pass, covering 16 Mekanism inputs and 14 Immersive Engineering inputs. The native jobs keep all original rolls through restart, spend 72,000 FE total, and export the exact recipes' products. The selected aluminum ore yields Immersive Engineering raw aluminum, the deepslate osmium ore yields Mekanism raw osmium, and the tin ingot produces Ender IO tin powder.
- The optional profile passes 448 client checks and 516 server assertions. The Forge-only control runs all 139 inputs for its 71 active recipes and passes 1,134 assertions. All six dedicated sessions exit at zero with zero runtime or registry errors. Both profiles use production SHA-256 `58803ec07dc520e3da7ec4dc413ba04dc78db24b524aa20435e00f8d2cbf98bd`. The optional client and server load identical Ender IO, JEI, Mekanism and Immersive Engineering bytes.
- All six captures report narration off, all ten volume categories at zero and no visibility, focus, fullscreen or cursor capture. The native observer records 4,473 samples with zero activations, foreground observations or onscreen observations. See `build/port-evidence/cycle-277-sag-metals-client/`.
- The live catalogs and exact saved rolls are in `build/port-runtime/cycle-277-sag-metals/{registry-snapshot.json,sag-metals-jei-results.json}` and `build/port-runtime/cycle-277-sag-core-verified/{registry-snapshot.json,sag-stock-results.json}`.

## Next verification

Reconcile the feature-family and dependency matrices with the retained runtime evidence. Identify acceptance checks that still need to run against the delivery candidate. [Completion audit](completion-audit.md)
