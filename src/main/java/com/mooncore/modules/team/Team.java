package com.mooncore.modules.team;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Une équipe : identité, propriétaire et membres (avec rôle). Données pures, testables. */
public final class Team {

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";

    private final String id;
    private String name;
    private UUID owner;
    private final long createdAt;
    private final String seasonId;
    private final Map<UUID, String> members = new ConcurrentHashMap<>();

    public Team(String id, String name, UUID owner, long createdAt, String seasonId) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.createdAt = createdAt;
        this.seasonId = seasonId;
        this.members.put(owner, ROLE_OWNER);
    }

    public void addMember(UUID uuid) { members.putIfAbsent(uuid, ROLE_MEMBER); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public boolean isMember(UUID uuid) { return members.containsKey(uuid); }
    public String role(UUID uuid) { return members.get(uuid); }
    public boolean isOwner(UUID uuid) { return uuid.equals(owner); }
    public int size() { return members.size(); }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID owner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; members.put(owner, ROLE_OWNER); }
    public long createdAt() { return createdAt; }
    public String seasonId() { return seasonId; }
    public Map<UUID, String> members() { return Collections.unmodifiableMap(members); }
}
