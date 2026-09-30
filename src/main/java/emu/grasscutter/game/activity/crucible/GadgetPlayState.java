package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.net.proto.GadgetCrucibleInfoOuterClass.GadgetCrucibleInfo;
import emu.grasscutter.net.proto.GadgetPlayInfoOuterClass.GadgetPlayInfo;
import emu.grasscutter.scripts.data.SceneGadgetCrucibleConfig;
import java.util.*;

/** Per-gadget state consumed by the original Crucible.lua; never shared between scene instances. */
public final class GadgetPlayState {
    public enum ChangeType { STARTED, STAGE_CHANGED, SUCCEEDED, TIMED_OUT, CANCELLED }
    public record Change(ChangeType type, int previousStage, int stage) {}

    private final Map<Integer, Map<String, Integer>> values = new HashMap<>();
    private int progress;
    private List<Integer> stages = List.of();
    private int stage;
    private long countdownBegin;
    private long battleBegin;
    private long deadline;
    private boolean active;
    private boolean running;

    public synchronized int getUidValue(int uid, String key) {
        return values.getOrDefault(uid, Map.of()).getOrDefault(key, 0);
    }

    public synchronized void setUidValue(int uid, String key, int value) {
        values.computeIfAbsent(uid, id -> new HashMap<>()).put(key, value);
    }

    public synchronized int getProgress() { return progress; }

    public synchronized boolean start(SceneGadgetCrucibleConfig config, long now) {
        var validated = config.validatedStages();
        if (active) return false;
        stages = validated;
        values.clear();
        progress = 0;
        stage = 0;
        countdownBegin = now;
        battleBegin = Math.addExact(now, config.start_cd);
        deadline = Math.addExact(battleBegin, config.duration);
        active = true;
        running = false;
        return true;
    }

    public synchronized List<Change> tick(long now) {
        if (!active) return List.of();
        var changes = new ArrayList<Change>();
        if (!running && now >= battleBegin) {
            running = true;
            changes.add(new Change(ChangeType.STARTED, stage, stage));
        }
        if (now >= deadline) {
            active = false;
            running = false;
            changes.add(new Change(ChangeType.TIMED_OUT, stage, stage));
        }
        return changes;
    }

    public synchronized List<Change> addProgress(int delta, long now) {
        var changes = new ArrayList<>(tick(now));
        if (!running) return changes;
        progress = (int) Math.min(stages.get(stages.size() - 1),
                Math.max(stages.get(stage), (long) progress + delta));
        while (stage + 1 < stages.size() && progress >= stages.get(stage + 1)) {
            int previous = stage++;
            changes.add(new Change(ChangeType.STAGE_CHANGED, previous, stage));
        }
        if (stage == stages.size() - 1) {
            active = false;
            running = false;
            changes.add(new Change(ChangeType.SUCCEEDED, stage, stage));
        }
        return changes;
    }

    public synchronized List<Change> stop() {
        if (!active) return List.of();
        active = false;
        running = false;
        return List.of(new Change(ChangeType.CANCELLED, stage, stage));
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
