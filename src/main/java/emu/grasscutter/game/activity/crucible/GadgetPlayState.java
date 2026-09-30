package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.net.proto.GadgetCrucibleInfoOuterClass.GadgetCrucibleInfo;
import emu.grasscutter.net.proto.GadgetPlayInfoOuterClass.GadgetPlayInfo;
import emu.grasscutter.scripts.data.SceneGadgetCrucibleConfig;
import java.util.*;

/** Per-gadget state consumed by the original Crucible.lua; never shared between scene instances. */
public final class GadgetPlayState {
    public enum ChangeType { STARTED, SCORED, PROGRESS_CHANGED, STAGE_CHANGED, SUCCEEDED, TIMED_OUT, CANCELLED }
    public record Round(int scheduleId, long sceneTicket, int battleWorldLevel,
                        Map<Integer, Integer> participantWorldLevels) {
        public static final Round NONE = new Round(0, 0, 0, Map.of());
        public Round { participantWorldLevels = Map.copyOf(participantWorldLevels); }
    }
    public record Change(ChangeType type, int previousStage, int stage, Round round,
                         long roundSerial, int remainingTime, Map<Integer, Integer> scores,
                         int progress, int costTime, Map<Integer, Integer> totalScores,
                         Map<Integer, PlayerStats> participantStats) {
        public Change {
            scores = Map.copyOf(scores); totalScores = Map.copyOf(totalScores);
            participantStats = Map.copyOf(participantStats);
        }
    }
    public record PlayerStats(int score, Map<String, Integer> balls, Map<Integer, Integer> groupKills) {
        public PlayerStats { balls = Map.copyOf(balls); groupKills = Map.copyOf(groupKills); }
    }

    private final Map<Integer, Map<String, Integer>> values = new HashMap<>();
    private final Map<Integer, Integer> creditedScores = new HashMap<>();
    private final Set<Integer> killedMonsters = new HashSet<>();
    private final Map<Integer, Map<Integer, Integer>> groupKills = new HashMap<>();
    private static final List<String> ELEMENTS = List.of("Water", "Fire", "Electric", "Ice", "Wind", "Rock", "Grass");
    private Round round = Round.NONE;
    private long roundSerial;
    private int progress;
    private List<Integer> stages = List.of();
    private int stage;
    private long countdownBegin;
    private long battleBegin;
    private long deadline;
    private int duration;
    private boolean active;
    private boolean running;

    public synchronized int getUidValue(int uid, String key) {
        return values.getOrDefault(uid, Map.of()).getOrDefault(key, 0);
    }

    public synchronized void setUidValue(int uid, String key, int value) {
        values.computeIfAbsent(uid, id -> new HashMap<>()).put(key, value);
    }

    public synchronized int getProgress() { return progress; }
    public synchronized boolean isActive() { return active; }
    public synchronized boolean isRunningAt(long now) { return active && now >= battleBegin && now < deadline; }
    public synchronized boolean acceptsUidOperation(int op, String name, long now) {
        if (op < 0 || name == null || name.isEmpty()) return false;
        // The original stop Lua clears random_buff after the state has ended.
        // Scene/round tickets guard queued callbacks; a new countdown cannot reuse this exception.
        return isRunningAt(now) || (!active && roundSerial > 0 && op == 1 && name.equals("random_buff"));
    }
    public synchronized Round getRound() { return round; }
    public synchronized long getRoundSerial() { return roundSerial; }
    public synchronized int getStartTime() { return (int) countdownBegin; }

    /** Crucible.lua accepts a player's clot submission, while 5001 is a server-only selection operation. */
    public synchronized boolean acceptsClientSubmission(int uid, int param1, int param2, int param3,
                                                         int teamEntityId, long now) {
        return uid > 0 && teamEntityId != 0 && param3 == teamEntityId && param1 != 5001 && param2 == 1
                && round.scheduleId() != 0 && round.participantWorldLevels().containsKey(uid) && isRunningAt(now);
    }

    public synchronized boolean recordMonsterKill(int entityId, int uid, long now) {
        return recordMonsterKill(entityId, uid, 0, now);
    }

    public synchronized boolean recordMonsterKill(int entityId, int uid, int groupId, long now) {
        if (entityId <= 0 || !isRunningAt(now) || !round.participantWorldLevels().containsKey(uid)
                || !killedMonsters.add(entityId)) return false;
        groupKills.computeIfAbsent(uid, key -> new HashMap<>()).merge(groupId, 1, Integer::sum);
        return true;
    }

    public synchronized boolean setRoundUidValue(int uid, String key, int value, long now) {
        if (!isRunningAt(now) || uid <= 0
                || (round.scheduleId() != 0 && !round.participantWorldLevels().containsKey(uid))) return false;
        setUidValue(uid, key, value);
        return true;
    }

    public synchronized boolean start(SceneGadgetCrucibleConfig config, long now) {
        return start(config, now, Round.NONE);
    }

    public synchronized boolean start(SceneGadgetCrucibleConfig config, long now, Round context) {
        var validated = config.validatedStages();
        if (active) return false;
        stages = validated;
        values.clear();
        creditedScores.clear();
        killedMonsters.clear();
        groupKills.clear();
        round = Objects.requireNonNull(context);
        roundSerial++;
        progress = 0;
        stage = 0;
        countdownBegin = now;
        battleBegin = Math.addExact(now, config.start_cd);
        deadline = Math.addExact(battleBegin, config.duration);
        duration = config.duration;
        active = true;
        running = false;
        return true;
    }

    public synchronized List<Change> tick(long now) {
        if (!active) return List.of();
        var changes = new ArrayList<Change>();
        if (!running && now >= battleBegin) {
            running = true;
            changes.add(change(ChangeType.STARTED, stage, stage, now, Map.of()));
        }
        if (now >= deadline) {
            active = false;
            running = false;
            changes.add(change(ChangeType.TIMED_OUT, stage, stage, now, Map.of()));
        }
        return changes;
    }

    public synchronized List<Change> addProgress(int delta, long now) {
        var changes = new ArrayList<>(tick(now));
        if (!running) return changes;
        if (delta > 0) {
            var scores = new HashMap<Integer, Integer>();
            values.forEach((uid, stats) -> {
                if (uid <= 0 || (round.scheduleId() != 0 && !round.participantWorldLevels().containsKey(uid))) return;
                int total = (int) Math.min(Integer.MAX_VALUE,
                        ELEMENTS.stream().mapToLong(key -> Math.max(0, stats.getOrDefault(key, 0))).sum());
                int previous = creditedScores.getOrDefault(uid, 0);
                if (total > previous) { scores.put(uid, total - previous); creditedScores.put(uid, total); }
            });
            if (!scores.isEmpty()) changes.add(change(ChangeType.SCORED, stage, stage, now, scores));
        }
        int previousProgress = progress;
        progress = (int) Math.min(stages.get(stages.size() - 1),
                Math.max(stages.get(stage), (long) progress + delta));
        if (progress != previousProgress)
            changes.add(change(ChangeType.PROGRESS_CHANGED, stage, stage, now, Map.of()));
        while (stage + 1 < stages.size() && progress >= stages.get(stage + 1)) {
            int previous = stage++;
            changes.add(change(ChangeType.STAGE_CHANGED, previous, stage, now, Map.of()));
        }
        if (stage == stages.size() - 1) {
            active = false;
            running = false;
            changes.add(change(ChangeType.SUCCEEDED, stage, stage, now, Map.of()));
        }
        return changes;
    }

    public synchronized List<Change> stop() { return stop(System.currentTimeMillis() / 1000); }

    public synchronized List<Change> stop(long now) {
        if (!active) return List.of();
        active = false;
        running = false;
        return List.of(change(ChangeType.CANCELLED, stage, stage, now, Map.of()));
    }

    private Change change(ChangeType type, int previous, int next, long now, Map<Integer, Integer> scores) {
        int remaining = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, deadline - now));
        int elapsed = (int) Math.max(0L, Math.min(duration, now - battleBegin));
        return new Change(type, previous, next, round, roundSerial, remaining, scores,
                progress, elapsed, creditedScores, participantStats());
    }

    private Map<Integer, PlayerStats> participantStats() {
        var uids = new HashSet<>(round.participantWorldLevels().keySet());
        if (round.scheduleId() == 0) uids.addAll(values.keySet());
        var result = new HashMap<Integer, PlayerStats>();
        for (int uid : uids) {
            var balls = new HashMap<String, Integer>();
            for (var element : ELEMENTS) balls.put(element,
                    Math.max(0, values.getOrDefault(uid, Map.of()).getOrDefault(element + "_ball", 0)));
            result.put(uid, new PlayerStats(creditedScores.getOrDefault(uid, 0), balls,
                    groupKills.getOrDefault(uid, Map.of())));
        }
        return result;
    }

    public synchronized int getStageBeginProgress() {
        return stages.isEmpty() ? 0 : stages.get(stage);
    }

    public synchronized int getFinalStage() { return Math.max(0, stages.size() - 1); }

    public synchronized GadgetPlayInfo toProto(SceneGadgetCrucibleConfig config) {
        return GadgetPlayInfo.newBuilder().setPlayType(1).setDuration(config.duration)
                .setStartCd(config.start_cd).addAllProgressStageList(config.validatedStages())
                .setStartTime((int) countdownBegin).setProgress(progress)
                .setCrucibleInfo(GadgetCrucibleInfo.newBuilder().setMpPlayId(config.mp_play_id)).build();
    }

    public synchronized int addProgress(int delta) {
        if (!stages.isEmpty()) throw new IllegalStateException("A timed round requires a score timestamp");
        progress = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) progress + delta));
        return progress;
    }
}
