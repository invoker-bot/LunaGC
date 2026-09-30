package emu.grasscutter.scripts.data.controller;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

class EntityControllerResultTest {
    @Test void luaFailureMustNotBecomeASuccessfulClientAck() {
        assertEquals(-1, EntityController.clientExecuteResult(LuaValue.valueOf(-1)));
        assertEquals(1, EntityController.clientExecuteResult(LuaValue.ONE));
    }
    @Test void successfulAndImplicitSuccessfulScriptsKeepTheirPreviousContract() {
        assertEquals(0, EntityController.clientExecuteResult(LuaValue.ZERO));
        assertEquals(0, EntityController.clientExecuteResult(LuaValue.NIL));
    }
}
