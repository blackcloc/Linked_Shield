package com.linkedshield.client;

import com.linkedshield.config.ConfigManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * 收到组队邀请时，在屏幕右上角显示一个小点（轻微呼吸效果），
 * 按 P 打开组队界面即可接受/拒绝。
 */
public class PartyNotifyLayer implements GuiLayer {

    private static final int DOT_SIZE = 5;
    private static final int COLOR = 0x00FFD479;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || PartyHudLayer.userHidden) {
            return;
        }
        if (minecraft.screen instanceof PartyScreen) {
            return; // 组队界面里已经有接受/拒绝按钮了
        }
        if (!ConfigManager.get().hud.enabled || !ClientPartyState.hasInvite()) {
            return;
        }
        int x = graphics.guiWidth() - 12 - DOT_SIZE;
        int y = 9;
        float pulse = 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 260.0D);
        int alpha = Math.max(40, Math.min(255, (int) (pulse * 255.0F)));

        // 外圈描边 + 十字拼接的主体，保证在任何背景上都看得见，且视觉上接近圆点
        graphics.fill(x - 2, y - 2, x + DOT_SIZE + 2, y + DOT_SIZE + 2, (alpha / 3 << 24) | COLOR);
        graphics.fill(x - 1, y - 1, x + DOT_SIZE + 1, y + DOT_SIZE + 1, 0xC0000000);
        graphics.fill(x + 1, y, x + DOT_SIZE - 1, y + DOT_SIZE, (alpha << 24) | COLOR);
        graphics.fill(x, y + 1, x + DOT_SIZE, y + DOT_SIZE - 1, (alpha << 24) | COLOR);
    }
}
