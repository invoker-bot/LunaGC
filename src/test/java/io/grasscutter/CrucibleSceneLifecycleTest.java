package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.activity.ActivityConfigItem;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.crucible.CrucibleSceneController;
import emu.grasscutter.game.activity.crucible.CrucibleSceneLifecycle;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CrucibleSceneLifecycleTest {
    @BeforeAll static void initializeScripts() throws Exception {
        Grasscutter.getConfig();
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    private static final class Groups implements CrucibleSceneLifecycle.Groups {
        final List<String> operations = new ArrayList<>();
        boolean failLoad;
        boolean failUnload;
        @Override public void load(int scheduleId) {
            operations.add("load:" + scheduleId);
            if (failLoad) throw new IllegalStateException("Missing stage script");
        }
        @Override public void unload() {
            operations.add("unload");
            if (failUnload) throw new IllegalStateException("Cannot save cleared state");
        }
    }

    @Test void approachingLoadsOnceAndAnOngoingRoundSurvivesStreamingDistance() {
        var groups = new Groups();
        var lifecycle = new CrucibleSceneLifecycle(groups);
        lifecycle.update(5001002, false, false);
        assertTrue(groups.operations.isEmpty());
        lifecycle.update(5001002, true, false);
        long ticket = lifecycle.ticket();
        lifecycle.update(5001002, true, false);
        lifecycle.update(5001002, false, true);
        assertEquals(List.of("load:5001002"), groups.operations);
        assertTrue(lifecycle.isCurrent(ticket));
        lifecycle.update(5001002, false, false);
        assertEquals(List.of("load:5001002", "unload"), groups.operations);
        assertFalse(lifecycle.isCurrent(ticket));
    }

    @Test void closingOrRerunningDiscardsOldCallbacksEvenDuringBattle() {
        var groups = new Groups();
        var lifecycle = new CrucibleSceneLifecycle(groups);
        lifecycle.update(5001002, true, false);
        long first = lifecycle.ticket();
        lifecycle.update(5001003, true, true);
        assertFalse(lifecycle.isCurrent(first));
        long second = lifecycle.ticket();
        lifecycle.update(0, true, true);
        assertFalse(lifecycle.isCurrent(second));
        lifecycle.update(5001003, true, false);
        assertFalse(lifecycle.isCurrent(second), "Resume cannot accept the previous scene's callbacks");
        assertEquals(List.of("load:5001002", "unload", "load:5001003", "unload", "load:5001003"),
                groups.operations);
    }

    @Test void failedLoadCleansPartialGroupsAndCanRetry() {
        var groups = new Groups();
        var lifecycle = new CrucibleSceneLifecycle(groups);
        groups.failLoad = true;
        assertThrows(IllegalStateException.class, () -> lifecycle.update(5001002, true, false));
        assertFalse(lifecycle.isMounted());
        assertFalse(lifecycle.isCurrent(lifecycle.ticket()));
        groups.failLoad = false;
        lifecycle.update(5001002, true, false);
        assertTrue(lifecycle.isMounted());
        assertEquals(List.of("load:5001002", "unload", "load:5001002"), groups.operations);
    }

    @Test void failedCleanupInvalidatesCallbacksAndRetriesBeforeLoadingAnotherSchedule() {
        var groups = new Groups();
        var lifecycle = new CrucibleSceneLifecycle(groups);
        lifecycle.update(5001002, true, false);
        long old = lifecycle.ticket();
        groups.failUnload = true;
        assertThrows(IllegalStateException.class, lifecycle::close);
        assertFalse(lifecycle.isCurrent(old));
        assertThrows(IllegalStateException.class, () -> lifecycle.update(5001003, true, false));
        assertFalse(lifecycle.isMounted());
        groups.failUnload = false;
        lifecycle.update(5001003, true, false);
        assertEquals(List.of("load:5001002", "unload", "unload", "unload", "load:5001003"), groups.operations);
    }

    @Test void eachWorldHasItsOwnLifetimeAndActivityExpiryIsExclusive() {
        var one = new CrucibleSceneLifecycle(new Groups());
        var two = new CrucibleSceneLifecycle(new Groups());
        one.update(5001002, true, false);
        two.update(5001002, true, false);
        long second = two.ticket();
        one.close();
        assertTrue(two.isCurrent(second));
        var config = new ActivityConfigItem();
        config.setActivityId(5001); config.setScheduleId(5001002);
        config.setBeginTime(new Date(1000)); config.setEndTime(new Date(2000));
        assertEquals(0, CrucibleSceneLifecycle.activeSchedule(config, 999));
        assertEquals(5001002, CrucibleSceneLifecycle.activeSchedule(config, 1000));
        assertEquals(0, CrucibleSceneLifecycle.activeSchedule(config, 2000));
        config.setDisabled(true);
        assertEquals(0, CrucibleSceneLifecycle.activeSchedule(config, 1500));
    }

    @Test void realSceneUsesBlock3003AndLoadsAllEightScriptsWithIndependentBindings() throws Exception {
        var meta = SceneMeta.of(3);
        assertNotNull(meta);
        var first = CrucibleSceneController.mainGroup(meta.blocks.values()).load(3);
        var second = CrucibleSceneController.mainGroup(meta.blocks.values()).load(3);
        assertEquals(3003, first.block_id);
        assertNotSame(first.getBindings(), second.getBindings());
        assertNull(meta.blocks.get(3003).groups, "Mounting cannot modify shared block metadata");
        var bindings = ScriptLoader.getEngine().createBindings();
        ScriptLoader.eval(ScriptLoader.getScript("Scene/3/scene3_block3003.lua"), bindings);
        var metadataGroups = ScriptLoader.getSerializer().toList(SceneGroup.class, bindings.get("groups"));
        for (int id : CrucibleSceneController.GROUP_IDS) {
            var group = id == CrucibleSceneController.MAIN_GROUP ? first
                    : metadataGroups.stream().filter(item -> item.id == id).findFirst().orElseThrow().load(3);
            assertNotNull(group.init_config, "Missing script for " + id);
            assertNotNull(group.getSuiteByIndex(1));
            if (id != CrucibleSceneController.MAIN_GROUP) {
                assertTrue(group.getSuiteByIndex(1).monsters.isEmpty());
                assertTrue(group.getSuiteByIndex(1).gadgets.isEmpty(), "Inactive stage groups must be empty");
            }
        }
        assertThrows(IllegalStateException.class, () -> CrucibleSceneController.mainGroup(List.of()));
    }
}
