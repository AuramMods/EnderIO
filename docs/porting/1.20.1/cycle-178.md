# Cycle 178 — Craft and edit a Location Printout

This test uses the shipped Coordinate Selector recipe and a clean Forge 47.4.28 installation with the packaged Ender IO jar. A survival player unlocks the recipe from its ingredients, places it through the native recipe book and collects one selector. A use with no paper produces no menu or printout. With two sheets in the offhand, the selector opens a menu for a block over eleven blocks away. Typing a label and clicking OK consumes one sheet and creates a printout with the exact block and Overworld dimension.

The printout editor is exercised from the Nether. The server verifies the Overworld location, custom label and remaining paper before and after a dedicated restart. A fresh client clears the label and reopens the same printout.

The menu encoder used the inverse of the server-side mode for a Location Printout. The selector and printout now use the same mode in the server menu and its Forge opening payload. The editor loads a printout's custom label when present, opens blank for unnamed printouts and attaches its listener after the initial value is displayed. The existing 50-character limit is shared between the opening packet, widget and incoming updates.

## Commands

Prepare a clean guard-enabled client as in [cycle 176](cycle-176.md) without optional mods. The exact local profile setup is recorded in `build/cycle-178-run.zsh`.

```sh
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-178-coordinate-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-178-client-clear-build.log 2>&1

zsh build/cycle-178-run.zsh cycle-178-coordinate-repro2 \
  build/port-runtime/cycle-177-tin-final/mods/enderio.jar 26140
zsh build/cycle-178-run.zsh cycle-178-coordinate-complete \
  enderio/build/libs/enderio-8.2.14.177-1.20.1+19519766d8.jar 26143
```

## Results

- The first packaged attempt verified crafting, the no-paper path and the exact Overworld printout. Its crouch emulation changed the player flag without waiting for the client pose. The scenario now drives the native key mapping and waits for the actual crouch state before interaction.
- The next attempt received the crafting output before the final grid slots arrived. The final scenario waits for the complete grid and verifies the stock ingredient arrangement before taking the selector.
- With those harness corrections, the previous jar reproducibly opens the named printout in the Nether with the right location and label, then fails the client menu validity check while the player holds that printout. See `build/port-evidence/cycle-178-coordinate-repro2-exercise-client/result.json`.
- The first corrected-jar attempt stopped waiting for the crafting screen. The test now spawns the client away from the crafting position and waits for the observed teleport before interacting. It also waits for the server to report the ingredient advancement and confirms the crafting result reaches inventory before closing the menu.
- The next attempt passed the Nether rename and the saved label check. Its immediate second crouch use after clearing the label did not reopen the menu. The final run checks the cleared item on the server before the second use and waits for both the client crouch input and pose.
- The full build passes 79 ordinary unit tests and 536 native runs with zero failures or skips. The native total includes all 209 discovered main gameplay cases.
- The final packaged jar passes 45 client checks and seven server assertions on two guarded clients and two dedicated server stages. The exact coordinates, dimension and one offhand paper survive crafting, renaming, restart and label clearing. Reopening the unnamed printout presents an empty editor and leaves the item unnamed. Both server stages exit with zero registry or runtime errors.
- The tested jar SHA-256 is `d0c608391565aeeb94db2cd92df424ef0fd5f7dd19242ed3584f507ecbbbc802`. The server and guarded client used the same bytes. See `build/port-runtime/cycle-178-coordinate-complete/coordinate-printout-results.json` and `build/port-evidence/cycle-178-coordinate-complete-{exercise,verify}-client/summary.json`.
- The independent focus observer records zero activations, foreground samples or on-screen windows across 3,262 observations. All capture volume categories are zero and the narrator is off.

## Remaining work

Forge 6.2 records the selection in its original item capability's top-level coordinate NBT. The current cycle covers the current save format. The historical location data needs a fixture from the Forge 6.2 jar and a migration check.
