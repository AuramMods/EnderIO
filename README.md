# Ender IO for Forge 1.20.1

This branch is Auram’s Ender IO port for Minecraft 1.20.1, Forge 47.4.10, and Java 17.

## Build and release locally

Keep GitHub Actions disabled for `AuramMods/EnderIO`. Run all builds and tests on the local machine using the existing Gradle cache. Keep workflow files out of this branch.

```sh
./gradlew build --continue
```

The build runs the local unit tests and Forge GameTests and produces the reobfuscated runtime jar in `enderio/build/libs/`. For a release, build from the clean version tag, verify the runtime jar locally, and upload that exact jar to GitHub Releases.

See the [installation and verification guide](docs/porting/1.20.1/install.md) and the [CraftTweaker and GameStages guide](crafttweaker.md).
