package emu.grasscutter.game.activity.crucible;

import java.util.*;

/** Per-gadget state consumed by the original Crucible.lua; never shared between scene instances. */
public final class GadgetPlayState {
    private final Map<Integer, Map<String, Integer>> values = new HashMap<>();
    private int progress;

    public synchronized int getUidValue(int uid, String key) {
        return values.getOrDefault(uid, Map.of()).getOrDefault(key, 0);
    }

    public synchronized void setUidValue(int uid, String key, int value) {
        values.computeIfAbsent(uid, id -> new HashMap<>()).put(key, value);
    }

    public synchronized int getProgress() { return progress; }

    public synchronized int addProgress(int delta) {
        progress = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) progress + delta));
        return progress;
    }
}
