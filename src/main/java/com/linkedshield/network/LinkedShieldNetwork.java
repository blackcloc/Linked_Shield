package com.linkedshield.network;

import com.linkedshield.LinkedShieldMod;
import com.linkedshield.party.PartyService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 网络包注册与发送。客户端处理器在 LinkedShieldClient 里通过 RegisterClientPayloadHandlersEvent 注册。 */
public final class LinkedShieldNetwork {

    /** v2：party_state 包每个队友多带一排状态效果（PartyStatePayload.Member#effects）。 */
    public static final String NETWORK_VERSION = "2";

    private LinkedShieldNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(NETWORK_VERSION);
        // 服务端 -> 客户端：小队状态（HUD + 组队界面）
        registrar.playToClient(PartyStatePayload.TYPE, PartyStatePayload.STREAM_CODEC);
        // 客户端 -> 服务端：组队界面上的按钮动作
        registrar.playToServer(PartyActionPayload.TYPE, PartyActionPayload.STREAM_CODEC,
                LinkedShieldNetwork::handleAction);
    }

    private static void handleAction(PartyActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PartyService.handleAction(player, payload.action(), payload.target(), payload.text());
        }
    }

    public static void sendTo(ServerPlayer player, PartyStatePayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** 用于日志。 */
    public static String describe() {
        return LinkedShieldMod.MODID + ":party_state+party_action v" + NETWORK_VERSION;
    }
}
