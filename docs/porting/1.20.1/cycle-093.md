# Cycle 093 — Verify Jade tooltips and mask painted conduits

## Scope

Exercise Jade 11.13.3 on packaged Forge while targeting a Soul Engine and an oak-plank-painted Energy Conduit. Check a live Blaze-to-Phantom soul change, an unbound engine, and the covered, wrench and restored views of the same conduit. Run Jade on both ends of the TCP session, on each side alone and with Jade absent.

## Changes

- Give Jade the facade's block state and display item while the conduit is covered. Remove the underlying block entity from the replacement accessor and supply empty server data so previously fetched conduit data cannot leak into the covered tooltip.
- Observe Jade's actual after-render callback from the separate client QA jar. Record overlay text, block identity, block entity access, soul payload and the rendered Bound Soul component without adding tooltip content.
- Check that native block picking still identifies the paint or the exact energy conduit as the player puts away or selects the wrench. Reopen the server and assert the new soul, exact energy balance and paint.

## Commands

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-093-reproduction-build.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-093-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-093-client-final.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.92-1.20.1+7473d5cb3d.jar
qa_jar=enderio/build/test-libraries/enderio-client-qa.jar
forge_server=build/port-runtime/cycle-047-legacy-discover
prior_dir=build/port-runtime/cycle-092-forge-client
jade="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/jade/xJQHCmWJ/d08e64e66521d69e083c8525f1fc507f1c202384/jade-xJQHCmWJ.jar"

for mode in both client-only absent; do
  profile="cycle-093-$mode-client"
  profile_dir="build/port-runtime/$profile"
  mkdir -p "$profile_dir"
  cp -R "$prior_dir/libraries" "$prior_dir/versions" "$profile_dir/"
  set --
  if [ "$mode" != absent ]; then set -- "$@" --mod "$jade"; fi
  python3 scripts/porting/client/prepare_packaged.py --name "$profile" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" --qa-jar "$qa_jar" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets" "$@"
  if [ "$mode" != absent ]; then
    mkdir -p "$profile_dir/config/jade"
    printf '%s\n' '{"general":{"enableTextToSpeech":false,"debug":true}}' > "$profile_dir/config/jade/jade.json"
  fi
done

# The before-client profile contains the original jar saved before the fix.
before=build/port-runtime/cycle-093-before-client
python3 scripts/porting/packaged_jade.py --name cycle-093-before-mask --profile both --port 25848 \
  --java "$port_java" --forge-server "$forge_server" --jar "$before/mods/enderio.jar" \
  --launch "$before/launch.json" --jade-jar "$jade"

port_number=25849
for mode in both client-only absent; do
  profile_dir="build/port-runtime/cycle-093-$mode-client"
  cp "$qa_jar" "$profile_dir/mods/enderio-client-qa.jar"
  python3 scripts/porting/packaged_jade.py --name "cycle-093-$mode" --profile "$mode" --port "$port_number" \
    --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
    --launch "$profile_dir/launch.json" --jade-jar "$jade"
  port_number=$((port_number + 1))
done
python3 scripts/porting/packaged_jade.py --name cycle-093-server-only --profile server-only --port 25852 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
  --launch build/port-runtime/cycle-093-absent-client/launch.json --jade-jar "$jade"
```

## Results

- Fixture setup required a complete soul payload with `entity_tag` and an unbound check for the absent entity ID inside its empty `EntityStorage` compound. The camera then used a fixed yaw and pitch so the real player ray hit the block at eye height. See the initial runs under `build/port-runtime/cycle-093-before*/`.
- The original jar drew “Oak Planks” alongside a pickaxe icon. Jade still saw the `enderio:conduit` block, its block entity and server data. The probe failed that exact covered-state assertion. The same run drew “Bound Soul: Blaze,” updated to “Bound Soul: Phantom” while targeting the same engine, and omitted the line on the unbound engine. See `build/port-evidence/cycle-093-before-mask-client/summary.json` and `painted-conduit.png` in the same directory.
- The build passed 79 unit and 482 native tests. Native profiles ran 171 core, seven Mekanism, 117 registry, 160 gameplay and 27 addon tests. The standalone reobfuscation of the final QA jar also passed. See `build/cycle-093-build.log` and `build/cycle-093-client-final.log`.
- The fixed profile with Jade on both ends passed 29 client checks. Jade displayed Oak Planks with the axe icon and received only the paint state. With the wrench it displayed Energy Conduit and received the conduit block entity and its server payload. Putting the wrench away restored the painted tooltip and discarded the conduit payload. The soul line changed from Blaze to Phantom on the same target. The 4.32 kFE display and capacitor item remained present, and the unbound engine omitted the soul line. See `build/port-evidence/cycle-093-both-client/`.
- The Jade-client-only profile passed 29 client checks. Local facade and wrench tooltips worked, and soul lines and server payloads were absent as expected. See `build/port-evidence/cycle-093-client-only-client/`.
- The server-only and absent profiles each passed 14 client checks. The non-Jade client kept its native soul state and picked the exact paint or Energy Conduit with the wrench. All four profiles passed five server assertions, including restart with the Phantom soul, 4,321 FE and the oak-plank paint. The eight server processes exited at 0 with empty runtime and registry error lists. See each `build/port-runtime/cycle-093-*/jade-results.json` and its matching client evidence directory.
- Each client captured six muted frames with narrator off. The native observer recorded zero activation, foreground or onscreen events across the 1,669 both-side, 1,783 client-only, 1,658 absent and 1,672 server-only samples. Jade text to speech was disabled in each Jade client profile.
- Production jar SHA-256 `e3b751fca092ad830b960dfc17471d810a5d61ba6b8e7f8f9605fd827d56e802`. Jade jar SHA-256 `a632c77975e5aedff6322b0072f8b0db3f5d4bf914df6bdcc87d1aea4d5f55f4`.

## Remaining verification

Jade's shared soul provider was exercised through the Soul Engine. The Powered Spawner, soul-bearing solar panels, and per-provider configuration changes still need separate overlay checks.
