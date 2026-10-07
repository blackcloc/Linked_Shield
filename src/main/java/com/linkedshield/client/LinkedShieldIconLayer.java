package com.linkedshield.client;

import com.linkedshield.LinkedShieldMod;
import com.linkedshield.config.LinkedShieldSettings;
import com.linkedshield.config.ConfigManager;
import com.linkedshield.network.PartyStatePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * 连携生效时，在玩家血条旁边显示的小图标：蓝色底 + 中间握手，
 * 右下角显示当前生效的连携队友数量。
 */
public class LinkedShieldIconLayer implements GuiLayer {

    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath(LinkedShieldMod.MODID, "textures/gui/linkedshield_icon.png");
    /** 原版血条：x = 屏宽/2 - 91，y = 屏高 - 39。 */
    private static final int VANILLA_HEALTH_X = 91;
    private static final int VANILLA_HEALTH_Y = 39;
    private static final int ICON_SIZE = 16;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || PartyHudLayer.userHidden) {
            return;
        }
        if (minecraft.screen instanceof PartyScreen) {
            return; // 组队界面打开时不显示 HUD
        }
        LinkedShieldSettings.Hud hud = ConfigManager.get().hud;
        LinkedShieldSettings.LinkedShieldIcon layout = hud.linkedshieldIcon;
        if (!hud.enabled || !layout.visible) {
            return;
        }
        PartyStatePayload payload = ClientPartyState.get();
        if (payload == null || !payload.linkedshieldActive()) {
            return;
        }

        double scale = Mth.clamp(layout.scale, 0.5D, 3.0D);
        int size = (int) Math.round(ICON_SIZE * scale);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        // 贴在血条左边，垂直与血条居中对齐
        int x = screenWidth / 2 - VANILLA_HEALTH_X - size - 4 + layout.offsetX;
        int y = screenHeight - VANILLA_HEALTH_Y - (size - 9) / 2 + layout.offsetY;

        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, x, y, 0.0F, 0.0F,
                size, size, ICON_SIZE, ICON_SIZE);

        if (layout.showCount && payload.linkedshieldCount() > 0) {
            String count = String.valueOf(payload.linkedshieldCount());
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + size - 3.0F, y + size - 10.0F);
            graphics.pose().scale(0.75F, 0.75F);
            graphics.drawString(minecraft.font, count, 0, 0, 0xFFFFFFFF, true);
            graphics.pose().popMatrix();
        }
    }
}
