# Cycle 140 — Verify live ME channel allocation

A native Forge GameTest builds a powered ME Controller with a single path to 33 real ME Drives. Each segment is a placed Ender IO conduit. The test reads the trunk capacity and usage from AE2's live nodes and counts all attached and active drives. It uses the default cable capacities documented by the [AE2 1.20.1 guide](https://guide.appliedenergistics.org/1.20.1/items-blocks-machines/cables).

Two normal trunk segments each report eight used channels. Replacing the first segment with a dense conduit leaves eight drives online while the second segment remains normal. Replacing the second segment brings 32 drives online. The native controller activates the waiting drive when the test removes an active one. Both segments still carry 32 channels.

## Commands and results

```sh
./gradlew :enderio-modded-conduits:runGameTestServer --console=plain > build/cycle-140-me-channels-native-oriented.log 2>&1
./gradlew build --continue --console=plain > build/cycle-140-me-channels-build.log 2>&1
```

- The first fixture connected 25 drives and observed eight active. AE2 drive fronts exclude network connections. Rotating the south row to face away from the conduit connects all 33 drives. The first runtime result is saved in `build/cycle-140-me-channels-initial.xml`.
- All 28 addon GameTests pass with the corrected orientation, including `merouteusesthenarrowesttierandreassignsavailablechannels`. The full build passes 79 unit cases and 513 native runs across five profiles, including all 190 main gameplay cases. The addon report is `enderio-modded-conduits/build/test-results/gameTestServer/results.xml`.

## Next verification

Exercise the AE2 terminal and Refined Storage grid from a guarded packaged multiplayer client, with independent counts from the remote native drive. [Cycle 139](cycle-139.md) covers native item operations across migration and restart.
