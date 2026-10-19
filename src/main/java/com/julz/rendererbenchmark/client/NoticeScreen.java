package com.julz.rendererbenchmark.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** A title, a message and an OK button that goes back to the parent screen. */
public class NoticeScreen extends Screen {
    private static final int TEXT_WIDTH = 300;

    private final Screen parent;
    private final Component message;

    public NoticeScreen(Screen parent, Component title, Component message) {
        super(title);
        this.parent = parent;
        this.message = message;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_OK, button -> onClose())
                .bounds(this.width / 2 - 100, this.height / 2 + 50, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFFFF);
        int width = Math.min(TEXT_WIDTH, this.width - 40);
        graphics.textWithWordWrap(this.font, this.message, (this.width - width) / 2, this.height / 2 - 40, width, 0xFFD0D0D0);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
