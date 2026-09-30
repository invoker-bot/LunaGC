package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.data.excels.activity.MpPlayWatcherData;
import emu.grasscutter.net.proto.GadgetPlayUidInfoOuterClass.GadgetPlayUidInfo;
import java.util.*;

/** Builds titles from an immutable round snapshot, never from live state after a restart. */
public final class CrucibleSettlement {
    // ElementType values in the resource watcher parameters and the original Crucible.lua.
    private static final Map<Integer, String> ELEMENTS = Map.of(1, "Fire", 2, "Water", 3, "Grass",
            4, "Electric", 5, "Ice", 7, "Wind", 8, "Rock");
    private CrucibleSettlement() {}

    public static int watcherFor(int uid, GadgetPlayState.Change change, Collection<MpPlayWatcherData> rules) {
        if (change.type() != GadgetPlayState.ChangeType.SUCCEEDED
                && change.type() != GadgetPlayState.ChangeType.TIMED_OUT
                && change.type() != GadgetPlayState.ChangeType.CANCELLED) return 0;
        var stats = change.participantStats().get(uid);
        if (stats == null) return 0;
        return rules.stream().filter(rule -> rule.getMpPlayId() == 1 && !rule.isDisuse()
                        && rule.getId() > 0 && rule.getProgress() > 0 && rule.getTriggerConfig() != null)
                .filter(rule -> progress(rule, stats, change.participantStats().values()) >= rule.getProgress())
                // Higher resource priority wins. For equal priority use a stable ID order;
                // the historical server's exact tie rule is not available.
                .sorted(Comparator.comparingInt(MpPlayWatcherData::getPriority).reversed()
                        .thenComparingInt(MpPlayWatcherData::getId))
                .mapToInt(MpPlayWatcherData::getId).findFirst().orElse(0);
    }

    private static long totalBalls(GadgetPlayState.PlayerStats stats) {
        return stats.balls().values().stream().mapToLong(value -> Math.max(0, value)).sum();
    }

    private static long progress(MpPlayWatcherData rule, GadgetPlayState.PlayerStats own,
                                 Collection<GadgetPlayState.PlayerStats> players) {
        var trigger = rule.getTriggerConfig();
        if (trigger.getTriggerType() == null) return 0;
        var params = trigger.getParamList();
        var first = params == null || params.isEmpty() || params.get(0) == null ? "" : params.get(0);
        try {
            return switch (trigger.getTriggerType()) {
                case "TRIGGER_CRUCIBLE_MAX_BALL" -> totalBalls(own) > 0
                        && totalBalls(own) == players.stream().mapToLong(CrucibleSettlement::totalBalls).max().orElse(0) ? 1 : 0;
                case "TRIGGER_CRUCIBLE_MAX_SCORE" -> own.score() > 0
                        && own.score() == players.stream().mapToInt(GadgetPlayState.PlayerStats::score).max().orElse(0) ? 1 : 0;
                case "TRIGGER_CRUCIBLE_ELEMENT_SCORE" -> Math.max(0, own.score());
                case "TRIGGER_CRUCIBLE_SUBMIT_BALL" -> {
                    int element = Integer.parseInt(first);
                    String name = ELEMENTS.get(element);
                    yield element == 0 ? totalBalls(own)
                            : name == null ? 0 : Math.max(0, own.balls().getOrDefault(name, 0));
                }
                case "TRIGGER_KILL_GROUP_MONSTER" -> Arrays.stream(first.split(","))
                        .map(String::trim).mapToInt(Integer::parseInt).distinct()
                        .mapToLong(group -> Math.max(0, own.groupKills().getOrDefault(group, 0))).sum();
                default -> 0;
            };
        } catch (NumberFormatException invalidResource) {
            // Unknown/malformed resource conditions cannot award a title.
            return 0;
        }
    }

    public static List<GadgetPlayUidInfo> members(GadgetPlayState.Change change,
            Map<Integer, GadgetPlayUidInfo> profiles, Collection<MpPlayWatcherData> rules) {
        var uids = new TreeSet<>(change.round().participantWorldLevels().keySet());
        uids.addAll(change.totalScores().keySet());
        if (change.round().scheduleId() == 0) uids.addAll(profiles.keySet());
        return uids.stream().map(uid -> profiles
                .getOrDefault(uid, GadgetPlayUidInfo.newBuilder().setUid(uid).build()).toBuilder()
                .setScore(change.totalScores().getOrDefault(uid, 0))
                .setBattleWatcherId(watcherFor(uid, change, rules)).build()).toList();
    }
}
