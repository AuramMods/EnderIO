# Registry-dependent tests on Forge 1.20.1

Run `./gradlew :enderio:check` for CPU JUnit tests and the registry suite, or `./gradlew :enderio:runRegistryTestServer` for the registry suite alone. `./gradlew build` includes both.

The registry suite loads the actual Ender IO mod in an isolated headless Forge server under `enderio/run/registry-test-server`. It never launches a client. Test data is scoped to that profile; it must not be installed in a normal game. Reports are written to `enderio/build/test-results/registryTestServer/results.xml`.

`resources/registry-tests.txt` lists the test classes. Each JUnit-discovered test becomes a required native GameTest, and the actual Jupiter engine runs it on the server thread. Existing `@BeforeEach`, assertion and test-instance semantics are preserved. Use `@ExtendWith(ForgeServerExtension.class)` to inject a `MinecraftServer` parameter. Add new registry test classes to the inventory. Keep tests synchronous and isolated; world/tick-dependent scenarios belong in the separate gameplay GameTest suite.

JUnit libraries are packaged as a test-only Forge game library because ordinary JVM classpaths do not provide Forge's transformed Minecraft classes. No fake Minecraft/Forge bootstrap is used, and neither this source set nor its library jar is included in shipped artifacts.

At cycle 034, this profile preserves 112 existing cases in 20 classes. The other 16 main-module JUnit cases remain in `src/test`. The separate older `src/gametest` suite is still awaiting migration; this profile does not replace its gameplay coverage.
