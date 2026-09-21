package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-21, uid 10001) with an empty payload -- the map-mark-tip
// panel opening, nothing to read. There is no generated proto for the message and no known Rsp
// opcode, so there is nothing to answer it with either; the payload is dropped unanswered. The
// handler exists to close the harvest loop: an opcode with a registered handler is no longer
// announced as unhandled, so the backlog stays a list of what is genuinely still missing.
@Opcodes(PacketOpcodes.GetMapMarkTipsReq)
public class HandlerGetMapMarkTipsReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
