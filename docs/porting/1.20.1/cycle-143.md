# Cycle 143 — Conserve heat through equilibrium and reconnection

The source baseline and the historical Forge ticker compute heat from the temperature difference, the two conduction coefficients and the source capacity. A native resistive heater with 100 times the heat capacity of a basic thermodynamic conductor drove the conductor far past equilibrium. Both native Mekanism endpoints defer applying heat until their next update, so a branch also reused heat that an earlier branch had already taken during the same conduit tick.

Each edge now caps its requested heat at the weighted equilibrium of both endpoints. The ticker records projected temperature changes as it visits each edge and applies one net change to each handler when the network tick finishes. The original conduction rate still applies below the equilibrium limit. Each transfer debits and credits the same amount of heat.

## Commands

The variables `port_java`, `port_forge`, `port_legacy`, `port_art` and `port_mek` use the pinned paths from [cycle 054](cycle-054.md).

```sh
./gradlew :enderio-modded-conduits:runGameTestServer --console=plain > build/cycle-143-heat-before.log 2>&1
./gradlew build --continue --console=plain > build/cycle-143-heat-build.log 2>&1

port_before=enderio/build/libs/enderio-8.2.14.141-1.20.1+80d7f3796f.jar
port_fixed=enderio/build/libs/enderio-8.2.14.142-1.20.1+91337a2101.jar
port_world=build/port-runtime/cycle-142-controls-final/port-spawners
python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --mekanism-probe --mod "$port_mek" --output build/port-runtime/cycle-143-heat-probe
python3 scripts/porting/packaged_mekanism_heat.py --name cycle-143-heat-packaged-before --port 26016 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before" --world "$port_world" \
  --probe build/port-runtime/cycle-143-heat-probe/mekanism-probe.jar --mekanism "$port_mek" \
  > build/cycle-143-heat-packaged-before.log 2>&1
python3 scripts/porting/packaged_mekanism_heat.py --name cycle-143-heat-packaged-fixed --port 26017 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_fixed" --world "$port_world" \
  --probe build/port-runtime/cycle-143-heat-probe/mekanism-probe.jar --mekanism "$port_mek" \
  > build/cycle-143-heat-packaged-fixed.log 2>&1
```

## Results

- The baseline native run fails both new balance cases. An isolated tick drives a 300 K conductor to 3,800 K from a 650 K heater. The fixed tests verify about 646.53 K at both endpoints, preserve the total heat, and show that an additional tick leaves the pair at equilibrium. A second test checks the projected source balance with two destinations. The third test uses regular server ticks to verify heating, a physical split and native conduit item replacement. The original XML is `build/cycle-143-heat-before.xml`.
- The full build passes 79 unit cases and all 516 native runs, including 31 addon cases and 190 main gameplay cases.
- The packaged probe samples each native destination over 20 server ticks. The previous jar peaks at 3,789.62 K with a 100-to-1 capacity ratio. The fixed jar passes seven assertions. The connected route peaks at 645.51 K, the split route remains at 300 K, and the replacement route peaks at 646.12 K before and after a dedicated restart. All three headless servers exit normally with zero runtime or registry errors.
- The fixed production jar has SHA-256 `84a6b538e54221e74c0d6c901226d69d309709f73c25f0ce416f5c0b5afae238`. The probe is installed separately. Reports and server logs are under `build/port-runtime/cycle-143-heat-packaged-before` and `build/port-runtime/cycle-143-heat-packaged-fixed`.

## Next verification

Exercise the AE2 facade item on a powered Ender IO conduit route and verify its native rendering and persistence.
