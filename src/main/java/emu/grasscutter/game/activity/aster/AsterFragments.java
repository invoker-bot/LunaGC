package emu.grasscutter.game.activity.aster;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.utils.JsonUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Coordinates and collection IDs extracted from the six pinned original exploration scripts. */
public final class AsterFragments {
    private AsterFragments() {}

    public static final class Area {
        private int groupId, stageId, watcherId;
        private Position position;
        private Set<Integer> configIds;

        public int groupId() {
            return groupId;
        }

        public int stageId() {
            return stageId;
        }

        public int watcherId() {
            return watcherId;
        }

        public Position position() {
            return new Position(position);
        }

        public Set<Integer> configIds() {
            return configIds;
        }
    }

    private static final class Holder {
        static final Map<Integer, Area> AREAS = load();

        private static Map<Integer, Area> load() {
            try (var stream =
                    AsterFragments.class.getResourceAsStream(
                            "/historical-scripts/Activity/2001/fragments.json")) {
                if (stream == null) throw new IllegalStateException("Aster fragments manifest missing");
                var rows =
                        JsonUtils.loadToList(new InputStreamReader(stream, StandardCharsets.UTF_8), Area.class);
                if (rows.size() != 6
                        || rows.stream()
                                .anyMatch(
                                        a -> a.position() == null || a.configIds() == null || a.configIds().size() < 7))
                    throw new IllegalStateException("Aster fragments manifest incomplete");
                rows.forEach(a -> a.configIds = Set.copyOf(a.configIds));
                return Collections.unmodifiableMap(
                        rows.stream().collect(Collectors.toMap(Area::groupId, a -> a)));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    public static Map<Integer, Area> areas() {
        return Holder.AREAS;
    }

    public static long key(int groupId, int configId) {
        return ((long) groupId << 32) | Integer.toUnsignedLong(configId);
    }

    public static boolean contains(long key) {
        var area = areas().get((int) (key >>> 32));
        return area != null && area.configIds().contains((int) key);
    }
}
