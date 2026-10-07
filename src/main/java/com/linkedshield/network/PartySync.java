package com.linkedshield.network;

import com.linkedshield.config.LinkedShieldSettings;
import com.linkedshield.config.ConfigManager;
import com.linkedshield.party.Party;
import com.linkedshield.party.PartyManager;
import com.linkedshield.party.PartyStore;
import com.linkedshield.shield.LinkedShieldAttachments;
import com.linkedshield.shield.ShieldData;
import com.linkedshield.shield.ShieldService;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 把小队状态推送给客户端（HUD + 组队界面的数据来源）。 */
public final class PartySync {

    /** 每 4 tick 全量同步一次。 */
    public static final int SYNC_INTERVAL_TICKS = 4;
    /** 每个队友最多同步多少条状态效果（HUD 一排图标够用，别把包撑大）。 */
    public static final int MAX_SYNCED_EFFECTS = 8;

    private PartySync() {
    }

    public static void tick(MinecraftServer server) {
        PartyStore store = PartyManager.store(server);
        store.tickInvites(server.getTickCount());
        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendState(player, store);
        }
    }

    /** 小队/邀请发生变化时立刻同步。 */
    public static void syncParty(MinecraftServer server, Party party) {
        PartyStore store = PartyManager.store(server);
        for (ServerPlayer member : PartyManager.onlineMembers(server, party)) {
            sendState(member, store);
        }
    }

    public static void sendStateNow(ServerPlayer player) {
        sendState(player, PartyManager.store(player.level().getServer()));
    }

    public static void sendState(ServerPlayer player, PartyStore store) {
        MinecraftServer server = player.level().getServer();
        LinkedShieldSettings cfg = ConfigManager.get();
        ShieldData ownShield = player.getData(LinkedShieldAttachments.SHIELD);
        Party party = store.partyOf(player.getUUID());

        List<PartyStatePayload.Member> members = new ArrayList<>();
        String leaderName = "";
        String partyName = "";
        boolean isLeader = false;
        if (party != null) {
            isLeader = party.isLeader(player.getUUID());
            ServerPlayer leader = server.getPlayerList().getPlayer(party.getLeader());
            leaderName = leader == null ? "???" : leader.getName().getString();
            partyName = party.displayName(leaderName);
            for (UUID id : party.orderedMembers()) {
                if (id.equals(player.getUUID())) {
                    continue;
                }
                ServerPlayer teammate = server.getPlayerList().getPlayer(id);
                if (teammate == null) {
                    continue;
                }
                ShieldData teammateShield = teammate.getData(LinkedShieldAttachments.SHIELD);
                members.add(new PartyStatePayload.Member(
                        teammate.getUUID(),
                        teammate.getName().getString(),
                        teammate.getHealth(),
                        teammate.getMaxHealth(),
                        teammateShield.getShield(),
                        ShieldService.effectiveMaxShield(teammateShield),
                        (int) Math.sqrt(player.distanceToSqr(teammate)),
                        party.isLeader(teammate.getUUID()),
                        collectEffects(teammate)));
            }
        }

        // 附近玩家：同维度、半径内、且不是自己的队友
        List<PartyStatePayload.Nearby> nearby = new ArrayList<>();
        double radiusSqr = cfg.party.nearbyRadius * cfg.party.nearbyRadius;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(player.getUUID())) {
                continue;
            }
            if (party != null && party.contains(other.getUUID())) {
                continue;
            }
            if (!other.level().dimension().equals(player.level().dimension())) {
                continue;
            }
            double distanceSqr = player.distanceToSqr(other);
            if (distanceSqr > radiusSqr) {
                continue;
            }
            nearby.add(new PartyStatePayload.Nearby(
                    other.getUUID(),
                    other.getName().getString(),
                    (int) Math.sqrt(distanceSqr),
                    store.inParty(other.getUUID())));
        }
        nearby.sort((a, b) -> Integer.compare(a.distance(), b.distance()));

        // 待接受的邀请
        String inviteFrom = "";
        PartyStore.PendingInvite invite = store.getInvite(player.getUUID());
        if (invite != null && invite.expireTick() >= server.getTickCount()) {
            ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviter());
            if (inviter != null) {
                inviteFrom = inviter.getName().getString();
            }
        }

        // 连携状态（脱战 + 半径内有队友）；图标上的数字是“实际连携人数”，含玩家自己
        int linkedshieldTeammates = ShieldService.activeLinkedShield(player, cfg, server.getTickCount());
        int linkedshieldCount = linkedshieldTeammates > 0 ? linkedshieldTeammates + 1 : 0;

        LinkedShieldNetwork.sendTo(player, new PartyStatePayload(
                List.copyOf(members),
                List.copyOf(nearby),
                ownShield.getShield(),
                ShieldService.effectiveMaxShield(ownShield),
                cfg.party.maxPartySize,
                party != null,
                isLeader,
                partyName,
                leaderName,
                inviteFrom,
                linkedshieldTeammates > 0,
                linkedshieldCount));
    }

    /**
     * 收集队友当前“可见”的状态效果，供 HUD 在血条下方画小图标。
     * 排序：增益在前，其次剩余时间长的在前（{@code duration = -1} 的无限效果视为最长）；
     * 最多保留 {@link #MAX_SYNCED_EFFECTS} 条。
     */
    private static List<PartyStatePayload.Effect> collectEffects(ServerPlayer teammate) {
        List<PartyStatePayload.Effect> effects = new ArrayList<>();
        for (MobEffectInstance instance : teammate.getActiveEffects()) {
            if (!instance.isVisible()) {
                continue; // 与 HUD 一致：看不见的效果不显示
            }
            Holder<MobEffect> holder = instance.getEffect();
            String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
            if (id == null) {
                continue; // 动态注册（没有注册名）的效果客户端解析不了，跳过
            }
            MobEffect effect = holder.value();
            int color = effect.getColor();
            effects.add(new PartyStatePayload.Effect(
                    id,
                    (color >>> 24) == 0 ? color | 0xFF000000 : color, // 原版色值没有 alpha，补成不透明
                    !effect.isBeneficial(),
                    instance.getDuration()));
        }
        effects.sort(Comparator.comparing((PartyStatePayload.Effect effect) -> effect.harmful())
                .thenComparing(Comparator.comparingInt(PartySync::remainingSortKey).reversed()));
        if (effects.size() > MAX_SYNCED_EFFECTS) {
            effects.subList(MAX_SYNCED_EFFECTS, effects.size()).clear();
        }
        return List.copyOf(effects);
    }

    private static int remainingSortKey(PartyStatePayload.Effect effect) {
        return effect.duration() <= 0 ? Integer.MAX_VALUE : effect.duration();
    }
}
