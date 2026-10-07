package com.linkedshield.client;

import com.linkedshield.network.PartyActionPayload;
import com.linkedshield.network.PartyStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 组队界面（按 P 打开）：
 * 左边是「我的小队」（队伍名、队内玩家、离开/解散/踢人/改名），
 * 右边是「附近玩家」（默认 10 格内的其他玩家，可一键邀请），
 * 顶部在有邀请时会出现「XX 邀请你加入小队」+ 接受/拒绝。
 */
public class PartyScreen extends Screen {

    private static final int PANEL_WIDTH = 168;
    private static final int ROW_HEIGHT = 18;
    /** 面板区域。 */
    private static final int PANEL_TOP = 14;
    /** 分区标题 y。 */
    private static final int SECTION_TITLE_Y = 52;
    /** 第一行列表项的 y（成员 / 附近玩家对齐）。 */
    private static final int LIST_TOP = 76;
    /** 默认打开组队界面的按键名（提示文案用）。 */
    private String signature = "";
    private EditBox renameBox;

    public PartyScreen() {
        super(Component.translatable("linkedshield.gui.title"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        if (!signature.equals(currentSignature())) {
            rebuild();
        }
    }

    /* ------------------------------------------------------------------ 按钮重建 */

    private String currentSignature() {
        PartyStatePayload state = ClientPartyState.get();
        if (state == null) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(state.inParty()).append('|').append(state.leader()).append('|')
                .append(state.partyName()).append('|').append(state.inviteFrom()).append('|');
        for (PartyStatePayload.Member member : state.members()) {
            sb.append(member.id()).append(member.leader()).append(',');
        }
        sb.append('|');
        for (PartyStatePayload.Nearby nearby : state.nearby()) {
            sb.append(nearby.id()).append(nearby.inParty()).append(',');
        }
        return sb.toString();
    }

    private void rebuild() {
        clearWidgets();
        renameBox = null;
        signature = currentSignature();

        PartyStatePayload state = ClientPartyState.get();
        int left = this.width / 2 - PANEL_WIDTH - 6;
        int right = this.width / 2 + 6;
        int panelBottom = this.height - 46;
        int buttonRow = this.height - 30;

        // 顶部邀请横幅：接受 / 拒绝
        if (state != null && state.hasInvite()) {
            addRenderableWidget(Button.builder(Component.translatable("linkedshield.gui.accept"),
                            b -> send(PartyActionPayload.simple("ACCEPT")))
                    .bounds(left + 6, 20, 98, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("linkedshield.gui.deny"),
                            b -> send(PartyActionPayload.simple("DENY")))
                    .bounds(left + 108, 20, 54, 18).build());
        }

        // 左边：我的小队（第一行是自己，后面才是队友）
        if (state != null && state.inParty()) {
            int y = LIST_TOP + ROW_HEIGHT;
            for (PartyStatePayload.Member member : state.members()) {
                if (state.leader()) {
                    final PartyStatePayload.Member target = member;
                    addRenderableWidget(Button.builder(Component.translatable("linkedshield.gui.kick"),
                                    b -> send(PartyActionPayload.kick(target.id())))
                            .bounds(left + PANEL_WIDTH - 46, y + 1, 42, 16).build());
                }
                y += ROW_HEIGHT;
            }
            addRenderableWidget(Button.builder(Component.translatable("linkedshield.gui.leave"),
                            b -> send(PartyActionPayload.simple("LEAVE")))
                    .bounds(left + 6, buttonRow, 78, 18).build());
            if (state.leader()) {
                addRenderableWidget(Button.builder(Component.translatable("linkedshield.gui.disband"),
                                b -> send(PartyActionPayload.simple("DISBAND")))
                        .bounds(left + 88, buttonRow, 74, 18).build());

                renameBox = new EditBox(this.font, left + 6, panelBottom - 24, PANEL_WIDTH - 62, 18,
                        Component.translatable("linkedshield.gui.rename_hint"));
                renameBox.setMaxLength(24);
                renameBox.setValue(state.partyName() != null && !state.partyName().endsWith("的小队")
                        ? state.partyName() : "");
                addRenderableWidget(renameBox);
                addRenderableWidget(Button.builder(Component.translatable("linkedshield.gui.rename"),
                                b -> send(PartyActionPayload.rename(renameBox == null ? "" : renameBox.getValue())))
                        .bounds(left + PANEL_WIDTH - 52, panelBottom - 24, 46, 18).build());
            }
        }

        // 右边：附近玩家
        if (state != null) {
            int y = LIST_TOP;
            for (PartyStatePayload.Nearby nearby : state.nearby()) {
                if (!nearby.inParty() && !ClientPartyState.isInvited(nearby.id())) {
                    final PartyStatePayload.Nearby target = nearby;
                    addRenderableWidget(Button.builder(Component.translatable("linkedshield.gui.invite"),
                                    b -> {
                                        ClientPartyState.markInvited(target.id());
                                        send(PartyActionPayload.invite(target.id()));
                                        rebuild();
                                    })
                            .bounds(right + PANEL_WIDTH - 52, y, 46, 18).build());
                }
                y += ROW_HEIGHT;
            }
        }

        addRenderableWidget(Button.builder(Component.translatable("linkedshield.gui.close"), b -> this.onClose())
                .bounds(right + 6, buttonRow, 74, 18).build());
    }

    private void send(PartyActionPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    /* ------------------------------------------------------------------ 绘制 */

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PartyStatePayload state = ClientPartyState.get();
        int left = this.width / 2 - PANEL_WIDTH - 6;
        int right = this.width / 2 + 6;
        int panelBottom = this.height - 46;

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);

        panel(graphics, left, PANEL_TOP, PANEL_WIDTH, panelBottom - PANEL_TOP);
        panel(graphics, right, PANEL_TOP, PANEL_WIDTH, panelBottom - PANEL_TOP);

        if (state == null) {
            graphics.drawCenteredString(this.font, Component.translatable("linkedshield.gui.no_party"),
                    this.width / 2, this.height / 2, 0xFFAAAAAA);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // 顶部邀请横幅文字（接受/拒绝按钮在同样位置的下方由 widget 绘制）
        if (state.hasInvite()) {
            graphics.drawString(this.font, Component.translatable("linkedshield.gui.invite_banner", state.inviteFrom()),
                    left + 6, 42, 0xFFFFD479, true);
        }

        // ---------------- 左：我的小队 ----------------
        graphics.drawString(this.font, Component.translatable("linkedshield.gui.party_section"),
                left + 6, SECTION_TITLE_Y, 0xFF7FD4FF, true);
        if (state.inParty()) {
            graphics.drawString(this.font, state.partyName(), left + 6, SECTION_TITLE_Y + 12, 0xFFFFE7A3, true);
            int y = LIST_TOP;
            // 自己永远排在第一位，并在名字后面标出“（你）”
            String selfName = this.minecraft != null && this.minecraft.player != null
                    ? this.minecraft.player.getName().getString() : "";
            if (!selfName.isEmpty()) {
                nameRow(graphics, left, y,
                        (state.leader() ? "★ " : "  ") + selfName + Component.translatable("linkedshield.gui.you").getString(),
                        state.leader());
                y += ROW_HEIGHT;
            }
            for (PartyStatePayload.Member member : state.members()) {
                nameRow(graphics, left, y, (member.leader() ? "★ " : "  ") + member.name(), member.leader());
                y += ROW_HEIGHT;
            }
        } else {
            graphics.drawString(this.font, Component.translatable("linkedshield.gui.no_party"),
                    left + 6, SECTION_TITLE_Y + 16, 0xFFAAAAAA, false);
        }

        // ---------------- 右：附近玩家 ----------------
        graphics.drawString(this.font, Component.translatable("linkedshield.gui.nearby_section",
                        String.valueOf((int) com.linkedshield.config.ConfigManager.get().party.nearbyRadius)),
                right + 6, SECTION_TITLE_Y, 0xFF7FD4FF, true);
        if (state.nearby().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("linkedshield.gui.nearby_empty"),
                    right + 6, SECTION_TITLE_Y + 16, 0xFFAAAAAA, false);
        }
        int y = LIST_TOP;
        for (PartyStatePayload.Nearby nearby : state.nearby()) {
            nearbyRow(graphics, nearby, right, y);
            y += ROW_HEIGHT;
        }

        // 底部提示
        graphics.drawCenteredString(this.font, Component.translatable("linkedshield.gui.hint", LinkedShieldClient.openKeyName()),
                this.width / 2, this.height - 44, 0xFF9AA7B4);

        // 最后画控件，保证按钮在面板背景之上（否则按钮会显得被遮挡成深灰）
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 组队界面里只显示名字（不显示血条/护盾值）。 */
    private void nameRow(GuiGraphics graphics, int x, int y, String label, boolean leader) {
        graphics.fill(x + 4, y, x + PANEL_WIDTH - 4, y + ROW_HEIGHT - 2, 0x66000000);
        graphics.drawString(this.font, label, x + 8, y + 4, leader ? 0xFFFFC85C : 0xFFFFFFFF, true);
    }

    private void nearbyRow(GuiGraphics graphics, PartyStatePayload.Nearby nearby, int x, int y) {
        graphics.fill(x + 4, y, x + PANEL_WIDTH - 4, y + ROW_HEIGHT - 2, 0x66000000);
        int color = nearby.inParty() ? 0xFF9AA7B4 : 0xFFFFFFFF;
        graphics.drawString(this.font, nearby.name(), x + 8, y + 6, color, true);
        String tag = nearby.inParty()
                ? Component.translatable("linkedshield.gui.in_party").getString()
                : ClientPartyState.isInvited(nearby.id())
                ? Component.translatable("linkedshield.gui.invited").getString()
                : nearby.distance() + "m";
        graphics.drawString(this.font, tag, x + PANEL_WIDTH - 56 - this.font.width(tag), y + 6, 0xFF9AA7B4, true);
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xB010161E);
        graphics.fill(x, y, x + w, y + 1, 0xFF2B3648);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF2B3648);
        graphics.fill(x, y, x + 1, y + h, 0xFF2B3648);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF2B3648);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
