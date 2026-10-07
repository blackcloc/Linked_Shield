package com.linkedshield.network;

import com.linkedshield.LinkedShieldMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * 客户端 -> 服务端的小队动作（组队界面按钮走这个包）。
 *
 * @param action INVITE / ACCEPT / DENY / LEAVE / DISBAND / KICK / RENAME
 * @param target 目标玩家（INVITE / KICK 用），其余传 {@link #NONE}
 * @param text   文本参数（RENAME 用）
 */
public record PartyActionPayload(String action, UUID target, String text) implements CustomPacketPayload {

    public static final UUID NONE = new UUID(0L, 0L);

    public static final Type<PartyActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LinkedShieldMod.MODID, "party_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyActionPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), PartyActionPayload::read);

    public static PartyActionPayload invite(UUID target) {
        return new PartyActionPayload("INVITE", target, "");
    }

    public static PartyActionPayload simple(String action) {
        return new PartyActionPayload(action, NONE, "");
    }

    public static PartyActionPayload kick(UUID target) {
        return new PartyActionPayload("KICK", target, "");
    }

    public static PartyActionPayload rename(String name) {
        return new PartyActionPayload("RENAME", NONE, name == null ? "" : name);
    }

    public static PartyActionPayload read(FriendlyByteBuf buf) {
        return new PartyActionPayload(buf.readUtf(24), buf.readUUID(), buf.readUtf(64));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(action, 24);
        buf.writeUUID(target == null ? NONE : target);
        buf.writeUtf(text == null ? "" : text, 64);
    }

    @Override
    public Type<PartyActionPayload> type() {
        return TYPE;
    }
}
