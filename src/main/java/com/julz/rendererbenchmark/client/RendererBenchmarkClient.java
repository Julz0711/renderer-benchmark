package com.julz.rendererbenchmark.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RendererBenchmarkClient implements ClientModInitializer {
    public static final String MOD_ID = "renderer-benchmark";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(BenchmarkController::tick);

        // A button in Video Settings, next to the Graphics API option it is about.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof VideoSettingsScreen) {
                Screens.getWidgets(screen).add(Button.builder(Component.translatable("renderer-benchmark.videoSettingsButton"),
                                button -> client.gui.setScreen(new BenchmarkScreen(screen)))
                        .bounds(width - 104, 6, 100, 20)
                        .build());
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "progress"), (graphics, tickCounter) -> {
            if (!BenchmarkController.isFlying()) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            graphics.text(client.font, hudText(), 4, 4, 0xFFFFFFFF);
        });
    }

    private static Component hudText() {
        String renderer = BenchmarkController.currentRenderer();
        long seconds = Math.round(Math.ceil(BenchmarkController.secondsLeft()));
        if (BenchmarkController.step() == BenchmarkController.Step.WARMUP) {
            return Component.translatable("renderer-benchmark.hud.warmup", renderer, seconds);
        }
        return Component.translatable("renderer-benchmark.hud.run", renderer, BenchmarkController.runNumber(),
                BenchmarkController.state().runsPerRenderer, seconds);
    }
}
