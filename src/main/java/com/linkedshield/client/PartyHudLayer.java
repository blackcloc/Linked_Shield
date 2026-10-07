package com.linkedshield.client;

import com.linkedshield.config.LinkedShieldSettings;
import com.linkedshield.config.ConfigManager;
import com.linkedshield.network.PartyStatePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.List;

/**
 * 屏幕最右侧的 FF14 风格小队面板：
 * 每行 = 序号 + 名字 + 血量条（+ 护盾条），队长用金色侧边标记；
 * 队友有状态效果时，血条下方再多画一排缩小的小图标。
 * 整体按 hud.partyList.scale 缩放（默认 0.8），不再显示距离。
 */
public class PartyHudLayer implements GuiLayer {

    /** 客户端按键 / 命令强制隐藏。 */
    public static boolean userHidden = false;

    /** 血条下方效果小图标的边长、间距和整条预留高度（面板缩放前，缩放后会更小）。 */
    private static final int EFFECT_ICON_SIZE = 9;
    private static final int EFFECT_ICON_GAP = 1;
    private static final int EFFECT_AREA = 10;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || userHidden) {
            return;
        }
        if (minecraft.screen instanceof PartyScreen) {
            return; // 组队界面打开时不显示 HUD，避免挡住界面按钮
        }
        LinkedShieldSettings cfg = ConfigManager.get();
        if (!cfg.hud.enabled || !cfg.hud.partyList.visible) {
            return;
        }
        PartyStatePayload payload = ClientPartyState.get();
        if (payload == null) {
            return;
        }
        LinkedShieldSettings.PartyList layout = cfg.hud.partyList;
        if (layout.hideWhenSolo && (!payload.inParty() || payload.members().isEmpty())) {
            return;
        }

        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        double scale = Mth.clamp(layout.scale, 0.4D, 2.0D);
        boolean right = !"LEFT".equalsIgnoreCase(layout.anchor);
        int scaledWidth = (int) Math.round(layout.rowWidth * scale);
        int panelX = right ? screenWidth - layout.offsetX - scaledWidth : layout.offsetX;
        int panelY = layout.offsetY;

        List<PartyStatePayload.Member> members = ClientPartyState.sortedMembers(layout.sortMode);
        int limit = Math.min(members.size(), Math.max(1, layout.maxRows));

        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX, panelY);
        graphics.pose().scale((float) scale, (float) scale);
        int y = 0;
        for (int i = 0; i < limit; i++) {
            PartyStatePayload.Member member = members.get(i);
            drawRow(graphics, font, member, 0, y, layout, i + 1);
            // 有状态效果时给这一行多留一条图标带的高度，避免盖住下一行
            y += layout.rowHeight + layout.rowGap + (hasEffects(member) ? EFFECT_AREA : 0);
        }
        graphics.pose().popMatrix();
    }

    private static boolean hasEffects(PartyStatePayload.Member member) {
        return member.effects() != null && !member.effects().isEmpty();
    }

    private void drawRow(GuiGraphics graphics, Font font, PartyStatePayload.Member member,
                         int x, int y, LinkedShieldSettings.PartyList layout, int index) {
        int rowWidth = layout.rowWidth;
        int rowHeight = layout.rowHeight;
        int alpha = (int) (Mth.clamp(layout.backgroundOpacity, 0.0D, 1.0D) * 255.0D);
        graphics.fill(x, y, x + rowWidth, y + rowHeight, (alpha << 24) | 0x0B1016);

        // 队长金色 / 普通蓝灰的左侧竖条
        int accent = member.leader() ? 0xFFFFC85C : 0xFF6E8BA6;
        graphics.fill(x, y, x + 2, y + rowHeight, accent);

        int barBottom = y + rowHeight;
        int hpTop = barBottom - 4;
        float healthRatio = member.maxHealth() <= 0.0F
                ? 0.0F : Mth.clamp(member.health() / member.maxHealth(), 0.0F, 1.0F);

        // 血量条
        graphics.fill(x + 2, hpTop, x + rowWidth, barBottom, 0xE0202329);
        int hpWidth = (int) ((rowWidth - 2) * healthRatio);
        if (hpWidth > 0) {
            graphics.fill(x + 2, hpTop, x + 2 + hpWidth, barBottom, healthColor(healthRatio));
        }

        // 护盾条（血量条正上方一条细蓝条）
        if (layout.showShieldBar) {
            double shieldRatio = member.maxShield() <= 0.0D
                    ? 0.0D : Mth.clamp(member.shield() / member.maxShield(), 0.0D, 1.0D);
            int shieldTop = hpTop - 3;
            graphics.fill(x + 2, shieldTop, x + rowWidth, hpTop, 0xCC141A22);
            int shieldWidth = (int) ((rowWidth - 2) * shieldRatio);
            if (shieldWidth > 0) {
                graphics.fill(x + 2, shieldTop, x + 2 + shieldWidth, hpTop, 0xFF4FC3F7);
            }
        }

        String name = (layout.showMemberIndex ? index + ". " : "") + member.name();
        int nameColor = member.health() <= 0.0F ? 0xFF8A8A8A : 0xFFFFFFFF;
        String position = layout.namePosition == null ? "ABOVE_BAR" : layout.namePosition.toUpperCase();
        int nameX;
        int nameY;
        switch (position) {
            case "LEFT" -> {
                nameX = x - font.width(name) - 4 + layout.nameOffsetX;
                nameY = y + (rowHeight - 8) / 2 + layout.nameOffsetY;
            }
            case "INSIDE_BAR" -> {
                nameX = x + 4 + layout.nameOffsetX;
                nameY = hpTop - 2 + layout.nameOffsetY;
            }
            default -> {
                nameX = x + 4 + layout.nameOffsetX;
                nameY = y + 2 + layout.nameOffsetY;
            }
        }
        graphics.drawString(font, name, nameX, nameY, nameColor, true);
        drawEffects(graphics, member, x, y, layout);
    }

    /**
     * 血条下方那排状态效果小图标：9x9、间隔 1px，从 {@code y + rowHeight + 1} 开始画。
     * 图标画在面板缩放矩阵里，所以跟着 {@code hud.partyList.scale} 一起缩小。
     * 已知效果用原版效果图标（原版配色，快到期时和原版一样闪烁），未知 id 退回 9x9 纯色块。
     */
    private static void drawEffects(GuiGraphics graphics, PartyStatePayload.Member member,
                                    int x, int y, LinkedShieldSettings.PartyList layout) {
        List<PartyStatePayload.Effect> effects = member.effects();
        if (effects == null || effects.isEmpty()) {
            return;
        }
        int iconX = x + 2;
        int iconY = y + layout.rowHeight + 1;
        int maxIcons = Math.max(1, (layout.rowWidth - 4) / (EFFECT_ICON_SIZE + EFFECT_ICON_GAP));
        int count = Math.min(effects.size(), maxIcons);
        for (int i = 0; i < count; i++) {
            PartyStatePayload.Effect effect = effects.get(i);
            int drawX = iconX + i * (EFFECT_ICON_SIZE + EFFECT_ICON_GAP);
            Identifier sprite = effectSprite(effect.id());
            if (sprite != null) {
                // 原版效果图标贴图本身已经上过色，原版也只用白色 tint（alpha 负责“快到期”闪烁），
                // 所以这里不能再拿效果颜色去乘 —— 那会把图标压暗、串色。
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                        drawX, iconY, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE,
                        ARGB.white(iconAlpha(effect.duration())));
            } else {
                // 解析不出图标（自定义 / 动态注册的效果）时，退回该效果颜色的纯色块
                graphics.fill(drawX, iconY, drawX + EFFECT_ICON_SIZE, iconY + EFFECT_ICON_SIZE,
                        opaque(effect.color()));
            }
        }
    }

    /**
     * 原版 {@code Gui#renderEffects} 的“快到期闪烁”透明度：剩余 200 tick 内按原版公式淡入淡出，
     * 其余情况（含 {@code duration = -1} 的无限效果）完全不透明。
     */
    private static float iconAlpha(int remainingTicks) {
        if (remainingTicks < 0 || remainingTicks > 200) {
            return 1.0F;
        }
        int flash = 10 - remainingTicks / 20;
        float alpha = Mth.clamp(remainingTicks / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F)
                + Mth.cos(remainingTicks * (float) Math.PI / 5.0F)
                * Mth.clamp(flash / 10.0F * 0.25F, 0.0F, 0.25F);
        return Mth.clamp(alpha, 0.0F, 1.0F);
    }

    /** 原版色值不带 alpha，补成不透明。 */
    private static int opaque(int argb) {
        return (argb >>> 24) == 0 ? argb | 0xFF000000 : argb;
    }

    /** 效果注册名 -> 原版效果图标 sprite（{@code mob_effect/xxx}）；解析不出来返回 null。 */
    private static Identifier effectSprite(String id) {
        Identifier effectId = id == null ? null : Identifier.tryParse(id);
        if (effectId == null) {
            return null;
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(effectId);
        return effect == null ? null : Gui.getMobEffectSprite(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
    }

    private static int healthColor(float ratio) {
        if (ratio > 0.6F) {
            return 0xFF5EC46A;
        }
        if (ratio > 0.3F) {
            return 0xFFE0C04B;
        }
        return 0xFFE04B4B;
    }
}
