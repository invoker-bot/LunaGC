package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.activity.crucible.CrucibleMatchmaking.*;
import emu.grasscutter.game.activity.crucible.CrucibleSceneController.MatchingTeam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.MatchTypeOuterClass.MatchType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.*;
import java.util.*;

/** Invoked outside scene/world monitors. Transfer operations never run from Lua or a scene tick. */
public final class CrucibleMatchSystem {
    private static final int TYPE = MatchType.MatchType_MP_PLAY.getNumber();
    private final GameServer server;
    private final CrucibleMatchmaking queue = new CrucibleMatchmaking(2, 4, 5, 300, 30, 60);
    private final Map<Integer, MatchingTeam> teams = new HashMap<>();
    private final Map<Long, World> destinations = new HashMap<>();

    public CrucibleMatchSystem(GameServer server) { this.server = server; }
    public boolean isReserved(Player player) { return queue.runFor(player.getUid()) != null; }

    public synchronized void start(Player owner, int type, int playId, int matchId, int dungeonId) {
        int retcode = Retcode.RET_MP_MATCH_PLAY_NOT_OPEN.getNumber();
        MatchingTeam team = null;
        if (type == TYPE && playId == 1 && matchId == 0 && dungeonId == 0) {
            if (queue.runFor(owner.getUid()) != null) retcode = Retcode.RET_MATCH_ALREADY_IN_MATCH.getNumber();
            else if (owner.getWorld() == null || owner.getWorld().getHost() != owner)
                retcode = Retcode.RET_MP_NOT_IN_MY_WORLD.getNumber();
            else if (owner.getScene() != null) {
                team = owner.getScene().getCrucibleSceneController().matchingTeam(owner, playId);
                if (team != null && current(team) && queue.enqueue(new Party(owner.getUid(),
                        team.context().scheduleId(), team.context().members()), now())) {
                    teams.put(owner.getUid(), team);
                    retcode = 0;
                } else retcode = Retcode.RET_MP_GUEST_MATCH_COND_NOT_MEET.getNumber();
            }
        }
        owner.sendPacket(PacketCrucibleMatch.start(type, playId, matchId, dungeonId, retcode));
        if (retcode == 0) send(team.players(), PacketCrucibleMatch.info(owner.getUid()));
        else if (team != null && queue.runFor(owner.getUid()) == null)
            team.source().getCrucibleSceneController().releaseMatching(team.serial());
    }

    public synchronized void cancelRequest(Player player, int type) {
        Run run = type == TYPE ? queue.cancel(player.getUid()) : null;
        player.sendPacket(PacketCrucibleMatch.cancel(type,
                run == null ? Retcode.RET_MATCH_NOT_IN_MATCH.getNumber() : 0));
        if (run != null) stop(run, 2);
    }

    public synchronized void cancel(Player player) {
        Run run = queue.cancel(player.getUid());
        if (run != null) stop(run, 7);
    }

    public synchronized void confirm(Player player, int type, boolean agree, boolean guest) {
        Run run = queue.runFor(player.getUid());
        boolean correctRole = player.getWorld() != null && (player.getWorld().getHost() != player) == guest;
        Reply reply = type == TYPE && correctRole && run != null && validSources(run)
                ? queue.confirm(player.getUid(), agree, now()) : Reply.INVALID;
        int retcode = reply == Reply.INVALID ? Retcode.RET_MATCH_NOT_IN_MATCH.getNumber()
                : reply == Reply.TIMED_OUT ? Retcode.RET_MP_REPLY_TIMEOUT.getNumber() : 0;
        player.sendPacket(PacketCrucibleMatch.confirm(type, agree, retcode, guest));
        if (run == null) return;
        if (reply == Reply.DECLINED || reply == Reply.TIMED_OUT) {
            queue.cancel(player.getUid()); stop(run, reply == Reply.TIMED_OUT ? 9 : 2);
        } else if (reply == Reply.ALL_AGREED) send(members(run), PacketCrucibleMatch.agreed(run.hostUid()));
    }

    /** The client sends this after the successful agreed-result notification, including the host. */
    public synchronized void allowEnter(Player player, int targetUid) {
        Run run = queue.runFor(player.getUid());
        if (run != null && validSources(run)) queue.allowEnter(player.getUid(), targetUid, now());
    }

    public synchronized void onTick() {
        long now = now();
        for (Run expired : queue.expire(now)) stop(expired,
                expired.stage() == Stage.CONFIRMING ? 9 : expired.stage() == Stage.QUEUED ? 3 : 7);
        for (Run run : queue.runs()) {
            boolean loading = run.stage() == Stage.LOADING;
            boolean valid = active(run.scheduleId()) && (loading ? validDestination(run) : validSources(run));
            if (!valid) { queue.cancel(run.members().keySet().iterator().next()); stop(run, 7); continue; }
            if (run.stage() == Stage.JOINING && queue.beginTransfer(run.id(), now)) {
                try { transfer(run); queue.transferred(run.id(), now); }
                catch (RuntimeException exception) {
                    queue.cancel(run.members().keySet().iterator().next()); stop(run, 6);
                    Grasscutter.getLogger().error("Crucible match {} could not transfer its team", run.id(), exception);
                }
            } else if (loading) {
                Player host = server.getPlayerByUid(run.hostUid());
                var players = members(run);
                if (players.stream().allMatch(p -> p.getSceneLoadState() == SceneLoadState.LOADED)
                        && host.getScene().getCrucibleSceneController().startMatchedPreparation(host, run.scheduleId(), players)) {
                    queue.complete(run.id());
                    release(run);
                    send(players, PacketCrucibleMatch.stop(run.hostUid(), 1));
                }
            }
        }
        // The MP invitation itself starts matchmaking once its fixed party has agreed.
        // No matchmaking callback is invoked while a scene holds its monitor.
        for (World world : List.copyOf(server.getWorlds())) {
            Player host = world.getHost();
            if (host == null || host.getWorld() != world || host.getScene() == null || queue.runFor(host.getUid()) != null) continue;
            var team = host.getScene().getCrucibleSceneController().matchingTeam(host, 1, false);
            if (team != null && current(team) && queue.enqueue(new Party(host.getUid(),
                    team.context().scheduleId(), team.context().members()), now)) {
                teams.put(host.getUid(), team);
                send(team.players(), PacketCrucibleMatch.info(host.getUid()));
            }
        }
        for (Run match : queue.findMatches(now))
            send(members(match), PacketCrucibleMatch.success(match.hostUid(), Math.toIntExact(match.deadline())));
    }

    private boolean active(int scheduleId) {
        return CrucibleSceneLifecycle.activeSchedule(ActivityManager.getScheduleActivityConfigMap().get(scheduleId),
                System.currentTimeMillis()) == scheduleId;
    }
    private boolean current(MatchingTeam team) {
        return team.players().stream().allMatch(p -> p.isOnline() && server.getPlayerByUid(p.getUid()) == p)
                && team.source().getCrucibleSceneController().isMatching(team);
    }
    private boolean validSources(Run run) {
        return run.parties().stream().allMatch(p -> {
            var team = teams.get(p.ownerUid());
            return team != null && team.context().scheduleId() == run.scheduleId()
                    && team.context().members().equals(p.members()) && current(team);
        });
    }
    private List<Player> members(Run run) {
        return run.parties().stream().map(p -> teams.get(p.ownerUid())).filter(Objects::nonNull)
                .flatMap(t -> t.players().stream()).toList();
    }
    private boolean validDestination(Run run) {
        World world = destinations.get(run.id());
        var players = members(run);
        return world != null && world.getHost().getUid() == run.hostUid()
                && world.getWorldLevel() == run.members().get(run.hostUid())
                && players.size() == run.members().size() && new HashSet<>(world.getPlayers()).equals(new HashSet<>(players))
                && players.stream().allMatch(p -> p.isOnline() && server.getPlayerByUid(p.getUid()) == p
                        && p.getWorldLevel() == run.members().get(p.getUid())
                        && p.getWorld() == world && p.getScene() != null && p.getScene().getId() == 3);
    }

    private void transfer(Run run) {
        if (!validSources(run)) throw new IllegalStateException("Matching party changed before transfer");
        Player host = server.getPlayerByUid(run.hostUid());
        var players = members(run);
        // Invalidate source invitations before World.addPlayer removes anyone from those scenes.
        run.parties().forEach(p -> {
            var team = teams.get(p.ownerUid());
            team.source().getCrucibleSceneController().releaseMatching(team.serial());
        });
        World world = host.getWorld();
        if (!world.isMultiplayer()) {
            world = new World(host, true);
            world.addPlayer(host);
            host.sendPacket(new PacketPlayerEnterSceneNotify(host, host, EnterType.EnterType_ENTER_OTHER,
                    EnterReason.HostFromSingleToMp, 3, host.getPosition()));
            host.sendPacket(new PacketEnterScenePeerNotify(host));
        }
        destinations.put(run.id(), world);
        for (Party party : run.parties()) {
            // Move guests before their old host; removing the host first would kick the remaining party.
            var ordered = players.stream().filter(p -> party.members().containsKey(p.getUid()))
                    .sorted(Comparator.comparing(p -> p.getUid() == party.ownerUid())).toList();
            for (Player player : ordered) {
                if (player.getWorld() == world) continue;
                player.getPosition().set(host.getPosition());
                player.getRotation().set(host.getRotation());
                world.addPlayer(player, 3);
                player.sendPacket(new PacketPlayerEnterSceneNotify(player, host, EnterType.EnterType_ENTER_OTHER,
                        EnterReason.MpPlay, 3, host.getPosition()));
                player.sendPacket(new PacketEnterScenePeerNotify(player));
            }
        }
    }

    private void stop(Run run, int reason) {
        send(members(run), PacketCrucibleMatch.stop(run.hostUid(), reason));
        release(run);
    }
    private void release(Run run) {
        destinations.remove(run.id());
        for (Party party : run.parties()) {
            var team = teams.remove(party.ownerUid());
            if (team != null) team.source().getCrucibleSceneController().releaseMatching(team.serial());
        }
    }
    private void send(List<Player> players, BasePacket packet) {
        players.stream().filter(Player::isOnline).forEach(p -> p.sendPacket(packet));
    }
    private static long now() { return System.currentTimeMillis() / 1000; }
}
