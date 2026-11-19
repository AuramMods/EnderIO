# Cycle 150 — Convert and waterlog painted redstone blocks

Forge 6.2.18-beta saves a diamond-painted redstone block, a gold-painted wooden plate pressed by a dropped item, an emerald-painted powered gate between walls, and a powered, top-half lapis-painted trapdoor with source water. Its native loot tables put one original painted item of each kind in a barrel. The port opens a copy and checks exact paint, power, facing, trapdoor half and waterlogging before taking any actions on the original blocks.

The packaged client replants all four original items in survival. The trapdoor goes into an existing water source, then retains the source and paint through hand opening, hand closing and redstone power. A dedicated restart checks both rows before removing their signal sources. A separate dry run repeats the shipped recipe, hopper, survival and redstone checks.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-150-painted-redstone-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_old=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.149-1.20.1+a21b4b80b0.jar
port_client=build/port-runtime/cycle-150-painted-redstone-client
port_previous=build/port-runtime/cycle-149-painted-redstone-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-150-painted-redstone-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_painted_redstone.py --name cycle-150-painted-redstone-migration --port 26031 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_old" \
  --jar "$port_jar" --launch "$port_client/launch.json" > build/cycle-150-painted-redstone-migration.log 2>&1
python3 scripts/porting/packaged_painted_redstone.py --name cycle-150-painted-redstone-regression --port 26032 \
  --java "$port_java" --forge-server "$port_forge" \
  --jar "$port_jar" --launch "$port_client/launch.json" > build/cycle-150-painted-redstone-regression.log 2>&1
```

## Results

- The full build passes 79 unit cases and 519 native runs, including all 192 main gameplay cases. The remapped client QA jar builds successfully.
- The conversion passes 35 server assertions and 49 client checks. All four original placed blocks keep their paint and powered states. The gate retains its north-facing lowered shape and the original trapdoor retains its north-facing top half and source water. All four native Forge 6.2 loot stacks convert to typed paint and replant in survival. Native loot from the replants carries the same four paints.
- The survival player places the lapis trapdoor in an existing water source. The client confirms source water when placed, opened by hand, closed by hand and opened by redstone. The frames show the open shape and the flow from the replanting cell into the drain. Both rows retain their powers and paints through restart. Removing power closes both gates and trapdoors, releases both plates and leaves both trapdoors waterlogged.
- The dry control passes 27 server assertions and 44 client checks. All five server sessions and both clients exit normally with zero reported runtime or registry errors. Both packaged scenarios use the same production jar on the port servers and clients, with SHA-256 `cc5f4fa6a03c61b93ff571ed1654571bb05141e5da86316698ef932b56d35c95`. The Forge 6.2.18-beta jar has SHA-256 `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`.
- The observer records 3,471 native samples across both guarded clients with zero activation, foreground or visible-window events. All 16 frames have the narrator off and every sound category at zero.

The server reports are under `build/port-runtime/cycle-150-painted-redstone-migration` and `build/port-runtime/cycle-150-painted-redstone-regression`. Client reports and frames are in the corresponding directories ending in `-client` under `build/port-evidence`.

## Next verification

Exercise the shipped recipes and gameplay for painted stairs, walls, crafting tables and glowstone. Verify placement shapes, the crafting grid and light emission using the packaged mod.
