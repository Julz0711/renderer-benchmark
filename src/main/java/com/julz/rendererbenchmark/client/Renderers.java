package com.julz.rendererbenchmark.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;

import java.util.Locale;

/** What the game is rendering with right now, and the Graphics API option that picks it at startup. */
public final class Renderers {
    public static final String VULKAN = "Vulkan";
    public static final String OPENGL = "OpenGL";

    private Renderers() {
    }

    /** "Vulkan" or "OpenGL": the backend that actually started, which can differ from the option. */
    public static String current() {
        DeviceInfo info = deviceInfo();
        if (info == null) {
            return OPENGL;
        }
        return info.backendName().toLowerCase(Locale.ROOT).contains("vulkan") ? VULKAN : OPENGL;
    }

    public static String other(String renderer) {
        return VULKAN.equals(renderer) ? OPENGL : VULKAN;
    }

    /** The option value that makes the game start on the given renderer. */
    public static PreferredGraphicsApi preferenceFor(String renderer) {
        return VULKAN.equals(renderer) ? PreferredGraphicsApi.VULKAN : PreferredGraphicsApi.OPENGL;
    }

    /** The renderer an option value starts on. "Default" tries OpenGL first in 26.3. */
    public static String startsOn(PreferredGraphicsApi api) {
        return api == PreferredGraphicsApi.VULKAN ? VULKAN : OPENGL;
    }

    public static PreferredGraphicsApi preference(Minecraft client) {
        return client.options.preferredGraphicsBackend().get();
    }

    public static PreferredGraphicsApi parsePreference(String name) {
        for (PreferredGraphicsApi api : PreferredGraphicsApi.values()) {
            if (api.getSerializedName().equals(name)) {
                return api;
            }
        }
        return PreferredGraphicsApi.DEFAULT;
    }

    /** Changes the Graphics API option and writes options.txt. Takes effect on the next start. */
    public static void setPreference(Minecraft client, PreferredGraphicsApi api) {
        client.options.preferredGraphicsBackend().set(api);
        client.options.save();
    }

    /** GPU name and driver, as on the F3 screen. */
    public static String gpu() {
        DeviceInfo info = deviceInfo();
        if (info == null) {
            return "unknown";
        }
        String driver = info.driverInfo().lines().findFirst().orElse("");
        return driver.isEmpty() ? info.name() : info.name() + " (" + driver + ")";
    }

    /**
     * Null when Vulkan can be used on this PC, otherwise the reason it cannot. Only checked while
     * running on OpenGL; when the game already runs on Vulkan, it is obviously available.
     */
    public static String vulkanProblem() {
        if (VULKAN.equals(current())) {
            return null;
        }
        try {
            BackendCreationException problem = VulkanBackend.checkBackendAvailable();
            return problem == null ? null : problem.getMessage();
        } catch (Throwable t) {
            // The check itself failed; the restart will show whether Vulkan starts.
            RendererBenchmarkClient.LOGGER.warn("Could not check whether Vulkan is available", t);
            return null;
        }
    }

    private static DeviceInfo deviceInfo() {
        GpuDevice device = RenderSystem.tryGetDevice();
        return device == null ? null : device.getDeviceInfo();
    }
}
