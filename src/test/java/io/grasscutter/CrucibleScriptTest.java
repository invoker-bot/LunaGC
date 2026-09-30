package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.activity.crucible.GadgetPlayState;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

class CrucibleScriptTest {
    @Test void originalResourceScriptScoresClotsByPlayerAndDoesNotCountRepeatedSubmission() throws Exception {
        var script = Path.of("resources/Scripts/Gadget/Crucible.lua");
        Assumptions.assumeTrue(Files.exists(script), "Needs the checked-out resources submodule");
        var state = new GadgetPlayState();
        var floats = new HashMap<String, Integer>();
        floats.put("_Crucible_Grume_Player_Sum_Inferior", 2);
        floats.put("_Crucible_Grume_Player_Sum_Superior", 1);
        floats.put("_Crucible_Grume_Player_In_Burst", 0);
        floats.put("_Team_Real_Grume_ElemType", 2); // Fire in the original script dictionary.
        var lib = new LuaTable();
        bind(lib, "PrintLog", args -> LuaValue.ZERO);
        bind(lib, "GetUidByTeamEntityId", args -> LuaValue.valueOf(args.arg(2).toint()));
        bind(lib, "GetTeamAbilityFloatValue", args -> LuaValue.valueOf(floats.getOrDefault(args.arg(3).tojstring(), 0)));
        bind(lib, "GetGadgetPlayUidValue", args -> LuaValue.valueOf(state.getUidValue(args.arg(4).toint(), args.arg(5).tojstring())));
        bind(lib, "SetGadgetPlayUidValue", args -> {
            state.setUidValue(args.arg(4).toint(), args.arg(5).tojstring(), args.arg(6).toint());
            return LuaValue.ZERO;
        });
        bind(lib, "AddGadgetPlayProgress", args -> { state.addProgress(args.arg(4).toint()); return LuaValue.ZERO; });
        var globals = JsePlatform.standardGlobals();
        globals.set("ScriptLib", lib);
        globals.load(Files.readString(script), "Crucible.lua").call();
        var submit = globals.get("OnClientExecuteReq");
        submit.invoke(new LuaValue[] {LuaValue.NIL, LuaValue.ZERO, LuaValue.ONE, LuaValue.valueOf(10001)});
        assertEquals(1600, state.getProgress()); // 2 * 300 + 1 * 1000.
        assertEquals(3, state.getUidValue(10001, "Fire_ball"));
        submit.invoke(new LuaValue[] {LuaValue.NIL, LuaValue.ZERO, LuaValue.ONE, LuaValue.valueOf(10001)});
        assertEquals(1600, state.getProgress(), "Cumulative ability values must not double-count");
        floats.put("_Crucible_Grume_Player_In_Burst", 1);
        submit.invoke(new LuaValue[] {LuaValue.NIL, LuaValue.ZERO, LuaValue.ONE, LuaValue.valueOf(10002)});
        assertEquals(3200, state.getUidValue(10002, "Fire")); // First burst submission doubles points.
        assertEquals(4800, state.getProgress());
        assertEquals(1600, state.getUidValue(10001, "Fire"));
        assertEquals(0, new GadgetPlayState().getProgress(), "Scene instances must be isolated");
    }

    private static void bind(LuaTable library, String name, java.util.function.Function<Varargs, LuaValue> function) {
        library.set(name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return function.apply(args); }
        });
    }
}
