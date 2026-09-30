package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.scripts.service.ScriptMonsterSpawnQueue;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScriptMonsterSpawnQueueTest {
    @Test void originalFiveAndTenSecondDelaysRunOnceOnTheSceneScheduler() {
        var scheduler = new ServerTaskScheduler();
        var queue = new ScriptMonsterSpawnQueue(new Object(), scheduler);
        var spawned = new ArrayList<Integer>();
        queue.spawn(305001001, 5, () -> spawned.add(1039));
        queue.spawn(305001001, 10, () -> spawned.add(1036));
        assertTrue(spawned.isEmpty());
        for (int i = 0; i < 4; i++) scheduler.runTasks();
        assertTrue(spawned.isEmpty());
        scheduler.runTasks(); assertEquals(List.of(1039), spawned);
        for (int i = 0; i < 5; i++) scheduler.runTasks();
        assertEquals(List.of(1039, 1036), spawned);
        for (int i = 0; i < 5; i++) scheduler.runTasks();
        assertEquals(List.of(1039, 1036), spawned);
    }

    @Test void clearingOneGroupCancelsItsOldTasksAndAllowsFreshSpawns() {
        var scheduler = new ServerTaskScheduler();
        var queue = new ScriptMonsterSpawnQueue(new Object(), scheduler);
        var spawned = new ArrayList<Integer>();
        queue.spawn(305001001, 5, () -> spawned.add(1));
        queue.spawn(133003554, 5, () -> spawned.add(2));
        for (int i = 0; i < 3; i++) scheduler.runTasks();
        queue.cancelGroup(305001001);
        queue.spawn(305001001, 5, () -> spawned.add(3));
        for (int i = 0; i < 2; i++) scheduler.runTasks();
        assertEquals(List.of(2), spawned);
        for (int i = 0; i < 3; i++) scheduler.runTasks();
        assertEquals(List.of(2, 3), spawned);
    }

    @Test void aTaskAlreadyFetchedByTheSchedulerIsInvalidatedBeforeItCanCreateAMonster() {
        var scheduler = new ServerTaskScheduler();
        var scene = new Object();
        var queue = new ScriptMonsterSpawnQueue(scene, scheduler);
        var spawned = new ArrayList<Integer>();
        queue.spawn(305001001, 5, () -> {
            assertTrue(Thread.holdsLock(scene));
            spawned.add(1);
        });
        var fetched = scheduler.getTask(0);
        queue.cancelGroup(305001001);
        fetched.run();
        assertTrue(spawned.isEmpty());
    }

    @Test void aZeroDelayCreatesImmediatelyUnderTheSceneMonitor() {
        var scheduler = new ServerTaskScheduler();
        var scene = new Object();
        var queue = new ScriptMonsterSpawnQueue(scene, scheduler);
        var spawned = new ArrayList<Integer>();
        queue.spawn(305001001, 0, () -> {
            assertTrue(Thread.holdsLock(scene));
            spawned.add(1);
        });
        assertEquals(List.of(1), spawned);
        scheduler.runTasks(); assertEquals(List.of(1), spawned);
    }
}
