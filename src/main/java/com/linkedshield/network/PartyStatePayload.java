package com.linkedshield.network;

import com.linkedshield.LinkedShieldMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端 -> 客户端的小队状态包，供 HUD 和组队界面使用。
 * 每 4 tick 同步一次（小队/邀请变化时立即同步）。
 *
 * @param members      队友（不含自己）
 * @param nearby       附近玩家（不含自己与队友）
 * @param ownShield    自己的当前护盾
 * @param ownMaxShield 自己的护盾上限
 * @param maxPartySize 小队人数上限
 * @param inParty      自己是否在小队里
 * @param leader       自己是否是队长
 * @param partyName    队伍显示名（没自定义就是“队长名 的小队”）
 * @param leaderName   队长名字
 * @param inviteFrom   待接受邀请的发起者名字，空串 = 没有邀请
 * @param linkedshieldActive 连携是否生效（脱战 + 身边有队友）
 * @param linkedshieldCount  生效的连携队友数量（图标右下角显示）
 */
public record PartyStatePayload(List<Member> members,
                                List<Nearby> nearby,
                                double ownShield,
                                double ownMaxShield,
                                int maxPartySize,
                                boolean inParty,
                                boolean leader,
                                String partyName,
                                String leaderName,
                                String inviteFrom,
                                boolean linkedshieldActive,
                                int linkedshieldCount) implements CustomPacketPayload {

    /** 一名队友的展示数据。distance 仅用于排序，界面不再显示。effects 是血条下方那排小图标。 */
    public record Member(UUID id,
                         String name,
                         float health,
                         float maxHealth,
                         double shield,
                         double maxShield,
                         int distance,
                         boolean leader,
                         List<Effect> effects) {
    }

    /**
     * 同步给 HUD 的一条状态效果。
     *
     * @param id       效果注册名，例如 {@code minecraft:speed}
     * @param color    ARGB 颜色（来自 {@link net.minecraft.world.effect.MobEffect#getColor()}）
     * @param harmful  是否是负面效果（{@code !MobEffect#isBeneficial()}）
     * @param duration 剩余 tick（{@code -1} = 无限）
     */
    public record Effect(String id, int color, boolean harmful, int duration) {
    }

    /** 附近玩家（组队界面右侧列表）。 */
    public record Nearby(UUID id, String name, int distance, boolean inParty) {
    }

    public static final Type<PartyStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LinkedShieldMod.MODID, "party_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyStatePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), PartyStatePayload::read);

    public static PartyStatePayload read(FriendlyByteBuf buf) {
        int memberCount = buf.readVarInt();
        List<Member> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            float health = buf.readFloat();
            float maxHealth = buf.readFloat();
            double shield = buf.readDouble();
            double maxShield = buf.readDouble();
            int distance = buf.readVarInt();
            boolean leader = buf.readBoolean();
            int effectCount = buf.readVarInt();
            List<Effect> effects = new ArrayList<>(effectCount);
            for (int j = 0; j < effectCount; j++) {
                effects.add(new Effect(
                        buf.readUtf(64),
                        buf.readVarInt(),
                        buf.readBoolean(),
                        buf.readVarInt()));
            }
            members.add(new Member(id, name, health, maxHealth, shield, maxShield,
                    distance, leader, List.copyOf(effects)));
        }
        int nearbyCount = buf.readVarInt();
        List<Nearby> nearby = new ArrayList<>(nearbyCount);
        for (int i = 0; i < nearbyCount; i++) {
            nearby.add(new Nearby(
                    buf.readUUID(),
                    buf.readUtf(64),
                    buf.readVarInt(),
                    buf.readBoolean()));
        }
        return new PartyStatePayload(
                List.copyOf(members),
                List.copyOf(nearby),
                buf.readDouble(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(members.size());
        for (Member member : members) {
            buf.writeUUID(member.id());
            buf.writeUtf(member.name(), 64);
            buf.writeFloat(member.health());
            buf.writeFloat(member.maxHealth());
            buf.writeDouble(member.shield());
            buf.writeDouble(member.maxShield());
            buf.writeVarInt(member.distance());
            buf.writeBoolean(member.leader());
            List<Effect> effects = member.effects() == null ? List.of() : member.effects();
            buf.writeVarInt(effects.size());
            for (Effect effect : effects) {
                buf.writeUtf(effect.id(), 64);
                buf.writeVarInt(effect.color());
                buf.writeBoolean(effect.harmful());
                buf.writeVarInt(effect.duration());
            }
        }
        buf.writeVarInt(nearby.size());
        for (Nearby player : nearby) {
            buf.writeUUID(player.id());
            buf.writeUtf(player.name(), 64);
            buf.writeVarInt(player.distance());
            buf.writeBoolean(player.inParty());
        }
        buf.writeDouble(ownShield);
        buf.writeDouble(ownMaxShield);
        buf.writeVarInt(maxPartySize);
        buf.writeBoolean(inParty);
        buf.writeBoolean(leader);
        buf.writeUtf(partyName, 64);
        buf.writeUtf(leaderName, 64);
        buf.writeUtf(inviteFrom, 64);
        buf.writeBoolean(linkedshieldActive);
        buf.writeVarInt(linkedshieldCount);
    }

    public boolean hasInvite() {
        return inviteFrom != null && !inviteFrom.isEmpty();
    }

    @Override
    public Type<PartyStatePayload> type() {
        return TYPE;
    }
}
