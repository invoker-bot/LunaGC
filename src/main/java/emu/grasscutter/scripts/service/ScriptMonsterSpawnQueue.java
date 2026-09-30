package emu.grasscutter.scripts.service;

import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import java.util.*;

/** Delayed Lua monster creation, cancelled when its group is refreshed or removed. */
public final class ScriptMonsterSpawnQueue {
    private final Object scene;
    private final ServerTaskScheduler scheduler;
    private final Map<Integer, Set<Integer>> tasks = new HashMap<>();
    private final Map<Integer, Long> generations = new HashMap<>();

    public ScriptMonsterSpawnQueue(Object scene, ServerTaskScheduler scheduler) {
        this.scene = scene;
        this.scheduler = scheduler;
    }

    public void spawn(int groupId, int delay, Runnable action) {
        synchronized (scene) {
            if (delay <= 0) {
                action.run();
                return;
            }
            long generation = generations.getOrDefault(groupId, 0L);
            var taskId = new int[1];
            taskId[0] = scheduler.scheduleDelayedTask(() -> {
                synchronized (scene) {
                    var pending = tasks.get(groupId);
                    if (pending != null) {
                        pending.remove(taskId[0]);
                        if (pending.isEmpty()) tasks.remove(groupId);
                    }
                    // A cancelled task may already have been fetched by the scheduler.
                    if (generations.getOrDefault(groupId, 0L) == generation) action.run();
                }
            }, delay);
            tasks.computeIfAbsent(groupId, ignored -> new HashSet<>()).add(taskId[0]);
        }
    }

    public void cancelGroup(int groupId) {
        synchronized (scene) {
            generations.merge(groupId, 1L, Long::sum);
            var pending = tasks.remove(groupId);
            if (pending != null) pending.forEach(scheduler::cancelTask);
        }
    }
}
