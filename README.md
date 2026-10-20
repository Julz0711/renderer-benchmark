# Renderer Benchmark

A client-side Minecraft Fabric mod for 26.3. Finds out whether **Vulkan or OpenGL** runs Minecraft better on
your PC, across plains, forests, mobs, rain, night, ocean and snow.

## How it works

Open **Video Settings → Benchmark...** (or the mod's page in Mod Menu). One screen shows:

- **Your setup**: the renderer the game runs on, your GPU and driver, and the settings that decide how fast it
  runs (render and simulation distance, FOV, window size, Sodium).
- **Switch to Vulkan / OpenGL**: changes the Graphics API option and closes the game, since the renderer can only
  change at startup. Start the game again and it runs on the other one.
- **Results**: the latest benchmark on each renderer side by side, scene by scene, with the difference and a
  plain answer such as "Vulkan is 18% faster overall, with fewer stutters".

**Run** flies the camera over a temporary world with a fixed seed through 7 scenes, about 20 seconds each,
while every frame is timed:

| Scene | Where and what |
| --- | --- |
| Plains | open plains at noon, long view distance |
| Lots of mobs | the same plains with 150 animals and villagers |
| Night | the same scene at midnight |
| Dense forest | the nearest dark forest |
| Rain | the forest in the rain |
| Ocean | the nearest ocean, lots of water |
| Snowfall | a snowy taiga while it snows |

Run it once on each renderer to compare them. The results are kept, so it doesn't matter which one goes first.

While flying, only what would hide the difference between the renderers changes: no frame cap, no VSync, and
no "Reduce FPS when AFK", which would otherwise cap the flight at 30 FPS after a minute. Everything else stays as
you set it, and the changed settings are put back afterwards (even if the game closes mid-flight).
The temporary world is deleted afterwards.

## Results

- Average FPS and **1% low FPS** (the average of the slowest 1% of frames, which is the stutter you feel) for
  every scene and overall, on both renderers.
- The difference per scene, and an overall verdict. Under 3% difference counts as a tie.
- A warning when the two renderers were measured with different settings.
- **Copy Results**: a short table for Discord, Reddit or a bug report.

## Things to know

- Keep the window in front and don't open menus while it flies. If you do, the current scene starts over.
  Leaving the world stops the benchmark and puts your settings back.
- If Vulkan can't run on your PC, the screen says why and the switch is disabled.
- If Vulkan is selected but the game started on OpenGL (it fell back, or a launcher option forces the graphics
  API), the screen says so.
- Sodium and other performance mods affect both renderers; the settings line notes when Sodium is installed.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5 or newer for Minecraft 26.3.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3.
3. Put the mod jar in your `mods` folder.
4. (Optional) Install [Mod Menu](https://modrinth.com/mod/modmenu).

## Configuration

`.minecraft/config/renderer-benchmark.json`:

```json
{
  "warmupSeconds": 10,
  "sceneSeconds": 20
}
```

`warmupSeconds` is the untimed flight after moving to a new place, while the terrain loads. The latest result
per renderer is kept in `config/renderer-benchmark/results.json`.

## Building from source

Any JDK 17+ starts the Gradle wrapper. Minecraft 26.3 needs Java 25, which Gradle downloads automatically.

```bash
./gradlew build                     # jar in build/libs/, also runs the unit tests
./gradlew runClient                 # dev client with Mod Menu
./gradlew runClient -PwithSodium    # ...plus Sodium
./gradlew runClientGameTest         # in-game tests (src/gametest)
```

The metrics, verdict, scenes, share text and flight route (`core` package) have no Minecraft dependencies and are
covered by plain unit tests in `src/test`. The client game test runs a whole benchmark with short scenes, checks
the saved result and that your settings come back, then fakes a result for the other renderer (a test can't
restart the game) to check the comparison.
