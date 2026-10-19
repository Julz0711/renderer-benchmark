package com.julz.rendererbenchmark.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.julz.rendererbenchmark.client.RendererBenchmarkClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    public static final int MIN_RUNS = 1;
    public static final int MAX_RUNS = 5;

    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("renderer-benchmark.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance;

    // Each renderer runs this many times and the median counts, which cuts the noise of a single run.
    public int runsPerRenderer = 3;
    // Untimed flight before the first run, so chunk loading and shader compiles do not count.
    public int warmupSeconds = 15;

    public static ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, ModConfig.class);
            } catch (IOException | JsonParseException e) {
                RendererBenchmarkClient.LOGGER.error("Failed to read {}, using defaults", CONFIG_FILE, e);
            }
        }
        if (instance == null) {
            instance = new ModConfig();
        }
        instance.sanitize();
        // Writes the file on first launch and adds options introduced by newer versions.
        save();
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            RendererBenchmarkClient.LOGGER.error("Failed to save {}", CONFIG_FILE, e);
        }
    }

    /** Pulls hand-edited values back into range. */
    public void sanitize() {
        runsPerRenderer = Math.clamp(runsPerRenderer, MIN_RUNS, MAX_RUNS);
        warmupSeconds = Math.clamp(warmupSeconds, 5, 60);
    }
}
