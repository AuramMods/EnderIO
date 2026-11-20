# Cycle 274 — All stock silverfish Soul Binder recipes

The fixture checks stone, cobblestone, deepslate and the four stone-brick varieties against the fixed 1.21.1 catalog. The seven corresponding recipe definitions in the Forge jar have the same ingredient, silverfish rule, 10,000 FE cost, zero XP cost and result.

A dispenser captures a separate marked silverfish for each binder using a stock Soul Vial. Hoppers feed the captured vial and the marked host block through different faces. Every job starts with an empty XP tank and stops after spending 3,127 FE. The saved task contains the exact recipe ID, both original inputs, one queued infested block and one queued empty vial. The fixture adds 137 mB of XP Juice to the stone binder to check that its pending zero-XP job leaves an existing reserve intact.

The fixture removes the force-load tickets and observes all five chunks unload. It loads them again and checks the saved work before and after a dedicated restart. The next energy delivery takes each job to 9,999 FE. The final delivery supplies 138 FE, of which one FE completes the job and 137 FE remains. The output hopper receives the exact infested block and a single unbound vial. Another dedicated restart verifies the same outputs, balance and empty machine slots.

The wrong-host control uses a captured silverfish and marked dirt. That dirt stays in its original hopper. The wrong-soul control delivers a captured Endermite and marked stone into the machine but starts no recipe. Both controls retain all 10,000 FE. The first run expected the dirt in the machine. Reopening that world and reading the four native block entities identified the correct rejected-insertion behavior.

## Commands

```sh
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=build/port-evidence/cycle-272-release/source-local/enderio/build/libs/enderio-8.2.14.271-dev+a00893d995.jar
python3 -Bu scripts/porting/packaged_silverfish_binding.py --name cycle-274-silverfish-verified --port 26436 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar"
```

## Results

- The packaged run passes 73 assertions across three headless Forge sessions. All seven jobs start at zero XP, preserve their exact progress through chunk eviction and restart, and export seven infested blocks and seven empty Soul Vials. The jobs collectively spend 70,000 FE and zero XP Juice. The stone binder keeps all 137 mB of its later XP reserve. Both rejected-input controls keep their exact stacks and energy after the final restart.
- Every session exits at zero with zero runtime or registry errors. The installed runtime hashes to `58803ec07dc520e3da7ec4dc413ba04dc78db24b524aa20435e00f8d2cbf98bd`, the candidate built in [cycle 272](cycle-272.md).
- The complete report is in `build/port-runtime/cycle-274-silverfish-verified/silverfish-binding-results.json`. The original failed test, reopened inventory read and checked recipe catalog are in `build/port-evidence/cycle-274-silverfish-binding/{run.log,negative-controls.log,catalog.json}`.

## Next verification

Verify how the zero-XP silverfish recipes appear in the native Soul Binder and JEI menus, including the displayed level and energy requirements and JEI ingredient lookup for the silverfish vial. [Completion audit](completion-audit.md)
