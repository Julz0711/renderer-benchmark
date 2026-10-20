package com.julz.rendererbenchmark.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.julz.rendererbenchmark.client.RendererBenchmarkClient;
import com.julz.rendererbenchmark.core.BenchmarkResult;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The latest benchmark on each renderer, kept across restarts so the two can be compared after
 * switching. Also remembers the settings a benchmark changed, so they come back even if the game
 * closes in the middle of one.
 */
public final class ResultStore {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("renderer-benchmark");
    private static final Path RESULTS_FILE = DIR.resolve("results.json");
    private static final Path RESTORE_FILE = DIR.resolve("restore-settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The settings a benchmark changes while it flies. */
    public static class SettingsSnapshot {
        public int framerateLimit;
        public boolean vsync;
        public boolean pauseOnLostFocus;
        public String inactivityFpsLimit;
    }

    private ResultStore() {
    }

    /** Results by renderer name. */
    public static Map<String, BenchmarkResult> load() {
        Map<String, BenchmarkResult> results = read(RESULTS_FILE, new TypeToken<Map<String, BenchmarkResult>>() {
        });
        Map<String, BenchmarkResult> valid = new LinkedHashMap<>();
        if (results != null) {
            results.forEach((renderer, result) -> {
                if (result != null && result.renderer != null && result.overall != null && result.scenes != null) {
                    valid.put(renderer, result);
                }
            });
        }
        return valid;
    }

    public static BenchmarkResult get(String renderer) {
        return load().get(renderer);
    }

    public static void put(BenchmarkResult result) {
        Map<String, BenchmarkResult> results = load();
        results.put(result.renderer, result);
        write(RESULTS_FILE, results);
    }

    public static void clear() {
        delete(RESULTS_FILE);
    }

    public static void saveRestore(SettingsSnapshot settings) {
        write(RESTORE_FILE, settings);
    }

    public static SettingsSnapshot loadRestore() {
        return read(RESTORE_FILE, new TypeToken<SettingsSnapshot>() {
        });
    }

    public static void clearRestore() {
        delete(RESTORE_FILE);
    }

    private static <T> T read(Path file, TypeToken<T> type) {
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, type);
        } catch (IOException | JsonParseException e) {
            RendererBenchmarkClient.LOGGER.error("Failed to read {}, ignoring it", file, e);
            return null;
        }
    }

    private static void write(Path file, Object value) {
        try {
            Files.createDirectories(DIR);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(value, writer);
            }
        } catch (IOException e) {
            RendererBenchmarkClient.LOGGER.error("Failed to save {}", file, e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            RendererBenchmarkClient.LOGGER.error("Failed to delete {}", file, e);
        }
    }
}
