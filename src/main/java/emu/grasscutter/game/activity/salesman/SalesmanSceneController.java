package emu.grasscutter.game.activity.salesman;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import java.util.*;

/** Liben is spawned locally by the client through the original seven group suites. */
public final class SalesmanSceneController {
    private final Scene scene;
    private final Map<Player, SalesmanNpcScene.Visibility> visibility = new HashMap<>();
    private final Map<Player, SalesmanNpcScene.Visit> activityDays = new HashMap<>();
    private SceneNpcBornData cachedBornData;
    private Map<Integer, SceneNpcBornEntry> locations = Map.of();
    public static final double INTERACTION_RANGE = 10;

    public SalesmanSceneController(Scene scene) { this.scene = scene; }

    private static ActivityConfigItem config() {
        return ActivityManager.getScheduleActivityConfigMap().values().stream()
                .filter(item -> item.getActivityId() == SalesmanSchedule.ACTIVITY_ID).findFirst().orElse(null);
    }

    private Map<Integer, SceneNpcBornEntry> locations() {
        var source = GameData.getSceneNpcBornData().get(3);
        if (source != cachedBornData || locations.isEmpty()) {
            var prepared = SalesmanNpcScene.locations(source);
            locations = prepared;
            cachedBornData = source;
        }
        return locations;
    }

    private SalesmanNpcScene.Visit visit(Player player, long now) {
        return scene.getId() == 3 && player.getScene() == scene && scene.getPlayers().contains(player)
                && player.getSceneLoadState() == Player.SceneLoadState.LOADED
                ? SalesmanNpcScene.visit(config(), player.getLevel(), now) : null;
    }

    public synchronized void update(long now) {
        if (scene.getId() != 3) return;
        for (var player : new ArrayList<>(visibility.keySet()))
            if (!scene.getPlayers().contains(player)) onPlayerLeaving(player);
        for (var player : scene.getPlayers()) updatePlayer(player, now);
    }

    public synchronized void updatePlayer(Player player, long now) {
        if (scene.getId() != 3) return;
        var visit = visit(player, now);
        var client = visibility.get(player);
        if (visit == null) {
            if (client != null) client.update(null);
            activityDays.remove(player);
            return;
        }
        var npc = locations().get(visit.day());
        if (!Objects.equals(activityDays.get(player), visit)) {
            player.sendPacket(new PacketActivityInfoNotify(player.getActivityManager().getInfoProtoByActivityId(SalesmanSchedule.ACTIVITY_ID)));
            player.getActivityManager().triggerActivityConditions();
            activityDays.put(player, visit);
        }
        if (client == null) {
            client = new SalesmanNpcScene.Visibility(new SalesmanNpcScene.Visibility.Client() {
                public void show(SalesmanNpcScene.Visit view) { player.sendPacket(new PacketGroupSuiteNotify(SalesmanNpcScene.GROUP_ID, view.day())); }
                public void hide() { player.sendPacket(new PacketGroupUnloadNotify(List.of(SalesmanNpcScene.GROUP_ID))); }
            });
            visibility.put(player, client);
        }
        int range = Grasscutter.getConfig().server.game.loadEntitiesForPlayerRange;
        client.update(SalesmanNpcScene.nearby(player.getPosition(), npc.getPos(), range) ? visit : null);
    }

    public synchronized boolean canInteract(Player player, long now) {
        var expected = visit(player, now);
        var client = visibility.get(player);
        return expected != null && client != null && expected.equals(client.shown())
                && SalesmanNpcScene.nearby(player.getPosition(), locations().get(expected.day()).getPos(), INTERACTION_RANGE);
    }

    public int completeTalk(Player player, int talkId, long now) {
        updatePlayer(player, now);
        if (!canInteract(player, now)) return Retcode.RET_NOT_CURRENT_TALK_VALUE;
        var manager = player.getActivityManager();
        synchronized (manager) {
            var item = config();
            var data = manager.getPlayerActivityDataMap().get(SalesmanSchedule.ACTIVITY_ID);
            int result = SalesmanTalk.complete(data, item, talkId, player.getLevel(), now, data == null ? () -> {} : data::save);
            if (result == 0) {
                player.sendPacket(new PacketActivityInfoNotify(manager.getInfoProtoByActivityId(SalesmanSchedule.ACTIVITY_ID)));
                manager.triggerActivityConditions();
            }
            return result;
        }
    }

    public synchronized void onPlayerLeaving(Player player) {
        var client = visibility.get(player);
        if (client != null) client.update(null);
        visibility.remove(player);
        activityDays.remove(player);
    }

    public synchronized void close() {
        for (var player : new ArrayList<>(visibility.keySet())) onPlayerLeaving(player);
    }
}
