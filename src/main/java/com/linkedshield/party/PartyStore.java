package com.linkedshield.party;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 全局小队表，挂在主世界（overworld）的 ServerLevel 数据附件上，随存档持久化。
 * 未接受的邀请只在内存里保存。
 */
public class PartyStore {

    public static final MapCodec<PartyStore> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Party.CODEC.listOf().optionalFieldOf("parties", List.of()).forGetter(PartyStore::parties)
    ).apply(instance, PartyStore::new));

    /** 成员 UUID -> 小队。 */
    private final Map<UUID, Party> byMember = new LinkedHashMap<>();
    /** 被邀请人 UUID -> 邀请。 */
    private final Map<UUID, PendingInvite> invites = new HashMap<>();

    public PartyStore() {
    }

    public PartyStore(List<Party> parties) {
        for (Party party : parties) {
            for (UUID member : party.getMembers()) {
                byMember.put(member, party);
            }
        }
    }

    public List<Party> parties() {
        return new ArrayList<>(new LinkedHashSet<>(byMember.values()));
    }

    public Party partyOf(UUID player) {
        return byMember.get(player);
    }

    public boolean inParty(UUID player) {
        return byMember.containsKey(player);
    }

    /**
     * 两名玩家是否互为队友：同一个 UUID 返回 false，任意一方没有小队也返回 false。
     * 只读内存表、不碰世界，是纯函数，自检可以无头调用。
     */
    public boolean areTeammates(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        Party party = byMember.get(a);
        return party != null && party == byMember.get(b);
    }

    /** 创建小队并让创建者成为队长。 */
    public Party create(UUID leader) {
        leave(leader);
        Party party = new Party(leader);
        byMember.put(leader, party);
        invites.remove(leader);
        return party;
    }

    public void join(Party party, UUID player) {
        leave(player);
        party.add(player);
        byMember.put(player, party);
        invites.remove(player);
    }

    /** 退出/被踢/解散后调用。 */
    public void leave(UUID player) {
        Party party = byMember.remove(player);
        if (party == null) {
            return;
        }
        party.remove(player);
        if (party.size() <= 1) {
            disband(party);
        }
    }

    /** 解散小队（清掉所有成员）。 */
    public void disband(Party party) {
        for (UUID member : new ArrayList<>(party.getMembers())) {
            byMember.remove(member);
        }
        party.getMembers().clear();
    }

    public void invite(UUID target, UUID inviter, long expireTick) {
        invites.put(target, new PendingInvite(inviter, expireTick));
    }

    public PendingInvite getInvite(UUID target) {
        return invites.get(target);
    }

    public void clearInvite(UUID target) {
        invites.remove(target);
    }

    public void tickInvites(long now) {
        invites.entrySet().removeIf(entry -> entry.getValue().expireTick() < now);
    }

    public record PendingInvite(UUID inviter, long expireTick) {
    }
}
