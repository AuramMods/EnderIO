# Clean packaged Forge server smoke test

This harness installs native Forge 47.4.28 in a new directory under `build/port-runtime`, copies only the main bundled Ender IO distributable into `mods`, and launches Java 17 with `nogui` and `java.awt.headless=true`. It does not use the Gradle run configurations, mapped development jars, source outputs or a test mod. Optional integrations are absent. EnderCore, modded conduits and GraphLib come from the main jar's nested dependencies.

Build the candidate with `./gradlew :enderio:jar :enderio:reobfJar`. Obtain the pinned installer from the [official Forge 1.20.1 download page](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html). The 47.4.28 installer SHA-1 published there is `585dd6071897becaed3b09bff4c3d9559e0cbc75`.

Run from the repository root, substituting the actual Java 17 executable and current artifact filename:

```sh
python3 scripts/porting/packaged_server_smoke.py \
  --java /path/to/java17/bin/java \
  --installer /path/to/forge-1.20.1-47.4.28-installer.jar \
  --jar /path/to/enderio-<version>.jar \
  --name packaged-smoke-unique-name
```

The name must be unused: the harness creates a new installation and never replaces an existing world. Its server binds only to `127.0.0.1:25636`; use `--port` to change this. EULA acceptance is preauthorized. Isolated options disable all sound categories, narrator, onboarding and fullscreen. No client or sound subsystem launches. The harness owns its subprocess handles and issues `stop` in a `finally` block; it can terminate only its own child if shutdown times out.

Three server sessions exercise actual console commands and ticked production block entities:

1. Place standard and pressurized tanks. Preserve 1,500 mB of water carrying a custom NBT value and seven diamonds in each persistence fixture. Process one water bucket in each separate gameplay fixture, requiring exactly 1,000 mB, one empty output bucket and no remaining filled input bucket.
2. Restart the saved world and require the same fluid, NBT and item states. Process concrete powder using the saved water through the packaged tank recipe. Reload datapacks and check the persistence fixtures again.
3. Restart again and require the saved concrete outputs, empty input/fluid state and unchanged persistence fixtures.

The harness reports **16 assertions**, process exit codes and server errors to `smoke-results.json`, alongside three console logs and installer output. Every assertion runs a native `execute if/unless` command and requires its actual success marker. Timeouts/failures preserve the logs and dump fixture NBT. A deliberately unfixed candidate in cycle 036 fails the bucket assertion after two successful persistence assertions, demonstrating that startup alone does not pass the harness.

This is a limited dedicated-server test. Console NBT seeds the fixtures; player interaction, GUIs, client synchronization, automation capability access and actual multiplayer are separate requirements. Passing these tank cases does not establish complete feature parity, historical save compatibility or every block's lifecycle behavior.
