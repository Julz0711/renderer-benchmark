package com.julz.rendererbenchmark.mixin;

import com.julz.rendererbenchmark.client.BenchmarkController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** runTick runs once per rendered frame, so this times every frame and moves the camera smoothly. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void rendererBenchmark$onFrame(CallbackInfo ci) {
        BenchmarkController.onFrame((Minecraft) (Object) this);
    }
}
