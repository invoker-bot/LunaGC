package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.game.activity.ActivityConfigItem;

/** One world's mounted activity groups, including the lifetime of queued script callbacks. */
public final class CrucibleSceneLifecycle {
    public interface Groups {
        void load(int scheduleId);
        void unload();
    }

    private final Groups groups;
    private int scheduleId;
    private long generation;
    private boolean cleanupPending;

    public CrucibleSceneLifecycle(Groups groups) { this.groups = groups; }

    public static int activeSchedule(ActivityConfigItem config, long nowMs) {
        return config != null && config.isActiveAt(nowMs) ? config.getScheduleId() : 0;
    }

    public synchronized void update(int desiredSchedule, boolean nearby, boolean roundActive) {
        if (cleanupPending) close();
        if (scheduleId != 0 && (scheduleId != desiredSchedule || (!nearby && !roundActive))) close();
        if (scheduleId != 0 || desiredSchedule == 0 || !nearby) return;
        scheduleId = desiredSchedule;
        generation++;
        try {
            groups.load(scheduleId);
        } catch (RuntimeException exception) {
            try { close(); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    public synchronized void close() {
        if (scheduleId == 0 && !cleanupPending) return;
        if (scheduleId != 0) { scheduleId = 0; generation++; }
        cleanupPending = true;
        groups.unload();
        cleanupPending = false;
    }

    public synchronized boolean isMounted() { return scheduleId != 0; }
    public synchronized int scheduleId() { return scheduleId; }
    public synchronized long ticket() { return generation; }
    public synchronized boolean isCurrent(long ticket) { return isMounted() && generation == ticket; }
}
