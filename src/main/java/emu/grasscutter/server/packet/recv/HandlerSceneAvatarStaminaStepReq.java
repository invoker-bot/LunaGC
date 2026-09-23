package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-21, uid 10001) as {3:msg[15]={1:fixed32=...,
// 2:fixed32=..., 3:fixed32=...}} -- a position, the client draining stamina one step at a time. This
// is high-frequency movement traffic the client is authoritative for, and there is no generated
// proto to read the position with, so the step is dropped unanswered; the client's own stamina bar
// keeps working. The handler exists to close the harvest loop: an opcode with a registered handler
// is no longer announced as unhandled, and @NoResponseExpected keeps its silence out of the
// unimplemented-request report too, so the backlog stays a list of what is genuinely still missing.
@Opcodes(PacketOpcodes.SceneAvatarStaminaStepReq)
@NoResponseExpected
public class HandlerSceneAvatarStaminaStepReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
