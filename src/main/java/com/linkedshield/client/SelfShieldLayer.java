package com.linkedshield.client;

import com.linkedshield.config.LinkedShieldSettings;
import com.linkedshield.config.ConfigManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * 玩家自己的护盾条：默认绘制在原版血条正上方（位置可在配置里改）。
 */
public class SelfShieldLayer implements GuiLayer {

    /** 原版血条：x = 屏宽/2 - 91，y = 屏高 - 39（与 vanilla Gui 保持一致）。 */
    private static final int VANILLA_HEALTH_X = 91;
    private static final int VANILLA_HEALTH_Y = 39;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || PartyHudLayer.userHidden) {
            return;
        }
        LinkedShieldSettings cfg = ConfigManager.get();
        LinkedShieldSettings.SelfShield layout = cfg.hud.selfShield;
        if (!cfg.hud.enabled || !layout.visible) {
            return;
        }
        double shield = ClientPartyState.ownShield();
        double maxShield = ClientPartyState.ownMaxShield();
        if (maxShield <= 0.0D) {
            return;
        }
        double ratio = Mth.clamp(shield / maxShield, 0.0D, 1.0D);

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int width = Math.max(1, layout.width);
        int height = Math.max(1, layout.height);
        int baseX = screenWidth / 2 - VANILLA_HEALTH_X;
        int baseY = switch (layout.anchor == null ? "ABOVE_HEALTH" : layout.anchor.toUpperCase()) {
            case "BELOW_HEALTH" -> screenHeight - VANILLA_HEALTH_Y + 10;
            case "ABOVE_HOTBAR" -> screenHeight - 22 - height - 3;
            default -> screenHeight - VANILLA_HEALTH_Y - height - 3;
        };
        int x = baseX + layout.offsetX;
        int y = baseY + layout.offsetY;

        if (layout.showBar) {
            graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF0B0F14);
            graphics.fill(x, y, x + width, y + height, 0xE01B2431);
            int filled = (int) (width * ratio);
            if (filled > 0) {
                graphics.fill(x, y, x + filled, y + height, parseColor(layout.barColor));
            }
        }

        if (layout.showText) {
            Font font = minecraft.font;
            String text = format(layout.numberFormat, shield, maxShield, ratio);
            int textWidth = font.width(text);
            int textX = switch (layout.textPosition == null ? "RIGHT" : layout.textPosition.toUpperCase()) {
                case "LEFT" -> x - textWidth - 4;
                case "CENTER" -> x + (width - textWidth) / 2;
                default -> x + width + 4;
            };
            int textY = y + (height - 8) / 2;
            graphics.drawString(font, text, textX, textY, 0xFFDCEBFA, true);
        }
    }

    private static String format(String numberFormat, double shield, double maxShield, double ratio) {
        String mode = numberFormat == null ? "CURRENT_MAX" : numberFormat.toUpperCase();
        return switch (mode) {
            case "RATIO" -> String.format("%.0f%%", ratio * 100.0D);
            case "CURRENT" -> String.format("%.0f", shield);
            default -> String.format("%.0f/%.0f", shield, maxShield);
        };
    }

    private static int parseColor(String hex) {
        try {
            String value = hex.startsWith("#") ? hex.substring(1) : hex;
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (RuntimeException e) {
            return 0xFF4FC3F7;
        }
    }
}
