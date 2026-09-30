package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.MpPlayRewardInfoOuterClass.MpPlayRewardInfo;
import emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import java.util.concurrent.Future;

/** Mounts the archived Crucible groups without changing the shared map metadata or group grid. */
public final class CrucibleSceneController implements CrucibleSceneLifecycle.Groups {
    public static final int MAIN_GROUP = 305001001;
    public static final Set<Integer> GROUP_IDS = Set.of(MAIN_GROUP, 133003549, 133003550,
            133003554, 133003555, 133003556, 133003557, 133003568);
    private final Scene scene;
    private final CrucibleSceneLifecycle lifecycle = new CrucibleSceneLifecycle(this);
    private final Map<Integer, SceneGroup> definitions = new LinkedHashMap<>();
    private volatile EntityGadget roundGadget;
    private final CrucibleInvitation invitation = new CrucibleInvitation();
    private final CrucibleRewards rewards = new CrucibleRewards();
    private final Set<Integer> departedRoundMembers = new HashSet<>();
    private EntityGadget rewardPoint;
    public static final int REWARD_GADGET = 70330039;
    private static final int INVITE_SECONDS = 30;
    private Future<?> prepareCallback, battleCallback, interruptCallback;

    public CrucibleSceneController(Scene scene) { this.scene = scene; }
    public CrucibleSceneLifecycle getLifecycle() { return lifecycle; }
    public boolean owns(int groupId) { return scene.getId() == 3 && GROUP_IDS.contains(groupId); }
    public boolean allows(int groupId) { return !owns(groupId) || lifecycle.isMounted(); }

    public void update(long nowMs) {
        if (scene.getId() != 3) return;
        var config = ActivityManager.getScheduleActivityConfigMap().values().stream()
                .filter(item -> item.getActivityId() == 5001).findFirst().orElse(null);
        int schedule = CrucibleSceneLifecycle.activeSchedule(config, nowMs);
        var center = mainPosition();
        int range = Grasscutter.getConfig().server.game.loadEntitiesForPlayerRange;
        double distance = Math.max(250, range);
        boolean nearby = scene.getPlayers().stream().anyMatch(player -> {
            var pos = player.getPosition();
            double x = pos.getX() - center.getX(), z = pos.getZ() - center.getZ();
            return x * x + z * z <= distance * distance;
        });
        var entity = scene.getEntityByConfigId(1001, MAIN_GROUP);
        synchronized (scene) {
            boolean busy = !scene.getPlayers().isEmpty() && (invitation.phase() != CrucibleInvitation.Phase.IDLE
                    || entity instanceof EntityGadget gadget && gadget.getGadgetPlayState().isActive()
                    || rewards.hasRemaining());
            lifecycle.update(schedule, nearby, busy);
            updateInvitation(nowMs / 1000);
        }
    }

    public void close() { synchronized (scene) { lifecycle.close(); } }

    private record Check(int retcode, int wrongUid) {
        static final Check OK = new Check(0, 0);
        Check(Retcode code, int uid) { this(code.getNumber(), uid); }
    }

    private EntityGadget mainGadget() {
        var entity = scene.getEntityByConfigId(1001, MAIN_GROUP);
        return entity instanceof EntityGadget gadget ? gadget : null;
    }

    private boolean scheduleCurrent() {
        var config = ActivityManager.getScheduleActivityConfigMap().get(lifecycle.scheduleId());
        return scene.getId() == 3 && lifecycle.isMounted()
                && CrucibleSceneLifecycle.activeSchedule(config, System.currentTimeMillis()) == lifecycle.scheduleId();
    }

    private Check checkMember(Player player) {
        if (player.getWorld() != scene.getWorld() || player.getScene() != scene || !scene.getPlayers().contains(player)
                || player.getSceneLoadState() != Player.SceneLoadState.LOADED)
            return new Check(Retcode.RET_MP_GUEST_LOADING_FIRST_ENTER, player.getUid());
        var data = GameData.getMpPlayGroupDataMap().get(1);
        if (data == null || data.getRadius() <= 0) return new Check(Retcode.RET_MP_PLAY_NOT_ACTIVE, player.getUid());
        var center = data.centerPosition();
        var pos = player.getPosition();
        double x = pos.getX() - center.getX(), z = pos.getZ() - center.getZ();
        if (x * x + z * z > (double) data.getRadius() * data.getRadius())
            return new Check(Retcode.RET_MP_OWNER_NOT_ENTER, player.getUid());
        if (player.getTeamManager().getActiveTeam().stream()
                .noneMatch(avatar -> avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) > 0))
            return new Check(Retcode.RET_MP_REPLY_NO_VALID_AVATAR, player.getUid());
        return Check.OK;
    }

    private Check checkOwner(Player owner, int playId, boolean skipMatch, List<Player> members) {
        if (owner != scene.getWorld().getHost() || owner.getScene() != scene)
            return new Check(Retcode.RET_MP_NOT_IN_MY_WORLD, owner.getUid());
        var gadget = mainGadget();
        if (playId != 1 || !scheduleCurrent() || gadget == null)
            return new Check(Retcode.RET_MP_PLAY_NOT_ACTIVE, 0);
        if (invitation.phase() != CrucibleInvitation.Phase.IDLE || !gadget.canStartGadgetPlay()
                || interruptCallback != null && !interruptCallback.isDone())
            return new Check(Retcode.RET_MP_IN_MP_PLAY_BATTLE, 0);
        var match = GameData.getMpPlayMatchDataMap().get(1);
        if (!skipMatch && (match == null || !match.isAutoMatch() || !"MP_PLAY_CRUCIBLE".equals(match.getPlayType())
                || match.getMinPlayers() != 2 || match.getMaxPlayers() != 4))
            return new Check(Retcode.RET_MP_MATCH_PLAY_NOT_OPEN, 0);
        if (members.isEmpty() || members.size() > 4 || !members.contains(owner))
            return new Check(Retcode.RET_MP_WORLD_IS_FULL, 0);
        for (var member : members) {
            var check = checkMember(member);
            if (check.retcode() != 0) return check;
        }
        return Check.OK;
    }

    public void checkOwnerRequest(Player owner, int playId, boolean skipMatch) {
        synchronized (scene) {
            var check = checkOwner(owner, playId, skipMatch, List.copyOf(scene.getWorld().getPlayers()));
            if (check.retcode() == 0 && rewards.hasRemaining(owner.getUid()))
                check = new Check(Retcode.RET_MP_PLAY_REMAIN_REWARDS, owner.getUid());
            owner.sendPacket(PacketMpPlay.ownerCheck(playId, skipMatch, check.retcode(), check.wrongUid()));
        }
    }

    public void startInvitation(Player owner, int playId, boolean skipMatch) {
        synchronized (scene) {
            var players = List.copyOf(scene.getWorld().getPlayers());
            var check = checkOwner(owner, playId, skipMatch, players);
            owner.sendPacket(PacketMpPlay.startInvite(playId, skipMatch, check.retcode()));
            if (check.retcode() != 0) return;
            var members = new HashMap<Integer, Integer>();
            for (var member : players) members.put(member.getUid(), member.getWorldLevel());
            invitation.start(new CrucibleInvitation.Context(lifecycle.scheduleId(), lifecycle.ticket(), owner.getUid(), members),
                    System.currentTimeMillis() / 1000, INVITE_SECONDS, GameData.getMpPlayGroupDataMap().get(1).getPrepareTime(), skipMatch);
            prepareCallback = battleCallback = null;
            if (invitation.phase() == CrucibleInvitation.Phase.PREPARING) beginPreparation();
            else if (invitation.phase() == CrucibleInvitation.Phase.MATCHING) sendToMembers(PacketMpPlay.inviteResult(1, true));
            else for (var member : scene.getPlayers())
                if (members.containsKey(member.getUid())) member.sendPacket(
                        PacketMpPlay.ownerInvite(1, INVITE_SECONDS, rewards.hasRemaining(member.getUid())));
        }
    }

    public void replyInvitation(Player guest, int playId, boolean agree) {
        synchronized (scene) {
            var context = invitation.context();
            if (playId != 1 || context == null || !scheduleCurrent() || !lifecycle.isCurrent(context.sceneTicket())
                    || guest.getScene() != scene || !scene.getPlayers().contains(guest)) {
                guest.sendPacket(PacketMpPlay.guestReplyResponse(playId, Retcode.RET_MP_REPLY_TIMEOUT.getNumber()));
                return;
            }
            if (invitation.phase() == CrucibleInvitation.Phase.INVITING && context.members().containsKey(guest.getUid())
                    && guest.getUid() != context.ownerUid() && agree) {
                var check = checkMember(guest);
                if (check.retcode() != 0) {
                    guest.sendPacket(PacketMpPlay.guestReplyResponse(playId, check.retcode()));
                    cancelInvitation(true);
                    return;
                }
            }
            var reply = invitation.reply(guest.getUid(), agree, System.currentTimeMillis() / 1000);
            guest.sendPacket(PacketMpPlay.guestReplyResponse(playId,
                    reply == CrucibleInvitation.Reply.INVALID || reply == CrucibleInvitation.Reply.TIMED_OUT
                            ? Retcode.RET_MP_REPLY_TIMEOUT.getNumber() : 0));
            if (reply == CrucibleInvitation.Reply.INVALID) return;
            if (reply != CrucibleInvitation.Reply.TIMED_OUT) sendToMembers(PacketMpPlay.guestReply(1, guest.getUid(), agree));
            if (reply == CrucibleInvitation.Reply.REJECTED || reply == CrucibleInvitation.Reply.TIMED_OUT)
                sendToMembers(PacketMpPlay.inviteResult(1, false));
            else if (reply == CrucibleInvitation.Reply.ALL_AGREED) {
                if (invitation.phase() == CrucibleInvitation.Phase.MATCHING) sendToMembers(PacketMpPlay.inviteResult(1, true));
                else beginPreparation();
            }
        }
    }

    public record MatchingTeam(Scene source, CrucibleInvitation.Context context, long serial, List<Player> players) {
        public MatchingTeam { players = List.copyOf(players); }
    }

    /** Direct solo matchmaking is allowed; a party must have completed the invitation first. */
    public MatchingTeam matchingTeam(Player owner, int playId) {
        return matchingTeam(owner, playId, true);
    }

    public MatchingTeam matchingTeam(Player owner, int playId, boolean directSolo) {
        synchronized (scene) {
            var players = List.copyOf(scene.getWorld().getPlayers());
            if (directSolo && invitation.phase() == CrucibleInvitation.Phase.IDLE && players.size() == 1
                    && checkOwner(owner, playId, false, players).retcode() == 0) {
                invitation.start(new CrucibleInvitation.Context(lifecycle.scheduleId(), lifecycle.ticket(), owner.getUid(),
                        Map.of(owner.getUid(), owner.getWorldLevel())), System.currentTimeMillis() / 1000,
                        INVITE_SECONDS, GameData.getMpPlayGroupDataMap().get(1).getPrepareTime(), false);
            }
            var context = invitation.context();
            if (playId != 1 || owner != scene.getWorld().getHost() || context == null
                    || invitation.phase() != CrucibleInvitation.Phase.MATCHING || context.ownerUid() != owner.getUid()) return null;
            var snapshot = new MatchingTeam(scene, context, invitation.serial(), players);
            return isMatching(snapshot) ? snapshot : null;
        }
    }

    public boolean isMatching(MatchingTeam team) {
        synchronized (scene) {
            var gadget = mainGadget();
            return team.source() == scene && invitation.isCurrent(team.serial())
                    && invitation.phase() == CrucibleInvitation.Phase.MATCHING && invitation.context().equals(team.context())
                    && lifecycle.isCurrent(team.context().sceneTicket()) && scheduleCurrent()
                    && scene.getWorld().getHost().getUid() == team.context().ownerUid()
                    && scene.getWorld().getWorldLevel() == team.context().members().get(team.context().ownerUid())
                    && gadget != null && gadget.canStartGadgetPlay()
                    && List.copyOf(scene.getWorld().getPlayers()).size() == team.players().size()
                    && team.players().stream().allMatch(player -> team.context().members().containsKey(player.getUid())
                            && player.getWorldLevel() == team.context().members().get(player.getUid())
                            && checkMember(player).retcode() == 0);
        }
    }

    /** Does not call matchmaking under the scene lock; the server tick observes invalidation. */
    public void releaseMatching(long serial) {
        synchronized (scene) {
            if (invitation.isCurrent(serial) && invitation.phase() == CrucibleInvitation.Phase.MATCHING) invitation.cancel();
        }
    }

    public boolean startMatchedPreparation(Player owner, int scheduleId, List<Player> players) {
        synchronized (scene) {
            if (scheduleId != lifecycle.scheduleId() || checkOwner(owner, 1, true, players).retcode() != 0
                    || !new HashSet<>(scene.getWorld().getPlayers()).equals(new HashSet<>(players))) return false;
            var members = new HashMap<Integer, Integer>();
            players.forEach(player -> members.put(player.getUid(), player.getWorldLevel()));
            if (!invitation.startPrepared(new CrucibleInvitation.Context(scheduleId, lifecycle.ticket(), owner.getUid(), members),
                    System.currentTimeMillis() / 1000, GameData.getMpPlayGroupDataMap().get(1).getPrepareTime())) return false;
            prepareCallback = battleCallback = null;
            beginPreparation();
            return true;
        }
    }

    private void sendToMembers(BasePacket packet) {
        var context = invitation.context();
        if (context != null) for (var player : scene.getPlayers())
            if (context.members().containsKey(player.getUid())) player.sendPacket(packet);
    }

    private void beginPreparation() {
        sendToMembers(PacketMpPlay.inviteResult(1, true));
        scene.broadcastPacket(new PacketMpPlayPrepareNotify(1, invitation.prepareEndTime()));
        prepareCallback = scene.getScriptManager().callEvent(new ScriptArgs(MAIN_GROUP, EventType.EVENT_MP_PLAY_PREPARE, 1));
    }

    private void updateInvitation(long now) {
        var phase = invitation.phase();
        if (phase == CrucibleInvitation.Phase.IDLE || phase == CrucibleInvitation.Phase.BATTLE) return;
        var context = invitation.context();
        boolean present = context != null && lifecycle.isCurrent(context.sceneTicket()) && scheduleCurrent()
                && scene.getWorld().getHost().getUid() == context.ownerUid()
                && context.members().keySet().stream().allMatch(uid -> scene.getPlayers().stream()
                        .anyMatch(player -> player.getUid() == uid && checkMember(player).retcode() == 0));
        if (!present || mainGadget() == null) { cancelInvitation(true); return; }
        if (invitation.expire(now)) { sendToMembers(PacketMpPlay.inviteResult(1, false)); return; }
        if (phase == CrucibleInvitation.Phase.PREPARING && prepareCallback != null && prepareCallback.isDone()
                && invitation.beginBattle(now)) {
            battleCallback = scene.getScriptManager().callEvent(new ScriptArgs(MAIN_GROUP, EventType.EVENT_MP_PLAY_BATTLE, 1));
        } else if (phase == CrucibleInvitation.Phase.STARTING && battleCallback != null && battleCallback.isDone()) {
            // A missing/failed Lua trigger must not leave clients stuck in preparation indefinitely.
            cancelInvitation(true);
        }
    }

    private void cancelInvitation(boolean script) {
        var phase = invitation.phase();
        if (!invitation.cancel()) return;
        if (phase == CrucibleInvitation.Phase.INVITING) sendToMembers(PacketMpPlay.inviteResult(1, false));
        else if (phase != CrucibleInvitation.Phase.BATTLE && phase != CrucibleInvitation.Phase.MATCHING) {
            scene.broadcastPacket(PacketMpPlay.interrupt(1));
            if (script && lifecycle.isMounted()) interruptCallback = scene.getScriptManager()
                    .callEvent(new ScriptArgs(MAIN_GROUP, EventType.EVENT_MP_PLAY_PREPARE_INTERRUPT, 1));
            var prepareGadget = scene.getEntityByConfigId(1015, MAIN_GROUP);
            if (prepareGadget != null) scene.removeEntity(prepareGadget);
        }
    }

    public void onPlayerLeaving(Player player) {
        synchronized (scene) {
            if (roundGadget != null && roundGadget.getGadgetPlayState().isActive()
                    && roundGadget.getGadgetPlayState().getRound().participantWorldLevels().containsKey(player.getUid()))
                departedRoundMembers.add(player.getUid());
            rewards.forfeit(player.getUid());
            refreshRewardPoint();
            var context = invitation.context();
            if (context == null || !context.members().containsKey(player.getUid())) return;
            if (invitation.phase() == CrucibleInvitation.Phase.BATTLE) {
                if (context.ownerUid() == player.getUid() && roundGadget != null) roundGadget.stopGadgetPlay();
            } else cancelInvitation(true);
        }
    }

    public long invitationSerial() { return invitation.serial(); }
    public boolean allowsInvitationEvent(long serial, int event) {
        return switch (event) {
            case EventType.EVENT_MP_PLAY_PREPARE -> invitation.acceptsEvent(serial, CrucibleInvitation.Event.PREPARE);
            case EventType.EVENT_MP_PLAY_BATTLE -> invitation.acceptsEvent(serial, CrucibleInvitation.Event.BATTLE);
            case EventType.EVENT_MP_PLAY_PREPARE_INTERRUPT -> invitation.acceptsEvent(serial, CrucibleInvitation.Event.INTERRUPT);
            default -> true;
        };
    }

    public int prepareEndTime(EntityGadget gadget) {
        return gadget.getGroupId() == MAIN_GROUP && gadget.getConfigId() == 1001 ? invitation.prepareEndTime() : 0;
    }

    public void onRoundStarted() {
        closeRewards();
        departedRoundMembers.clear();
        invitation.battleStarted();
    }

    /** Scene entity operations take the scene monitor first; use the same order for Lua callbacks. */
    public void runIfCurrent(long ticket, Runnable callback) {
        runIfCurrent(ticket, -1, callback);
    }

    public void runIfCurrent(long ticket, long roundSerial, Runnable callback) {
        synchronized (scene) {
            synchronized (lifecycle) {
                if (lifecycle.isCurrent(ticket)
                        && (roundSerial < 0 || currentRoundSerial() == roundSerial)) callback.run();
            }
        }
    }

    public long currentRoundSerial() {
        var gadget = roundGadget;
        return gadget == null ? 0 : gadget.getGadgetPlayState().getRoundSerial();
    }

    public boolean isRoundActive(long serial) {
        var gadget = roundGadget;
        return gadget != null && gadget.getGadgetPlayState().getRoundSerial() == serial
                && gadget.getGadgetPlayState().isActive();
    }

    /** Called with the scene monitor, immediately before invoking the installed gadget controller. */
    public boolean acceptsClientSubmission(Player player, EntityGadget gadget, int param1, int param2, int param3) {
        if (gadget != roundGadget || gadget.getGroupId() != MAIN_GROUP || gadget.getConfigId() != 1001
                || player.getScene() != scene || !scene.getPlayers().contains(player)
                || scene.getEntities().get(gadget.getId()) != gadget) return false;
        var play = gadget.getGadgetPlayState();
        var round = play.getRound();
        long now = System.currentTimeMillis();
        var config = ActivityManager.getScheduleActivityConfigMap().get(round.scheduleId());
        return lifecycle.isCurrent(round.sceneTicket())
                && CrucibleSceneLifecycle.activeSchedule(config, now) == round.scheduleId()
                && play.acceptsClientSubmission(player.getUid(), param1, param2, param3,
                        player.getTeamManager().getEntity().getId(), now / 1000);
    }

    /** Called with the scene monitor before starting the gadget's countdown. */
    public GadgetPlayState.Round captureRound(EntityGadget gadget) {
        var config = ActivityManager.getScheduleActivityConfigMap().get(lifecycle.scheduleId());
        if (!lifecycle.isMounted() || CrucibleSceneLifecycle.activeSchedule(config, System.currentTimeMillis()) == 0
                || scene.getEntities().get(gadget.getId()) != gadget) return null;
        var context = invitation.context();
        if (invitation.phase() != CrucibleInvitation.Phase.STARTING || context == null
                || context.scheduleId() != config.getScheduleId() || !lifecycle.isCurrent(context.sceneTicket())
                || gadget.getGroupId() != MAIN_GROUP || gadget.getConfigId() != 1001
                || context.members().keySet().stream().anyMatch(uid -> scene.getPlayers().stream()
                        .noneMatch(player -> player.getUid() == uid && checkMember(player).retcode() == 0))) return null;
        roundGadget = gadget;
        var personalLevels = new HashMap<Integer, Integer>();
        for (var player : scene.getPlayers()) if (context.members().containsKey(player.getUid()))
            personalLevels.put(player.getUid(), player.getWorldLevel());
        return new GadgetPlayState.Round(config.getScheduleId(), lifecycle.ticket(),
                scene.getWorld().getWorldLevel(), personalLevels);
    }

    /** The caller has already checked the scene generation, gadget identity and round serial. */
    public void onPlayChange(GadgetPlayState.Change change) {
        if (change.type() == GadgetPlayState.ChangeType.SUCCEEDED || change.type() == GadgetPlayState.ChangeType.TIMED_OUT
                || change.type() == GadgetPlayState.ChangeType.CANCELLED) invitation.finish();
        var round = change.round();
        if (change.type() == GadgetPlayState.ChangeType.SUCCEEDED) openRewards(change);
        for (var player : scene.getPlayers()) {
            var ownLevel = round.participantWorldLevels().get(player.getUid());
            if (ownLevel == null) continue;
            var manager = player.getActivityManager();
            if (change.type() == GadgetPlayState.ChangeType.SCORED) {
                var score = change.scores().get(player.getUid());
                if (score != null) manager.triggerWatcher(5001, round.scheduleId(),
                        WatcherTriggerType.TRIGGER_CRUCIBLE_ELEMENT_SCORE, String.valueOf(score));
            } else if (change.type() == GadgetPlayState.ChangeType.SUCCEEDED) {
                manager.triggerWatcher(5001, round.scheduleId(), WatcherTriggerType.TRIGGER_MP_PLAY_BATTLE_WIN, "1");
                manager.triggerWatcher(5001, round.scheduleId(), WatcherTriggerType.TRIGGER_CRUCIBLE_WORLD_LEVEL_SCORE,
                        String.valueOf(change.remainingTime()), String.valueOf(ownLevel), String.valueOf(round.battleWorldLevel()));
            }
        }
    }

    public void onMonsterKilled(EntityMonster monster, Player killer) {
        if (!owns(monster.getGroupId())) return;
        long ticket = lifecycle.ticket();
        runIfCurrent(ticket, () -> {
            var entity = scene.getEntityByConfigId(1001, MAIN_GROUP);
            if (!(entity instanceof EntityGadget gadget) || scene.getEntities().get(monster.getId()) != monster) return;
            var state = gadget.getGadgetPlayState();
            var round = state.getRound();
            if (!scene.getPlayers().contains(killer)
                    || !state.recordMonsterKill(monster.getId(), killer.getUid(), monster.getGroupId(),
                            System.currentTimeMillis() / 1000)) return;
            killer.getActivityManager().triggerWatcher(5001, round.scheduleId(),
                    WatcherTriggerType.TRIGGER_KILL_GROUP_MONSTER, String.valueOf(monster.getGroupId()));
        });
    }

    public static Position mainPosition() { return new Position(2347, 283.898f, -1735.401f); }

    public static ScenePoint rewardPosition(SceneGroup group, int configId) {
        if (group == null || group.getBindings() == null) throw new IllegalStateException("MP reward group missing");
        var point = ScriptLoader.getSerializer().toList(ScenePoint.class, group.getBindings().get("points")).stream()
                .filter(item -> item.config_id == configId).findFirst()
                .orElseThrow(() -> new IllegalStateException("MP reward point missing: " + group.id + "/" + configId));
        if (point.pos == null || point.rot == null) throw new IllegalStateException("MP reward point position missing");
        return point;
    }

    private void openRewards(GadgetPlayState.Change change) {
        try {
            var data = GameData.getMpPlayGroupDataMap().get(1);
            var gadgetData = GameData.getGadgetDataMap().get(REWARD_GADGET);
            if (data == null || data.getResinCost() <= 0 || gadgetData == null
                    || gadgetData.getType() != EntityType.MpPlayRewardPoint || !scheduleCurrent())
                throw new IllegalStateException("Crucible reward resources unavailable");
            var group = definitions.get(data.getRewardGroupId());
            var position = rewardPosition(group, data.getRewardConfigId());
            var members = new HashMap<Integer, CrucibleRewards.Reward>();
            for (var player : scene.getPlayers()) {
                var level = change.round().participantWorldLevels().get(player.getUid());
                if (level == null || departedRoundMembers.contains(player.getUid())
                        || player.getSceneLoadState() != Player.SceneLoadState.LOADED
                        || player.getServer().getPlayerByUid(player.getUid()) != player) continue;
                var drop = data.rewardForWorldLevel(level);
                if (!GameData.getDropTableDataMap().containsKey(drop.getDropId()))
                    throw new IllegalStateException("Crucible reward drop missing: " + drop.getDropId());
                members.put(player.getUid(), new CrucibleRewards.Reward(level, drop.getDropId()));
            }
            if (members.isEmpty()) return;
            var ticket = new CrucibleRewards.Ticket(change.round().scheduleId(), change.round().sceneTicket(), change.roundSerial());
            if (!rewards.open(ticket, data.getResinCost(), members)) return;
            removeRewardPoint();
            rewardPoint = new EntityGadget(scene, REWARD_GADGET, new Position(position.pos), new Position(position.rot));
            rewardPoint.setGroupId(group.id); rewardPoint.setBlockId(group.block_id);
            rewardPoint.setConfigId(position.config_id); rewardPoint.buildContent();
            scene.addEntity(rewardPoint);
        } catch (RuntimeException exception) {
            closeRewards();
            Grasscutter.getLogger().error("Could not open Crucible rewards for schedule {} round {}",
                    change.round().scheduleId(), change.roundSerial(), exception);
        }
    }

    public MpPlayRewardInfo rewardInfo(EntityGadget gadget) {
        synchronized (scene) {
            if (gadget != rewardPoint) return MpPlayRewardInfo.getDefaultInstance();
            var info = rewards.snapshot();
            return MpPlayRewardInfo.newBuilder().setResin(info.resin())
                    .addAllRemainUidList(info.remaining()).addAllQualifyUidList(info.qualified()).build();
        }
    }

    public static boolean withinRewardDistance(Position player, Position point) {
        double x = player.getX() - point.getX(), y = player.getY() - point.getY(), z = player.getZ() - point.getZ();
        return x * x + y * y + z * z <= 100;
    }

    public void claimReward(Player player, EntityGadget gadget, GadgetInteractReq request) {
        synchronized (scene) {
            int retcode;
            var ticket = rewards.ticket();
            if (gadget != rewardPoint || scene.getEntities().get(gadget.getId()) != gadget
                    || request.getGadgetEntityId() != gadget.getId()
                    || request.getGadgetId() != 0 && request.getGadgetId() != gadget.getGadgetId())
                retcode = Retcode.RET_GADGET_NOT_EXIST_VALUE;
            else if (ticket == null || !scheduleCurrent() || !lifecycle.isCurrent(ticket.sceneTicket())
                    || ticket.scheduleId() != lifecycle.scheduleId() || ticket.roundSerial() != currentRoundSerial())
                retcode = Retcode.RET_MP_PLAY_NOT_ACTIVE_VALUE;
            else if (player.getScene() != scene || !scene.getPlayers().contains(player)
                    || player.getSceneLoadState() != Player.SceneLoadState.LOADED
                    || player.getServer().getPlayerByUid(player.getUid()) != player)
                retcode = Retcode.RET_MP_PLAY_REWARD_NO_QUALIFICATION_VALUE;
            else if (!withinRewardDistance(player.getPosition(), gadget.getPosition())) retcode = Retcode.RET_DISTANCE_LONG_VALUE;
            else if (request.getOpType() == InterOpType.InterOpType_INTER_OP_START) {
                retcode = rewardRetcode(rewards.preview(ticket, player.getUid()));
                if (retcode == 0 && emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage
                        && player.getProperty(PlayerProperty.PROP_PLAYER_RESIN) < rewards.snapshot().resin())
                    retcode = Retcode.RET_RESIN_NOT_ENOUGH_VALUE;
            } else if (request.getOpType() != InterOpType.InterOpType_INTER_OP_FINISH
                    || request.getResinCostType() != ResinCostType.ResinCostType_NONE
                    && request.getResinCostType() != ResinCostType.ResinCostType_NORMAL)
                retcode = Retcode.RET_FORBIDDEN_VALUE;
            else {
                try {
                    int cost = rewards.snapshot().resin();
                    retcode = rewardRetcode(rewards.claim(ticket, player.getUid(), reward -> deliverReward(player, reward, cost)));
                } catch (RuntimeException exception) {
                    retcode = Retcode.RET_SVR_ERROR_VALUE;
                    Grasscutter.getLogger().error("Crucible reward delivery failed; claim consumed to prevent replay: uid {} ticket {}",
                            player.getUid(), ticket, exception);
                }
            }
            player.sendPacket(new PacketGadgetInteractRsp(gadget, InteractType.InteractType_INTERACT_MP_PLAY_REWARD,
                    request.getOpType() == InterOpType.UNRECOGNIZED ? null : request.getOpType(), retcode));
            refreshRewardPoint();
        }
    }

    private CrucibleRewards.Result deliverReward(Player player, CrucibleRewards.Reward reward, int cost) {
        List<GameItem> items;
        try { items = player.getServer().getDropSystem().handleDungeonRewardDrop(reward.dropId(), false); }
        catch (RuntimeException exception) {
            Grasscutter.getLogger().error("Invalid Crucible drop {}", reward.dropId(), exception);
            return CrucibleRewards.Result.INVALID_REWARD;
        }
        var inventory = player.getInventory();
        synchronized (inventory) {
            var valid = CrucibleRewardDelivery.validate(items, inventory::getInventoryTab);
            if (valid != CrucibleRewards.Result.OK) return valid;
        }
        // Do not hold the inventory monitor while taking the resin monitor.
        if (!player.getResinManager().useResin(cost)) return CrucibleRewards.Result.NOT_ENOUGH_RESIN;
        CrucibleRewards.Result valid;
        synchronized (inventory) {
            valid = CrucibleRewardDelivery.validate(items, inventory::getInventoryTab);
            if (valid == CrucibleRewards.Result.OK) inventory.addItems(items, ActionReason.MpPlayTakeReward);
        }
        if (valid != CrucibleRewards.Result.OK) {
            // Inventory could fill between preflight and debit; no items were granted, so refund safely.
            player.getResinManager().addResin(cost);
            return valid;
        }
        player.sendPacket(new PacketGadgetAutoPickDropInfoNotify(items));
        return CrucibleRewards.Result.OK;
    }

    private static int rewardRetcode(CrucibleRewards.Result result) {
        return switch (result) {
            case OK -> 0;
            case STALE -> Retcode.RET_MP_PLAY_NOT_ACTIVE_VALUE;
            case NO_QUALIFICATION -> Retcode.RET_MP_PLAY_REWARD_NO_QUALIFICATION_VALUE;
            case ALREADY_TAKEN -> Retcode.RET_MP_PLAY_REWARD_HAS_TAKEN_VALUE;
            case PREVIEW_REQUIRED -> Retcode.RET_FORBIDDEN_VALUE;
            case BUSY -> Retcode.RET_FREQUENT_VALUE;
            case NOT_ENOUGH_RESIN -> Retcode.RET_RESIN_NOT_ENOUGH_VALUE;
            case INVENTORY_FULL -> Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
            case INVALID_REWARD -> Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        };
    }

    private void refreshRewardPoint() {
        if (rewardPoint == null) return;
        if (!rewards.hasRemaining()) removeRewardPoint();
        else scene.updateEntity(rewardPoint, VisionType.VisionType_VISION_REFRESH);
    }
    private void removeRewardPoint() {
        var point = rewardPoint;
        rewardPoint = null;
        if (point != null && scene.getEntities().get(point.getId()) == point) scene.removeEntity(point);
    }
    private void closeRewards() { rewards.clear(); removeRewardPoint(); }

    /** The historical activity block is absent; attach its group to the actual map block here. */
    public static SceneGroup mainGroup(Collection<SceneBlock> blocks) {
        var pos = mainPosition();
        var block = blocks.stream().filter(item -> item.min != null && item.max != null
                        && pos.getX() >= item.min.getX() && pos.getX() <= item.max.getX()
                        && pos.getZ() >= item.min.getZ() && pos.getZ() <= item.max.getZ())
                .min(Comparator.comparingInt(item -> item.id))
                .orElseThrow(() -> new IllegalStateException("原素烘炉位置没有对应的地图 block"));
        var group = SceneGroup.of(MAIN_GROUP);
        group.block_id = block.id; group.pos = pos; group.refresh_id = 99999;
        return group;
    }

    @Override public void load(int scheduleId) {
        var manager = scene.getScriptManager();
        definitions.put(MAIN_GROUP, mainGroup(manager.getBlocks().values()));
        var block = manager.getBlocks().get(definitions.get(MAIN_GROUP).block_id);
        scene.loadBlock(block);
        for (int id : GROUP_IDS.stream().filter(id -> id != MAIN_GROUP).sorted().toList()) {
            var source = block.groups == null ? null : block.groups.get(id);
            if (source == null) source = manager.findGroupById(id);
            if (source == null) throw new IllegalStateException("原素烘炉关联组缺失: " + id);
            var group = SceneGroup.of(id);
            group.block_id = source.block_id; group.pos = new Position(source.pos);
            group.refresh_id = source.refresh_id;
            group.dynamic_load = source.dynamic_load;
            group.business = source.business;
            definitions.put(id, group);
        }
        // Validate every script before spawning anything. Each group has its own Lua bindings.
        for (var group : definitions.values()) {
            group.load(scene.getId());
            if (group.init_config == null || group.getSuiteByIndex(1) == null)
                throw new IllegalStateException("原素烘炉脚本不能初始化: " + group.id);
        }
        manager.registerLocalGroups(definitions.values());
        for (var group : definitions.values()) {
            // A server restart, streaming reload or fresh schedule must not resurrect a prior round.
            var cached = manager.getCachedGroupInstanceById(group.id);
            if (cached != null) cached.reset();
        }
        scene.onLoadGroup(new ArrayList<>(definitions.values()));
        scene.onRegisterGroups();
    }

    @Override public void unload() {
        cancelInvitation(false);
        closeRewards();
        departedRoundMembers.clear();
        roundGadget = null;
        var manager = scene.getScriptManager();
        RuntimeException failure = null;
        for (var group : definitions.values()) {
            var cached = manager.getCachedGroupInstances().get(group.id);
            try {
                scene.getEntities().values().stream().filter(entity -> entity.getGroupId() == group.id)
                        .filter(EntityGadget.class::isInstance).map(EntityGadget.class::cast)
                        .forEach(EntityGadget::cancelGadgetPlayForUnload);
                scene.unloadGroup(manager.getBlocks().get(group.block_id), group);
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
            try {
                if (cached != null) { cached.reset(); cached.save(); }
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
        manager.unregisterLocalGroups(definitions.keySet());
        definitions.clear();
    }
}
