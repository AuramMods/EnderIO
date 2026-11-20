# Cycle 253 — Experience Rod and SAG Mill particle gameplay

The guarded survival client uses the stock Experience Rod against a standard tank over dedicated TCP. The fixture observes the live spell particle sent by the server, checks its green color and animated sprite, and follows it as it rises from the tank. The same rod transfers XP with Minimal particles selected, shows the effect again with All selected, and produces no effect when the tank contains less than one point.

The server feeds four stone blocks into an idle SAG Mill through a vanilla hopper and gives the mill exactly 9,600 FE. While the stock recipe runs, the client observes its rising purple-gray dust and terrain debris at the front face. After lingering particles expire, the client checks both queues while Minimal is selected and the server confirms that the mill is still powered. Restoring All brings both effects back. The fixture checks the completed mill, the exact hopper output and the saved tank remainder after dedicated restart.

## Commands

```sh
source build/cycle-177-pins.zsh
port_jar=build/port-evidence/cycle-251-distribution/jars/enderio/enderio-8.2.14.250-dev+fb31bbe8bc.jar
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=2 --console=plain
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_name=cycle-253-gameplay-final
port_profile="$port_runtime/$port_name-client"
port_seed="$port_runtime/cycle-252-obsidian-client"
mkdir -p "$port_profile/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_profile/"
cp "$port_seed/config/fml.toml" "$port_profile/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -Bu scripts/porting/packaged_gameplay_particles.py --name "$port_name" --port 26367 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_profile/launch.json"
```

## Results

- The updated client QA jar compiles and remaps separately. The fixture uses the production jar preserved from cycle 251 with SHA-256 `a75142885db0e9038d954a755ce1fbbe904c4c45c41725d5ea745e061ad2c0fd`. See `build/cycle-253-particle-qa-final.log`.
- The first run verifies the three withdrawals and the effect under both particle settings. It times out at the rejected use because the fixture waits for a clear screen while Forge opens the native tank menu. The updated fixture detects that menu, closes it and verifies the rejected transfer through the real client and server. See `build/port-evidence/cycle-253-gameplay-particles-client/summary.json`.
- The first retry disconnects during loopback login. The final run reports native disconnect details if one occurs. It connects on a fresh isolated profile and proceeds through both effects. See `build/port-evidence/cycle-253-gameplay-verified-client/summary.json`.
- The final profile passes 28 client checks and nine server assertions. The three rod withdrawals deliver seven, three and three XP. Each leaves 19 mB. All displays a rising green spell particle on both observed uses. Minimal and the rejected use leave the effect queue clear for 42 ticks. The rejected use opens the actual tank menu and leaves the saved total at 13 XP. See `build/port-evidence/cycle-253-gameplay-final-client/summary.json`.
- The active mill renders both native particle types at its front. The tracked dust rises by about 0.185 blocks initially and 0.241 blocks after restoring All. Minimal suppresses both particles for the 30-tick sample while the server confirms active processing. The idle and completed mill stay clear. Exactly four cobblestone reach the output hopper for 9,600 FE, and the hopper contents and 19 mB tank balance survive restart. Both servers exit cleanly with zero runtime or registry errors. See `build/port-runtime/cycle-253-gameplay-final/gameplay-particle-results.json`.
- All five captures confirm zero sound volumes, narrator off and an uncaptured cursor. The native observer records zero activation, foreground or onscreen events across 3,831 samples. All captures and both server stages use the matching production jar. The QA classes live in their separate archive.

## Next verification

Check the outstanding AE2 guide UI behavior in the pinned optional profile and record GuideME's role alongside the already verified Ender IO conduit routing.
