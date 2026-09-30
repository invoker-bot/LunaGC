package emu.grasscutter.game.activity.aster;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AsterLittleDetailInfoOuterClass.AsterLittleDetailInfo;
import emu.grasscutter.net.proto.AsterLittleInfoNotifyOuterClass.AsterLittleInfoNotify;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Streams the original exploration groups per world and keeps collection in the host's replay. */
public final class AsterSceneController {
    private final Scene scene;
    private volatile int scheduleId;
    private final Map<Integer, SceneGroup> mounted = new ConcurrentHashMap<>();
    private final Map<Integer, AsterLittleDetailInfo> notified = new HashMap<>();

    public AsterSceneController(Scene scene) {
        this.scene = scene;
    }

    public boolean owns(int groupId) {
        return scene.getId() == 3 && AsterFragments.areas().containsKey(groupId);
    }

    private ActivityConfigItem config() {
        return ActivityManager.getScheduleActivityConfigMap().values().stream()
                .filter(c -> c.getActivityId() == AsterSchedule.ACTIVITY_ID)
                .findFirst()
                .orElse(null);
    }

    private PlayerActivityData data(Player player) {
        return player.getActivityManager() == null
                ? null
                : player.getActivityManager().getPlayerActivityDataMap().get(AsterSchedule.ACTIVITY_ID);
    }

    public boolean allows(SceneGroup group) {
        var host = scene.getWorld().getHost();
        var config = config();
        return group != null
                && mounted.get(group.id) == group
                && config != null
                && config.getScheduleId() == scheduleId
                && AsterSchedule.phaseOpen(
                        data(host), config, host.getLevel(), System.currentTimeMillis(), 1);
    }

    public boolean allowsGadget(int groupId, SceneGadget gadget) {
        if (!owns(groupId)) return true;
        if (gadget == null || !allows(gadget.group)) return false;
        var ownerData = data(scene.getWorld().getHost());
        if (ownerData == null) return false;
        synchronized (ownerData) {
            return !AsterSchedule.progress(ownerData).hasCollected(groupId, gadget.config_id);
        }
    }

    public static SceneGroup definition(AsterFragments.Area area, Collection<SceneBlock> blocks) {
        var pos = area.position();
        var block =
                blocks.stream()
                        .filter(
                                b ->
                                        b.min != null
                                                && b.max != null
                                                && pos.getX() >= b.min.getX()
                                                && pos.getX() <= b.max.getX()
                                                && pos.getZ() >= b.min.getZ()
                                                && pos.getZ() <= b.max.getZ())
                        .min(Comparator.comparingInt(b -> b.id))
                        .orElseThrow(
                                () -> new IllegalStateException("Aster map block missing: " + area.groupId()));
        var group = SceneGroup.of(area.groupId());
        group.block_id = block.id;
        group.pos = new Position(pos);
        group.dynamic_load = true;
        group.refresh_id = 99999;
        return group;
    }

    private void load(AsterFragments.Area area) {
        var manager = scene.getScriptManager();
        var group = definition(area, manager.getBlocks().values());
        group.load(scene.getId());
        if (group.init_config == null
                || group.getSuiteByIndex(1) == null
                || group.gadgets == null
                || !area.configIds().stream()
                        .allMatch(
                                id -> group.gadgets.containsKey(id) && group.gadgets.get(id).point_type == 9127))
            throw new IllegalStateException("Aster exploration script incomplete: " + area.groupId());
        scene.loadBlock(manager.getBlocks().get(group.block_id));
        mounted.put(group.id, group);
        manager.registerLocalGroups(List.of(group));
        try {
            var cached = manager.getCachedGroupInstanceById(group.id);
            if (cached != null) cached.reset();
            scene.onLoadGroup(List.of(group));
            scene.onRegisterGroups();
        } catch (RuntimeException failure) {
            unload(group.id);
            throw failure;
        }
    }

    private void unload(int id) {
        var group = mounted.remove(id);
        if (group == null) return;
        var manager = scene.getScriptManager();
        // Remove the registration first, so stale queued script callbacks cannot create entities.
        try {
            scene.unloadGroup(manager.getBlocks().get(group.block_id), group);
        } finally {
            manager.unregisterLocalGroups(List.of(id));
        }
    }

    public void close() {
        synchronized (scene) {
            for (int id : List.copyOf(mounted.keySet())) unload(id);
            scheduleId = 0;
            notified.clear();
        }
    }

    public void update(long now) {
        if (scene.getId() != 3) return;
        synchronized (scene) {
            var config = config();
            var host = scene.getWorld().getHost();
            var data = data(host);
            boolean active =
                    AsterSchedule.phaseOpen(data, config, host.getLevel(), now, 1)
                            && !scene.getPlayers().isEmpty();
            if (!active || scheduleId != 0 && config.getScheduleId() != scheduleId) close();
            if (active) {
                scheduleId = config.getScheduleId();
                int stage;
                synchronized (data) {
                    stage = AsterSchedule.currentStage(data);
                }
                for (var area : AsterFragments.areas().values()) {
                    var stageData = emu.grasscutter.data.GameData.getAsterLittleDataMap().get(area.stageId());
                    boolean visible =
                            area.stageId() <= stage
                                    && now >= AsterSchedule.beginTime(config, stageData.getOpenDay())
                                    && scene.getPlayers().stream()
                                            .anyMatch(
                                                    p -> {
                                                        double x = p.getPosition().getX() - area.position().getX(),
                                                                z = p.getPosition().getZ() - area.position().getZ();
                                                        return x * x + z * z <= 900 * 900;
                                                    });
                    if (visible && !mounted.containsKey(area.groupId())) load(area);
                    else if (!visible && mounted.containsKey(area.groupId())) unload(area.groupId());
                }
            }
            notified
                    .keySet()
                    .removeIf(uid -> scene.getPlayers().stream().noneMatch(p -> p.getUid() == uid));
            if (config != null && config.isActiveAt(now))
                for (var player : scene.getPlayers()) {
                    var playerData = data(player);
                    if (playerData == null) continue;
                    AsterLittleDetailInfo info;
                    synchronized (playerData) {
                        info = AsterSchedule.littleInfo(playerData, config, player.getLevel(), now);
                    }
                    if (!info.equals(notified.put(player.getUid(), info))) notifyDetail(player, info);
                }
        }
    }

    private void notifyDetail(Player player, AsterLittleDetailInfo info) {
        var packet = new BasePacket(PacketOpcodes.AsterLittleInfoNotify);
        packet.setData(AsterLittleInfoNotify.newBuilder().setInfo(info).build());
        player.sendPacket(packet);
        player.getActivityManager().triggerActivityConditions();
    }
    /** Shared world gather resources prohibit guests. A guest must collect in their own world. */
    public boolean gather(Player player, EntityGadget gadget, GadgetInteractReq request) {
        synchronized (scene) {
            var config = config();
            var data = data(player);
            int code = Retcode.RET_ACTIVITY_CLOSE_VALUE;
            if (player == scene.getWorld().getHost()
                    && data != null
                    && player.getScene() == scene
                    && scene.getPlayers().contains(player)
                    && player.getSceneLoadState() == Player.SceneLoadState.LOADED
                    && scene.getEntityById(gadget.getId()) == gadget
                    && gadget.getPointType() == 9127
                    && mounted.containsKey(gadget.getGroupId())
                    && config != null
                    && config.getScheduleId() == scheduleId) {
                double x = player.getPosition().getX() - gadget.getPosition().getX();
                double y = player.getPosition().getY() - gadget.getPosition().getY();
                double z = player.getPosition().getZ() - gadget.getPosition().getZ();
                if (x * x + y * y + z * z <= 25)
                    code =
                            AsterGather.collect(
                                    data,
                                    config,
                                    player.getLevel(),
                                    System.currentTimeMillis(),
                                    gadget.getGroupId(),
                                    gadget.getConfigId(),
                                    data::saveSync);
            }
            player.sendPacket(
                    new PacketGadgetInteractRsp(
                            gadget, InteractType.InteractType_INTERACT_GATHER, request.getOpType(), code));
            if (code != 0) return false;
            var watcherId = AsterFragments.areas().get(gadget.getGroupId()).watcherId();
            player.sendPacket(
                    new PacketActivityUpdateWatcherNotify(
                            AsterSchedule.ACTIVITY_ID, data.getWatcherInfoMap().get(watcherId)));
            player.sendPacket(
                    new PacketItemAddHintNotify(new GameItem(109, 1), ActionReason.ActivityGather));
            var info =
                    AsterSchedule.littleInfo(data, config, player.getLevel(), System.currentTimeMillis());
            notified.put(player.getUid(), info);
            notifyDetail(player, info);
            scene
                    .getScriptManager()
                    .callEvent(
                            new ScriptArgs(gadget.getGroupId(), EventType.EVENT_GATHER, gadget.getConfigId())
                                    .setEventSource(gadget.getConfigId()));
            // Removing the parent also removes its gather-object child. Both refer to the same saved
            // point.
            var parent =
                    scene.getEntities().values().stream()
                            .filter(EntityGadget.class::isInstance)
                            .map(EntityGadget.class::cast)
                            .filter(g -> g != gadget && g.getChildren().contains(gadget))
                            .findFirst()
                            .orElse(null);
            if (parent != null) scene.removeEntity(parent);
            else scene.removeEntity(gadget);
            return true;
        }
    }
}
