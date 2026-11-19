# Cycle 139 — Read and write items through storage conduits

The packaged Forge migration scenario now mounts three native AE2 item cells and one native Refined Storage disk. It runs storage operations at the powered end of each route and independently checks the physical cell or disk on the other side of the chunk boundary. The AE2 routes use normal, dense and mixed conduits. Each route stores iron ingots carrying its own NBT marker, and a request with the wrong marker returns nothing. The QA probe uses native storage APIs on both releases and stays outside the distributed jar.

## Commands

```sh
./gradlew build --continue --console=plain > build/cycle-139-storage-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.137-1.20.1+3020a44475.jar
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
port_mod_cache="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth"
port_ae="$port_mod_cache/ae2/QRpVsqDI/4e81984691e35c729880a11f72deb52dcc74d41f/ae2-QRpVsqDI.jar"
port_guide="$port_mod_cache/guideme/i7Tp1AHw/3d432fc99cefcfd17a12635d2d220f09a069f1c7/guideme-i7Tp1AHw.jar"
port_rs="$port_mod_cache/refined-storage/ZITLFjjf/14bf4de4975f6310d22a6ed06c6a7e1e0805641/refined-storage-ZITLFjjf.jar"
python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --storage-probe --mod "$port_ae" --mod "$port_rs" --output build/port-runtime/cycle-139-storage-probe-verified
python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-139-storage-verified --port 26007 --java "$port_java" --forge-server "$port_forge" \
  --legacy-jar "$port_legacy" --jar "$port_jar" --conduit-fixture build/port-runtime/cycle-053-fixture/fixture.jar \
  --placed-storage-conduits --mod "$port_ae" --mod "$port_guide" --mod "$port_rs" \
  --mod build/port-runtime/cycle-139-storage-probe-verified/storage-probe.jar \
  > build/cycle-139-storage-verified.log 2>&1
```

The Forge 6 fixture builder and pinned dependencies are documented in [cycle 053](cycle-053.md). The first run reached both native addon networks and stopped when the RS drive rejected a freshly constructed disk. The fixture now invokes the disk's normal player inventory tick to create its persistent ID before inserting it into the drive. The original result is in `build/port-runtime/cycle-139-storage-io/legacy-migration-results.json`.

## Results

- The build passes 79 unit cases and all 512 native runs across five profiles, including all 190 main gameplay cases and 27 addon cases.
- The packaged Forge 47.4.28 run passes 100 assertions, including 11 new storage checks. The original 6.2.18 beta writes 41 tagged ingots to each of the four routes. Conversion and eviction of both chunks preserve each exact total and tag. A write through the converted routes adds seven to each cell or disk. After a dedicated restart, each route returns 11 tagged ingots from the restored inventory of 48 and retains exactly 37. All three servers exit normally with zero runtime or registry errors.
- The tested bundled artifact has SHA-256 `b7b0fd8dd933c1d4b85b412a856164eed83def6d835eedd34902b3ee809b7573`. Results, dependency hashes, commands and stage logs are recorded in `build/port-runtime/cycle-139-storage-verified/legacy-migration-results.json`.

## Next verification

Verify channel limits on powered ME routes and exercise native client terminals over dedicated multiplayer. The current checks establish item operations through the conduit network and on-disk retention for the tested routes.
