package emu.grasscutter.game.activity.aster;

import java.util.*;

/**
 * Kept in the activity document, so a new replay resets the event currency and collected points.
 */
public final class AsterProgress {
    private Set<Long> collected = new HashSet<>();
    private int pendingWatcherReward;

    public Set<Long> collected() {
        return Set.copyOf(collected);
    }

    public int credit() {
        return collected.size();
    }

    public boolean collect(int groupId, int configId) {
        return collected.add(AsterFragments.key(groupId, configId));
    }

    public boolean hasCollected(int groupId, int configId) {
        return collected.contains(AsterFragments.key(groupId, configId));
    }

    public int pendingWatcherReward() {
        return pendingWatcherReward;
    }

    public void beginWatcherReward(int id) {
        if (pendingWatcherReward != 0) throw new IllegalStateException("Aster reward already reserved");
        pendingWatcherReward = id;
    }

    public void finishWatcherReward() {
        pendingWatcherReward = 0;
    }

    public boolean isValid() {
        return collected != null
                && collected.stream().allMatch(k -> k != null && AsterFragments.contains(k))
                && (pendingWatcherReward == 0
                        || pendingWatcherReward >= 1200101 && pendingWatcherReward <= 1200106);
    }
}
