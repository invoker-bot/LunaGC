package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.data.*;
import java.util.*;

/** Mounts the archived Crucible groups without changing the shared map metadata or group grid. */
public final class CrucibleSceneController implements CrucibleSceneLifecycle.Groups {
    public static final int MAIN_GROUP = 305001001;
    public static final Set<Integer> GROUP_IDS = Set.of(MAIN_GROUP, 133003549, 133003550,
            133003554, 133003555, 133003556, 133003557, 133003568);
    private final Scene scene;
    private final CrucibleSceneLifecycle lifecycle = new CrucibleSceneLifecycle(this);
    private final Map<Integer, SceneGroup> definitions = new LinkedHashMap<>();
    private volatile EntityGadget roundGadget;

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
        boolean busy = !scene.getPlayers().isEmpty() && entity instanceof EntityGadget gadget
                && gadget.getGadgetPlayState().isActive();
        synchronized (scene) { lifecycle.update(schedule, nearby, busy); }
    }

    public void close() { synchronized (scene) { lifecycle.close(); } }

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

    /** Called with the scene monitor before starting the gadget's countdown. */
    public GadgetPlayState.Round captureRound(EntityGadget gadget) {
        var config = ActivityManager.getScheduleActivityConfigMap().get(lifecycle.scheduleId());
        if (!lifecycle.isMounted() || CrucibleSceneLifecycle.activeSchedule(config, System.currentTimeMillis()) == 0
                || scene.getEntities().get(gadget.getId()) != gadget) return null;
        var play = GameData.getMpPlayGroupDataMap().get(1);
        if (play == null || play.getRadius() <= 0) return null;
        var center = play.centerPosition();
        var members = new HashMap<Integer, Integer>();
        for (var player : scene.getPlayers()) {
            var pos = player.getPosition();
            double x = pos.getX() - center.getX(), z = pos.getZ() - center.getZ();
            if (x * x + z * z <= (double) play.getRadius() * play.getRadius())
                members.put(player.getUid(), player.getWorldLevel());
        }
        if (members.isEmpty()) return null;
        roundGadget = gadget;
        return new GadgetPlayState.Round(config.getScheduleId(), lifecycle.ticket(),
                scene.getWorld().getWorldLevel(), members);
    }

    /** The caller has already checked the scene generation, gadget identity and round serial. */
    public void onPlayChange(GadgetPlayState.Change change) {
        var round = change.round();
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
                    || !state.recordMonsterKill(monster.getId(), killer.getUid(), System.currentTimeMillis() / 1000)) return;
            killer.getActivityManager().triggerWatcher(5001, round.scheduleId(),
                    WatcherTriggerType.TRIGGER_KILL_GROUP_MONSTER, String.valueOf(monster.getGroupId()));
        });
    }

    public static Position mainPosition() { return new Position(2347, 283.898f, -1735.401f); }

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
        roundGadget = null;
        var manager = scene.getScriptManager();
        RuntimeException failure = null;
        for (var group : definitions.values()) {
            var cached = manager.getCachedGroupInstances().get(group.id);
            try {
                scene.getEntities().values().stream().filter(entity -> entity.getGroupId() == group.id)
                        .filter(EntityGadget.class::isInstance).map(EntityGadget.class::cast)
                        .forEach(gadget -> gadget.getGadgetPlayState().stop());
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
