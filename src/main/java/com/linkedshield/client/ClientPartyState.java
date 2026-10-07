package com.linkedshield.client;

import com.linkedshield.config.ConfigManager;
import com.linkedshield.network.PartyStatePayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 客户端保存的最后一次小队状态（HUD 与组队界面的数据源）。 */
public final class ClientPartyState {

    /** HUD 预览模式：-Dlinkedshield.huddemo=true 时用假队友数据渲染，方便单人调布局。 */
    private static final boolean DEMO = Boolean.getBoolean("linkedshield.huddemo");

    private static volatile PartyStatePayload latest;
    private static volatile PartyStatePayload demo;
    /** 本次会话里已经点过“邀请”的玩家（只影响按钮文字）。 */
    private static final java.util.Set<UUID> INVITED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private ClientPartyState() {
    }

    public static void markInvited(UUID id) {
        INVITED.add(id);
    }

    public static boolean isInvited(UUID id) {
        return INVITED.contains(id);
    }

    public static void update(PartyStatePayload payload) {
        latest = payload;
    }

    public static void clear() {
        latest = null;
        INVITED.clear();
    }

    public static PartyStatePayload get() {
        if (DEMO) {
            if (demo == null) {
                demo = new PartyStatePayload(
                        List.of(new PartyStatePayload.Member(UUID.randomUUID(), "Aria_Light", 15.5F, 20.0F, 62.0D, 100.0D, 7, false,
                                        List.of(new PartyStatePayload.Effect("minecraft:regeneration", 0xFFCD5CAB, false, 620),
                                                new PartyStatePayload.Effect("minecraft:speed", 0xFF7CAFC6, false, 1200),
                                                new PartyStatePayload.Effect("minecraft:strength", 0xFF932423, false, 300))),
                                new PartyStatePayload.Member(UUID.randomUUID(), "Kupo Knight", 6.0F, 20.0F, 90.0D, 100.0D, 12, false,
                                        List.of(new PartyStatePayload.Effect("minecraft:absorption", 0xFF2552A5, false, 900),
                                                new PartyStatePayload.Effect("minecraft:poison", 0xFF4E9331, true, 180),
                                                new PartyStatePayload.Effect("minecraft:slowness", 0xFF5A6C81, true, 400))),
                                new PartyStatePayload.Member(UUID.randomUUID(), "Moogle", 19.0F, 20.0F, 12.0D, 100.0D, 23, false,
                                        List.of())),
                        List.of(new PartyStatePayload.Nearby(UUID.randomUUID(), "Estinien", 4, false),
                                new PartyStatePayload.Nearby(UUID.randomUUID(), "Y'shtola", 8, true)),
                        68.0D, 100.0D, 6, true, true,
                        "拂晓血盟", "Aria_Light", "Estinien", true, 4);
            }
            return demo;
        }
        return latest;
    }

    public static double ownShield() {
        PartyStatePayload payload = get();
        return payload == null ? 0.0D : payload.ownShield();
    }

    public static double ownMaxShield() {
        PartyStatePayload payload = get();
        return payload == null ? 0.0D : payload.ownMaxShield();
    }

    public static boolean hasInvite() {
        PartyStatePayload payload = get();
        return payload != null && payload.hasInvite();
    }

    public static String inviteFrom() {
        PartyStatePayload payload = get();
        return payload == null ? "" : payload.inviteFrom();
    }

    public static String partyName() {
        PartyStatePayload payload = get();
        return payload == null ? "" : payload.partyName();
    }

    public static boolean inParty() {
        PartyStatePayload payload = get();
        return payload != null && payload.inParty();
    }

    public static boolean isLeader() {
        PartyStatePayload payload = get();
        return payload != null && payload.leader();
    }

    public static List<PartyStatePayload.Nearby> nearby() {
        PartyStatePayload payload = get();
        return payload == null ? List.of() : payload.nearby();
    }

    /** 按配置排序队友（自己不在列表里）。 */
    public static List<PartyStatePayload.Member> sortedMembers(String sortMode) {
        PartyStatePayload payload = get();
        if (payload == null) {
            return List.of();
        }
        List<PartyStatePayload.Member> members = new ArrayList<>(payload.members());
        String mode = sortMode == null ? "LEADER_FIRST" : sortMode.toUpperCase();
        switch (mode) {
            case "NAME" -> members.sort(Comparator.comparing(PartyStatePayload.Member::name,
                    String.CASE_INSENSITIVE_ORDER));
            case "DISTANCE" -> members.sort(Comparator.comparingInt(PartyStatePayload.Member::distance));
            default -> members.sort(Comparator
                    .comparing((PartyStatePayload.Member m) -> !m.leader())
                    .thenComparingInt(PartyStatePayload.Member::distance));
        }
        return members;
    }

    public static com.linkedshield.config.LinkedShieldSettings.Hud hud() {
        return ConfigManager.get().hud;
    }
}
