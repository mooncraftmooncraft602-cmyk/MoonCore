package com.mooncore.modules.team;

import com.mooncore.api.team.TeamService;
import com.mooncore.command.sub.TeamSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TeamManager : équipes natives (création, invitations, membres). Implémente {@link TeamService}
 * — ce qui active les limites de spawners par équipe d'AntiFarm et les futurs classements par
 * équipe.
 */
@ModuleInfo(id = "team", name = "TeamManager")
public final class TeamManagerModule extends AbstractModule implements TeamService {

    public enum CreateResult { OK, ALREADY_IN_TEAM, INVALID_NAME, NAME_TAKEN }

    private record Invite(String teamId, long expiryMs) {}

    private final Map<String, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, String> memberIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    private TeamStore store;
    private int maxMembers;
    private long inviteTtlMs;

    @Override
    protected void onEnable() throws Exception {
        this.store = new TeamStore(data().database());
        data().applyMigrations(TeamStore.migrations());

        this.maxMembers = moduleConfig().getInt("max-members", 8);
        this.inviteTtlMs = moduleConfig().getLong("invite-ttl-seconds", 120) * 1000L;

        teams.putAll(store.loadAll());
        teams.values().forEach(t -> t.members().keySet().forEach(uuid -> memberIndex.put(uuid, t.id())));
        log().info("TeamManager : " + teams.size() + " équipe(s) chargée(s).");

        services().register(TeamService.class, this);
        plugin().rootCommand().register(new TeamSubCommand(this));
    }

    @Override
    protected void onDisable() {
        services().unregister(TeamService.class);
        teams.clear();
        memberIndex.clear();
        invites.clear();
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        this.maxMembers = moduleConfig().getInt("max-members", 8);
        this.inviteTtlMs = moduleConfig().getLong("invite-ttl-seconds", 120) * 1000L;
    }

    // ---- TeamService ----

    @Override
    public Optional<String> teamId(UUID player) {
        return Optional.ofNullable(memberIndex.get(player));
    }

    @Override
    public int memberCount(String teamId) {
        Team t = teams.get(teamId);
        return t == null ? 0 : t.size();
    }

    // ---- API gestion ----

    public Team team(UUID player) {
        String id = memberIndex.get(player);
        return id == null ? null : teams.get(id);
    }

    public Team byId(String id) { return teams.get(id); }
    public int maxMembers() { return maxMembers; }

    public CreateResult create(UUID owner, String name) {
        if (memberIndex.containsKey(owner)) return CreateResult.ALREADY_IN_TEAM;
        if (!TeamNames.isValid(name)) return CreateResult.INVALID_NAME;
        String id = TeamNames.toId(name);
        if (teams.containsKey(id)) return CreateResult.NAME_TAKEN;

        String seasonId = plugin().getConfig().getString("core.season-id", "season-1");
        Team team = new Team(id, name, owner, System.currentTimeMillis(), seasonId);
        teams.put(id, team);
        memberIndex.put(owner, id);
        store.saveTeam(team);
        return CreateResult.OK;
    }

    public boolean invite(UUID owner, UUID target) {
        Team t = team(owner);
        if (t == null || !t.isOwner(owner)) return false;
        if (memberIndex.containsKey(target)) return false;
        if (t.size() >= maxMembers) return false;
        invites.put(target, new Invite(t.id(), System.currentTimeMillis() + inviteTtlMs));
        return true;
    }

    public Team accept(UUID player) {
        if (memberIndex.containsKey(player)) return null;
        Invite inv = invites.remove(player);
        if (inv == null || System.currentTimeMillis() > inv.expiryMs()) return null;
        Team t = teams.get(inv.teamId());
        if (t == null || t.size() >= maxMembers) return null;
        t.addMember(player);
        memberIndex.put(player, t.id());
        store.addMember(t.id(), player, Team.ROLE_MEMBER);
        return t;
    }

    /** Quitte l'équipe ; si propriétaire, dissout l'équipe. Retourne le résultat. */
    public boolean leave(UUID player) {
        Team t = team(player);
        if (t == null) return false;
        if (t.isOwner(player)) {
            disband(t);
        } else {
            t.removeMember(player);
            memberIndex.remove(player);
            store.removeMember(t.id(), player);
        }
        return true;
    }

    public boolean kick(UUID owner, UUID target) {
        Team t = team(owner);
        if (t == null || !t.isOwner(owner) || owner.equals(target) || !t.isMember(target)) return false;
        t.removeMember(target);
        memberIndex.remove(target);
        store.removeMember(t.id(), target);
        return true;
    }

    public boolean disband(UUID owner) {
        Team t = team(owner);
        if (t == null || !t.isOwner(owner)) return false;
        disband(t);
        return true;
    }

    private void disband(Team t) {
        for (UUID member : t.members().keySet()) {
            memberIndex.remove(member);
        }
        teams.remove(t.id());
        store.deleteTeam(t.id());
    }

    public java.util.Collection<Team> all() { return teams.values(); }
}
