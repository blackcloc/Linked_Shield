package com.linkedshield.party;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 一个小队：队长 + 成员（队长也在成员里）。 */
public class Party {

    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<Party> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("leader").forGetter(Party::getLeader),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Party::getName),
            UUID_CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(party -> new ArrayList<>(party.members))
    ).apply(instance, Party::new));

    private UUID leader;
    /** 队伍名，空串表示用默认名（队长名 + 的小队）。 */
    private String name = "";
    private final Set<UUID> members = new LinkedHashSet<>();

    public Party(UUID leader) {
        this(leader, "", List.of(leader));
    }

    public Party(UUID leader, Collection<UUID> members) {
        this(leader, "", members);
    }

    public Party(UUID leader, String name, Collection<UUID> members) {
        this.leader = leader;
        this.name = name == null ? "" : name;
        this.members.add(leader);
        for (UUID member : members) {
            if (member != null) {
                this.members.add(member);
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    /** 界面上显示的名字：没自定义就用“队长名 的小队”。 */
    public String displayName(String leaderName) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return (leaderName == null || leaderName.isBlank() ? "???" : leaderName) + " 的小队";
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public boolean add(UUID player) {
        return members.add(player);
    }

    public void remove(UUID player) {
        members.remove(player);
        if (leader.equals(player) && !members.isEmpty()) {
            leader = members.iterator().next();
        }
    }

    /** 顺序为：队长在前，其余按加入顺序。 */
    public List<UUID> orderedMembers() {
        List<UUID> ordered = new ArrayList<>(members.size());
        ordered.add(leader);
        for (UUID member : members) {
            if (!member.equals(leader)) {
                ordered.add(member);
            }
        }
        return ordered;
    }
}
