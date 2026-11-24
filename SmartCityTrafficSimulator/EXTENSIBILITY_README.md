# Extensibility refactor — Smart City Traffic Simulator

This copy is derived from the uploaded `SmartCityTrafficSimulator(1).zip` source tree. It includes a verified refactor of (a) `ScenarioInstaller` and (b) the Visual Rule Builder's condition/action registration, plus an event added in an earlier verified patch. The original upload is unchanged.

## 1. How the new Rule Builder extension works

`ConditionOption` / `ActionOption` continue to contain all the original built-in enum entries. They now implement `ConditionChoice` / `ActionChoice`, which can also be implemented by registered options. `RuleDraft` carries these descriptors. The `RuleExtensionRegistry` holds **typed factories** for Vehicle, Pedestrian, TrafficLight, and Road. The Swing condition/action dropdowns and `RuleBuilder` receive the same registry.

To add a new supported condition or action:

1. Implement `Condition<Vehicle>` / `Action<Vehicle>` (or the appropriate other target type) in a new Java class.
2. Register a new option and typed factory in the **bootstrap** (see `DefaultRuleExtensions.create()`), before constructing the Swing panel.
3. The choice appears automatically in the relevant target category's drop-down. Existing `RuleBuilder` condition/action switch statements do NOT need to be edited. Its built-in switch branches remain for compatibility with original options.
4. The same `Rule<T>`, `RuleEngine.evaluateGroup()`, `RuleEffects`, and `RuleEffectApplier` pipeline continues to be used. New actions must collect only effect kinds already supported by `RuleEffects`; a *new kind of effect* would also need changes to effect resolution/application.

New working examples registered in `DefaultRuleExtensions`:

- `TrafficJamCondition` for `Vehicle`: based on immutable per-tick observations, matches when vehicle speed is <= 3 m/s **and** current-road vehicle count satisfies the selected comparison and threshold (e.g. >= 3).
- `AdaptiveWeatherSpeedAction` for `Vehicle`: requests a 0.50 speed fraction under rain, or 0.85 under dry conditions. It collects an effect; it does not set vehicle speed directly.

Both new choices are present in the regular **Rule Management Panel** (the two-argument constructor now uses `DefaultRuleExtensions.create()`).

### Example: adding another plugin at startup

```java
RuleExtensionRegistry registry = new RuleExtensionRegistry();
registry.registerVehicleCondition("MY_CONDITION", "My vehicle condition",
        ValueKind.PRESENCE, draft -> new MyVehicleCondition());
registry.registerVehicleAction("MY_ACTION", "My vehicle action",
        ValueKind.NONE, draft -> new MyVehicleAction());
RuleManagementPanel manager = new RuleManagementPanel(
        engine, city, () -> "USER-" + java.util.UUID.randomUUID(), registry);
```

`ValueKind` currently supports the original editor's input types (none, presence, numeric, weather, vehicle family, signal phase). A completely new value-editor widget type would require an explicit UI addition. Registration is developer-controlled; it is not execution of arbitrary source code entered by end users.

## 2. How the new scenario extension works

`ScenarioInstaller` no longer switches over `ScenarioPreset`. It is responsible for only the common operations:

- validate the scenario definition and required incident road,
- create/install a `TrafficSpawner` using the scenario's rate,
- call the supplied `ScenarioDefinition.Setup` strategy to schedule custom events.

`BuiltInScenarios` stores the recipes for `NORMAL_TRAFFIC`, `RUSH_HOUR`, `RAIN`, `ROADWORK`, `ACCIDENT`; the existing `ScenarioPreset` method signature is retained, and those presets still behave as before.

To implement a new programmatic scenario **without editing ScenarioInstaller**:

```java
ScenarioDefinition speedZone = new ScenarioDefinition(
        "SPEED_ZONE", 0.0, true,
        (simulation, map, targetRoad) -> simulation.scheduleEvent(
                new SpeedLimitChangeEvent("SPEED-ZONE-START", 2.0, targetRoad, 8.0)));
ScenarioInstaller.install(speedZone, engine, city, spawnRoutes, r1, 42L);
```

The above code is used and tested by `ExtensibilityRegistryVerification.java`.

**Current limit:** The desktop preset selector still uses the fixed `ScenarioPreset` enum, and some Stage 7 visualization/pedestrian settings still check those specific enum values. Therefore a new programmatic `ScenarioDefinition` does NOT appear in the existing GUI preset drop-down automatically. Adding completely dynamic desktop scenario selection would require separately refactoring `DesktopSession`, `Stage72ScenarioFactory`, and `MainWindow`. This patch fixes the `ScenarioInstaller` *execution extension point*, not the entire GUI preset-selection system.

## 3. Verification

Compiled all main and testing Java source files with OpenJDK 21 and `javac --release 21`. Maven was not installed in the verification environment, so NetBeans/Maven build was not executed here.

`com.smartcity.simulator.testing.ExtensibilityRegistryVerification` passed **19 assertions** including:

- plugin options listed and type-filtered,
- RuleBuilder compiles a typed Rule<Vehicle>,
- RuleEngine produces 0.50 (rain) and 0.85 (dry) speed fractions,
- injected Swing RuleBuilderPanel round-trips both options,
- **regular/default RuleManagementPanel** also exposes and registers both options,
- new `SPEED_ZONE` scenario installs without a ScenarioInstaller switch and changes car movement at the scheduled time.

Regression verification classes also passed:

`Stage43Verification`, `Stage44Verification` (25 checks), `Stage54RuleEffectsTest`, `Stage55IntegrationTest`, `Stage56FinalVerificationTest`, `Stage61CatalogueTest` (60), `Stage62RuleBuilderTest` (47), `Stage63RuleBuilderPanelTest` (46), `Stage64RuleLifecycleTest` (46), `Stage72TripCompletionVerification` (14), `Stage72Verification` (27), `Stage73Verification` (25), and `Stage74Verification` (36).

Run with Java 21 from the project root:

```bash
find src/main/java -name '*.java' > sources.txt
mkdir -p target/verified-classes
javac --release 21 -encoding UTF-8 -d target/verified-classes @sources.txt
java -Djava.awt.headless=true -cp target/verified-classes \
  com.smartcity.simulator.testing.ExtensibilityRegistryVerification
```

The normal GUI can be started through the project's existing NetBeans/Maven launch configuration. For UI screenshots, select target **Vehicle**, then condition **Traffic jam: speed <= 3 m/s and vehicle count** and action **Adaptive speed cap: rain 50%, dry 85%**.

## 4. Recommended code screenshots for the report

- `scenarios/ScenarioDefinition.java` — scenario extension contract
- `scenarios/ScenarioInstaller.java` — no event-type switch, delegates to `definition.setup().configure(...)`
- `testing/ExtensibilityRegistryVerification.java` — new scenario installed and verified
- `rules/builder/RuleExtensionRegistry.java` — typed extension registration
- `rules/builder/DefaultRuleExtensions.java` — new condition and action registered
- `rules/conditions/TrafficJamCondition.java` — new target-specific condition
- `rules/actions/AdaptiveWeatherSpeedAction.java` — new effect-collecting action
- `rules/builder/RuleBuilder.java` — registered factories used before original built-in switches

## 5. Merge caution

The patch is based on the exact source files in the uploaded ZIP. If your local NetBeans project has newer edits, compare/merge the changed files rather than overwriting them blindly.
