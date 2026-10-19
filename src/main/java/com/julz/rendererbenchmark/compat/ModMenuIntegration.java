package com.julz.rendererbenchmark.compat;

import com.julz.rendererbenchmark.client.BenchmarkScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Opens the comparison screen from Mod Menu. Only loaded by Mod Menu itself. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BenchmarkScreen::new;
    }
}
