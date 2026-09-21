package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-21, uid 10001) with an empty payload -- the mutelist panel
// opening. No generated proto, no known Rsp, so nothing to answer with; the payload is dropped
// unanswered. The handler exists to close the harvest loop: an opcode with a registered handler is no
// longer announced as unhandled, so the backlog stays a list of what is genuinely still missing.
@Opcodes(PacketOpcodes.GetPlayerMutelistReq)
public class HandlerGetPlayerMutelistReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
