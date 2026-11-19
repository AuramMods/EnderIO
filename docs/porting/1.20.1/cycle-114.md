# Cycle 114 — Migrate and verify the Staff of Levity

The Forge 6 Staff saves its reservoir as a root `Fluid` compound and its enabled flag as `ToggleState`. The existing Forge energy adapter already reads the historical `Energy` compound. Salvage the fluid amount and its full tag at item load and normalize the historical Vapor of Levity ID. Remove the decoded legacy fields so they cannot refill the item or re-enable it on a later load.

The staff now requires the full configured Forge Energy cost before it spends a tick's resources. The server owns toggling, resource use and the Levitation effect. The client receives the normal item and effect updates, including when its local common config differs from the dedicated server's config.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-113-final-client
port_jar=enderio/build/libs/enderio-8.2.14.113-1.20.1+7b2a799c8b.jar

run_legacy() {
  run_name=$1
  run_port=$2
  run_jar=$3
  shift 3
  python3 scripts/porting/packaged_levitation_staff.py --name "$run_name" --port "$run_port" --java "$port_java" \
    --forge-server "$port_forge" --legacy-jar "$port_forge/mods/enderio.jar" --jar "$run_jar" "$@"
}

run_legacy cycle-114-before 25917 "$port_prior/mods/enderio.jar"
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-114-final-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-114-qa-final.log 2>&1

port_client=build/port-runtime/cycle-114-fixed-client
mkdir -p "$port_client"
cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
python3 scripts/porting/client/prepare_packaged.py --name "$(basename "$port_client")" --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
run_legacy cycle-114-verified 25918 "$port_client/mods/enderio.jar" --launch "$port_client/launch.json"
```

## Results

- Forge 6.2.18-beta saves the two staffs with their original enabled and disabled flags, fuel tags and energy balances. On the previous port, the copied barrel retains those raw fields and the conversion assertion fails. See `build/port-runtime/cycle-114-before/legacy/smoke-legacy.log` and `build/port-runtime/cycle-114-before/port/smoke-converted.log`.
- The full Gradle build passes 79 unit cases and 483 Forge GameTest executions, including every one of the 161 discovered gameplay cases. See `build/cycle-114-final-build.log`. The final QA jar builds separately after the fixture adjustments.
- The first two packaged clients confirm both migrated Staff runs. The first tank fixture used byte counts for the machine's integer NBT. The second expected the partially filled staff in the output slot. An independent server reopen reads all 60 tagged mB in the input slot and an empty tank. The final client takes the partial fill from the native input slot. See `build/cycle-114-fixed.log`, `build/cycle-114-confirmed.log` and `build/cycle-114-tank-diagnosis.log`.
- The dedicated server charges 3 FE per tick. The client uses its default local 1 FE setting. An old active Staff lifts the player from the inventory and exhausts 18 mB, leaving 28 FE. A second saved Staff starts through crouch-use and stops with 2 FE and 20 tagged mB after 20 ticks. Those balances match on both ends.
- The tank retains 110 mB of water through two work cycles with the Staff in its input. It then transfers all 60 tagged mB of Vapor while the Staff keeps its 185 FE. The native tank menu removes the filled Staff exactly once. Standing use leaves it idle. Crouch-use applies Levitation and the client renders the effect and active foil. Both sides observe ascent. Exhausting the fuel leaves exactly 5 FE, and the client lands after the effect expires. See `build/port-runtime/cycle-114-verified/port/smoke-converted.log` and `build/port-evidence/cycle-114-verified-exercise-client/levitation-ascent.png`.
- The clean restart preserves every measured balance and the disabled state of all three Staff stacks. The packaged run passes 12 server assertions and 15 client checks. All three server runs exit at zero with zero runtime or registry errors. See `build/port-runtime/cycle-114-verified/levitation-staff-results.json`. The production artifact SHA-256 is `f23d028121ad490487363def868dc7f1e6a94edd4352a82b083455984e2eaa62`.
- The native observer records 3,381 samples across the two client sessions with zero activation, foreground or on-screen events. All three captures record zero volume in every category and the narrator off. See `build/port-evidence/cycle-114-verified-exercise-client/summary.json` and `build/port-evidence/cycle-114-verified-verify-client/summary.json`.

## Remaining verification

Crafting the Staff in survival, producing Vapor of Levity in the VAT, manual mid-flight cancellation and integrated-server use remain open.
