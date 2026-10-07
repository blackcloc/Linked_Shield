package com.linkedshield.party;

import com.linkedshield.shield.LinkedShieldAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 小队读写入口（服务端）。数据存在主世界的数据附件里。 */
public final class PartyManager {

    private PartyManager() {
    }

    public static PartyStore store(MinecraftServer server) {
        return server.overworld().getData(LinkedShieldAttachments.PARTY_STORE);
    }

    public static Party partyOf(ServerPlayer player) {
        return store(player.level().getServer()).partyOf(player.getUUID());
    }

    /** 在线成员（含自己）。 */
    public static List<ServerPlayer> onlineMembers(MinecraftServer server, Party party) {
        List<ServerPlayer> result = new ArrayList<>();
        if (party == null) {
            return result;
        }
        for (UUID id : party.orderedMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null) {
                result.add(member);
            }
        }
        return result;
    }

    /** 广播一条消息给小队所有在线成员。 */
    public static void broadcast(MinecraftServer server, Party party, net.minecraft.network.chat.Component message) {
        for (ServerPlayer member : onlineMembers(server, party)) {
            member.sendSystemMessage(message);
        }
    }
}
