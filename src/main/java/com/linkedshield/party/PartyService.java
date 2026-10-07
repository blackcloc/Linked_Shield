package com.linkedshield.party;

import com.linkedshield.config.ConfigManager;
import com.linkedshield.network.PartySync;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 小队操作的服务端实现。指令（/party ...）和组队界面（C2S 动作包）都走这里，
 * 保证两条入口行为一致。
 */
public final class PartyService {

    private PartyService() {
    }

    /* ------------------------------------------------------------------ 动作入口 */

    /** 组队界面按钮走这里。 */
    public static void handleAction(ServerPlayer player, String action, UUID target, String text) {
        switch (action == null ? "" : action.toUpperCase()) {
            case "INVITE" -> {
                ServerPlayer targetPlayer = player.level().getServer().getPlayerList().getPlayer(target);
                if (targetPlayer != null) {
                    invite(player, targetPlayer);
                }
            }
            case "ACCEPT" -> accept(player);
            case "DENY" -> deny(player);
            case "LEAVE" -> leave(player);
            case "DISBAND" -> disband(player);
            case "KICK" -> {
                ServerPlayer targetPlayer = player.level().getServer().getPlayerList().getPlayer(target);
                if (targetPlayer != null) {
                    kick(player, targetPlayer);
                }
            }
            case "RENAME" -> rename(player, text);
            default -> {
            }
        }
    }

    /* ------------------------------------------------------------------ 具体操作 */

    public static boolean invite(ServerPlayer sender, ServerPlayer target) {
        MinecraftServer server = sender.level().getServer();
        PartyStore store = PartyManager.store(server);
        int maxSize = ConfigManager.get().party.maxPartySize;

        if (target.getUUID().equals(sender.getUUID())) {
            sender.sendSystemMessage(Component.translatable("linkedshield.command.party.self_invite"));
            return false;
        }
        if (store.inParty(target.getUUID())) {
            sender.sendSystemMessage(Component.translatable("linkedshield.command.party.already_in",
                    target.getName().getString()));
            return false;
        }
        Party party = store.partyOf(sender.getUUID());
        if (party == null) {
            party = store.create(sender.getUUID());
            PartySync.syncParty(server, party);
        } else if (party.size() >= maxSize) {
            sender.sendSystemMessage(Component.translatable("linkedshield.command.party.full", String.valueOf(maxSize)));
            return false;
        }
        store.invite(target.getUUID(), sender.getUUID(),
                server.getTickCount() + ConfigManager.get().party.inviteTimeoutSeconds * 20L);
        sender.sendSystemMessage(Component.translatable("linkedshield.command.party.invited",
                target.getName().getString()));
        target.sendSystemMessage(Component.translatable("linkedshield.command.party.invite.received",
                sender.getName().getString()));
        // 让被邀请者的客户端立刻出现右上角小点
        PartySync.sendStateNow(target);
        return true;
    }

    public static boolean accept(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        PartyStore store = PartyManager.store(server);
        PartyStore.PendingInvite invite = store.getInvite(player.getUUID());
        if (invite == null) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.no_invite"));
            return false;
        }
        store.clearInvite(player.getUUID());
        if (invite.expireTick() < server.getTickCount()) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.expired"));
            return false;
        }
        ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviter());
        Party party = inviter == null ? null : store.partyOf(invite.inviter());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.inviter_gone"));
            return false;
        }
        int maxSize = ConfigManager.get().party.maxPartySize;
        if (party.size() >= maxSize) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.full", String.valueOf(maxSize)));
            return false;
        }
        store.join(party, player.getUUID());
        player.sendSystemMessage(Component.translatable("linkedshield.command.party.accepted"));
        PartyManager.broadcast(server, party, Component.translatable("linkedshield.command.party.joined",
                player.getName().getString()));
        PartySync.syncParty(server, party);
        PartySync.sendStateNow(player);
        return true;
    }

    public static boolean deny(ServerPlayer player) {
        PartyStore store = PartyManager.store(player.level().getServer());
        if (store.getInvite(player.getUUID()) == null) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.no_invite"));
            return false;
        }
        store.clearInvite(player.getUUID());
        player.sendSystemMessage(Component.translatable("linkedshield.command.party.denied"));
        PartySync.sendStateNow(player);
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        PartyStore store = PartyManager.store(server);
        Party party = store.partyOf(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.not_in_party"));
            return false;
        }
        store.leave(player.getUUID());
        player.sendSystemMessage(Component.translatable("linkedshield.command.party.left"));
        PartyManager.broadcast(server, party, Component.translatable("linkedshield.command.party.left.broadcast",
                player.getName().getString()));
        PartySync.syncParty(server, party);
        PartySync.sendStateNow(player);
        return true;
    }

    public static boolean disband(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        PartyStore store = PartyManager.store(server);
        Party party = store.partyOf(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.not_in_party"));
            return false;
        }
        if (!party.isLeader(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.not_leader"));
            return false;
        }
        PartyManager.broadcast(server, party, Component.translatable("linkedshield.command.party.disbanded"));
        store.disband(party);
        PartySync.syncParty(server, party);
        PartySync.sendStateNow(player);
        return true;
    }

    public static boolean kick(ServerPlayer player, ServerPlayer target) {
        MinecraftServer server = player.level().getServer();
        PartyStore store = PartyManager.store(server);
        Party party = store.partyOf(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.not_in_party"));
            return false;
        }
        if (!party.isLeader(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.not_leader"));
            return false;
        }
        if (target.getUUID().equals(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.kick_self"));
            return false;
        }
        if (!party.contains(target.getUUID())) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.not_member",
                    target.getName().getString()));
            return false;
        }
        store.leave(target.getUUID());
        player.sendSystemMessage(Component.translatable("linkedshield.command.party.kicked",
                target.getName().getString()));
        target.sendSystemMessage(Component.translatable("linkedshield.command.party.kicked.target"));
        PartySync.syncParty(server, party);
        PartySync.sendStateNow(target);
        return true;
    }

    /** 队长给队伍改名（最多 24 个字符）。 */
    public static boolean rename(ServerPlayer player, String text) {
        MinecraftServer server = player.level().getServer();
        PartyStore store = PartyManager.store(server);
        Party party = store.partyOf(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.not_in_party"));
            return false;
        }
        if (!party.isLeader(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("linkedshield.command.party.not_leader"));
            return false;
        }
        String name = text == null ? "" : text.trim();
        if (name.length() > 24) {
            name = name.substring(0, 24);
        }
        party.setName(name);
        PartyManager.broadcast(server, party, Component.translatable("linkedshield.command.party.renamed",
                party.displayName(player.getName().getString())));
        PartySync.syncParty(server, party);
        return true;
    }
}
