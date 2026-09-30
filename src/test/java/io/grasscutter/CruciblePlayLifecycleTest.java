package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.crucible.GadgetPlayState;
import emu.grasscutter.game.activity.crucible.GadgetPlayState.ChangeType;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.ScriptLib;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.scripts.serializer.LuaSerializer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.*;

class CruciblePlayLifecycleTest {
    @BeforeAll static void initializeRuntimePaths() {
        Grasscutter.getConfig(); // Bootstrap configuration before FileUtils, as server startup does.
    }

    private static Globals originalMainGroup() {
        var source = ScriptLoader.readScript("Scene/3/scene3_group305001001.lua");
        assertNotNull(source, "The missing historical group must be available from the jar");
        var globals = JsePlatform.standardGlobals();
        globals.set("EventType", CoerceJavaToLua.coerce(new EventType()));
        globals.load(source, "original Crucible main group").call();
        return globals;
    }

    private static SceneGadgetCrucibleConfig originalConfig() {
        return new LuaSerializer().toList(SceneGadget.class, originalMainGroup().get("gadgets"))
                .stream().filter(gadget -> gadget.config_id == 1001).findFirst().orElseThrow().crucible_config;
    }

    @Test void recoveredGroupDeserializesTheOriginalSettingsAndValidSuites() {
        var globals = originalMainGroup();
        var serializer = new LuaSerializer();
        var gadgets = serializer.toList(SceneGadget.class, globals.get("gadgets"));
        var crucible = gadgets.stream().filter(gadget -> gadget.config_id == 1001).findFirst().orElseThrow();
        assertEquals(70330024, crucible.gadget_id);
        assertNotNull(crucible.crucible_config);
        assertEquals(900, crucible.crucible_config.duration);
        assertEquals(3, crucible.crucible_config.start_cd);
        assertEquals(List.of(0, 5000, 20000, 35000), crucible.crucible_config.progress_stage);
        assertEquals(1, crucible.crucible_config.mp_play_id);
        var monsters = serializer.toList(SceneMonster.class, globals.get("monsters"));
        var monsterIds = new HashSet<>(monsters.stream().map(monster -> monster.config_id).toList());
        serializer.toList(SceneSuite.class, globals.get("suites")).forEach(suite ->
                assertTrue(monsterIds.containsAll(suite.monsters), "Every suite monster must be defined"));
    }

    @Test void countdownStagesSuccessAndRerunFollowTheOriginalThresholds() {
        var state = new GadgetPlayState();
        assertTrue(state.start(originalConfig(), 1000));
        assertFalse(state.start(originalConfig(), 1001), "Cannot restart an active round");
        assertTrue(state.addProgress(5000, 1002).isEmpty());
        assertEquals(0, state.getProgress());
        assertEquals(ChangeType.STARTED, state.tick(1003).get(0).type());
        assertTrue(state.tick(1003).isEmpty(), "Countdown completes once");
        var stage1 = state.addProgress(5000, 1004);
        assertEquals(1, stage1.get(0).stage());
        assertEquals(0, stage1.get(0).previousStage());
        var remaining = state.addProgress(40000, 1005);
        assertEquals(List.of(ChangeType.STAGE_CHANGED, ChangeType.STAGE_CHANGED, ChangeType.SUCCEEDED),
                remaining.stream().map(GadgetPlayState.Change::type).toList());
        assertEquals(35000, state.getProgress());
        assertTrue(state.addProgress(300, 1006).isEmpty());
        state.setUidValue(10001, "Fire", 5000);
        assertTrue(state.start(originalConfig(), 1100));
        assertEquals(0, state.getProgress());
        assertEquals(0, state.getUidValue(10001, "Fire"));
    }

    @Test void deadlineRejectsLateScoreAndTimeoutAndCancellationFireOnce() {
        var state = new GadgetPlayState();
        state.start(originalConfig(), 1000);
        state.tick(1003);
        state.addProgress(6000, 1100);
        state.addProgress(-6000, 1101);
        assertEquals(5000, state.getProgress(), "Cannot regress below the current stage");
        assertEquals(ChangeType.TIMED_OUT, state.addProgress(35000, 1903).get(0).type());
        assertEquals(5000, state.getProgress(), "Score at the deadline does not count");
        assertTrue(state.tick(1904).isEmpty());
        state.start(originalConfig(), 2000);
        assertEquals(ChangeType.CANCELLED, state.stop().get(0).type());
        assertTrue(state.stop().isEmpty());
    }

    @Test void originalStageCallbackSelectsTheResourceGroupAndSuccessCleansAllSevenGroups() {
        var globals = originalMainGroup();
        var refreshes = new ArrayList<String>();
        var lib = new LuaTable();
        for (var name : List.of("PrintLog", "SetGroupVariableValue", "ShowTemplateReminder", "CancelGroupTimerEvent",
                "RemoveExtraGroupSuite", "KillGroupEntity", "KillEntityByConfigId", "SetGadgetEnableInteract",
                "SetGroupGadgetStateByConfigId", "GadgetPlayUidOp", "KillExtraGroupSuite")) {
            bind(lib, name, args -> LuaValue.ZERO);
        }
        bind(lib, "GetServerTime", args -> LuaValue.valueOf(1000));
        bind(lib, "GetGroupSuite", args -> LuaValue.ONE);
        bind(lib, "GetSceneUidList", args -> new LuaTable());
        bind(lib, "RefreshGroup", args -> {
            var info = args.arg(2);
            refreshes.add(info.get("group_id").toint() + ":" + info.get("suite").toint());
            return LuaValue.ZERO;
        });
        globals.set("ScriptLib", lib);
        var policy = new LuaTable();
        policy.set("GROUP_KILL_MONSTER", 2);
        globals.set("GroupKillPolicy", policy);
        var event = new LuaTable();
        event.set("param1", 1); event.set("param2", 0); event.set("param3", 1);
        globals.get("action_EVENT_GADGET_LUA_NOTIFY_1002").call(LuaValue.NIL, event);
        assertTrue(refreshes.contains("133003554:2"));
        refreshes.clear();
        globals.get("action_EVENT_GADGET_PLAY_STOP_1003").call(LuaValue.NIL, event);
        assertTrue(refreshes.containsAll(List.of("133003554:1", "133003555:1", "133003556:1",
                "133003557:1", "133003568:1", "133003549:1", "133003550:1")));
    }

    @Test void luaServerTimeUsesEpochSecondsForBurstDurations() {
        long before = System.currentTimeMillis() / 1000;
        long time = new ScriptLib().GetServerTime();
        long after = System.currentTimeMillis() / 1000;
        assertTrue(time >= before && time <= after);
    }

    private static void bind(LuaTable library, String name, java.util.function.Function<Varargs, LuaValue> function) {
        library.set(name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return function.apply(args); }
        });
    }
}
