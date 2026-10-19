# Renderer Benchmark

A client-side Minecraft Fabric mod for 26.3. One click finds out whether **Vulkan or OpenGL** runs Minecraft
better on your PC, and keeps the winner if you want.

## How it works

1. Open **Video Settings → Compare APIs...** (or the mod's page in Mod Menu) and press **Compare Vulkan and OpenGL**.
2. The mod creates a temporary world with a fixed seed and flies the camera along the same 75 second loop over it,
   after a short warm-up. Every frame is timed. By default it flies the loop 3 times.
3. It switches the Graphics API option to the other renderer and asks you to restart the game, since the
   renderer can only change at startup.
4. After the restart it asks whether to continue, flies the same loop on the other renderer, and shows the results.

While flying, both renderers use the same settings: your render and simulation distance, FOV 70, no frame cap and
no VSync, since a cap would hide the difference. Your own settings are put back after each half. The temporary
world is deleted afterwards.

## Results

- A plain answer, such as **"Vulkan is 18% faster, with fewer stutters"**. Under 3% difference counts as a tie,
  since two runs on the same renderer can differ that much.
- Average FPS, **1% and 0.1% low FPS** (the average of the slowest frames, which is the stutter you feel)
  and the number of frames over 50 ms, side by side for both renderers. Each number is the median of the runs.
- The GPU and driver, and the settings used. A warning if the window size changed between the two halves.
- **Use the winner** or **keep your own setting**. Closing the screen keeps your setting.
- **Copy as Text**: a short table for Discord, Reddit or a bug report.
- **Save Image**: saves the results screen to the `screenshots` folder.
- **Last Result** reopens the most recent comparison.

## Things to know

- Keep the window in front and don't open menus while it flies. If you do, the current run starts over.
  Leaving the world cancels the comparison and puts your settings back.
- If Vulkan can't run on your PC, the screen says why and there is nothing to compare.
- If the game doesn't start on the other renderer after the restart (it fell back to OpenGL, or a launcher
  option forces the graphics API), the mod says so and cancels.
- If the game closes in the middle of a flight, the comparison is cancelled on the next start and your
  settings are put back.
- Sodium and other performance mods affect both renderers; the result notes when Sodium is installed.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5 or newer for Minecraft 26.3.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3.
3. Put the mod jar in your `mods` folder.
4. (Optional) Install [Mod Menu](https://modrinth.com/mod/modmenu).

## Configuration

Runs per renderer can be set on the mod's screen. Everything is in `.minecraft/config/renderer-benchmark.json`:

```json
{
  "runsPerRenderer": 3,
  "warmupSeconds": 15
}
```

A comparison in progress is kept in `config/renderer-benchmark/pending.json` and the last result in
`config/renderer-benchmark/last-result.json`.

## Building from source

Any JDK 17+ starts the Gradle wrapper. Minecraft 26.3 needs Java 25, which Gradle downloads automatically.

```bash
./gradlew build                     # jar in build/libs/, also runs the unit tests
./gradlew runClient                 # dev client with Mod Menu
./gradlew runClient -PwithSodium    # ...plus Sodium
./gradlew runClientGameTest         # in-game tests (src/gametest)
```

The metrics, verdict and flight route (`core` package) have no Minecraft dependencies and are covered by plain
unit tests in `src/test`. The client game test runs a whole comparison with short runs (the second half on the
same renderer, since a test can't restart the game) and checks the results, the stored state and that your
settings come back.
