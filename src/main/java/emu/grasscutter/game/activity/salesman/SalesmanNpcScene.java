package emu.grasscutter.game.activity.salesman;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.ActivityConfigItem;
import emu.grasscutter.game.world.Position;
import java.util.*;

/** Client-owned NPC suites from the original event's scene born table. */
public final class SalesmanNpcScene {
    public static final int GROUP_ID = 305003001;
    public static final int NPC_ID = 30001;
    public record Visit(int scheduleId, int day) {}
    private SalesmanNpcScene() {}

    public static Map<Integer, SceneNpcBornEntry> locations(SceneNpcBornData data) {
        if (data == null || data.getSceneId() != 3 || data.getBornPosList() == null)
            throw new IllegalArgumentException("百货奇货 NPC 出生表缺失");
        var slots = new HashMap<Integer, SceneNpcBornEntry>();
        for (var entry : data.getBornPosList()) {
            if (entry.getGroupId() != GROUP_ID) continue;
            var suites = entry.getSuiteIdList();
            if (entry.getConfigId() != NPC_ID || suites == null || suites.size() != 1
                    || suites.get(0) == null || suites.get(0) < 1 || suites.get(0) > 7
                    || !finite(entry.getPos()) || !finite(entry.getRot())
                    || slots.putIfAbsent(suites.get(0), entry) != null)
                throw new IllegalArgumentException("百货奇货 NPC 套件或位置无效");
        }
        if (slots.size() != 7) throw new IllegalArgumentException("百货奇货七天 NPC 套件不齐全");
        return Map.copyOf(slots);
    }
    public static Visit visit(ActivityConfigItem config, int rank, long now) {
        int day = SalesmanSchedule.dayIndex(config, now);
        return rank >= 12 && day > 0 && config.getScheduleId() > 0
                ? new Visit(config.getScheduleId(), Math.min(day, 7)) : null;
    }
    public static boolean resourcesAvailable() {
        try { return locations(GameData.getSceneNpcBornData().get(3)).size() == 7; }
        catch (RuntimeException invalid) { return false; }
    }
    private static boolean finite(Position pos) {
        return pos != null && Float.isFinite(pos.getX()) && Float.isFinite(pos.getY()) && Float.isFinite(pos.getZ());
    }
    public static boolean nearby(Position player, Position npc, double range) {
        if (!finite(player) || !finite(npc) || !Double.isFinite(range) || range < 0) return false;
        double x = player.getX() - npc.getX(), y = player.getY() - npc.getY(), z = player.getZ() - npc.getZ();
        return x*x + y*y + z*z <= range*range;
    }

    public static final class Visibility {
        public interface Client {
            void show(Visit visit);
            void hide();
        }
        private final Client client;
        private Visit shown;
        public Visibility(Client client) { this.client = client; }
        public synchronized Visit shown() { return shown; }
        public synchronized void update(Visit visit) {
            if (Objects.equals(shown, visit)) return;
            if (shown != null) { client.hide(); shown = null; }
            if (visit != null) { client.show(visit); shown = visit; }
        }
    }
}
