package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-21, uid 10001) as {1:varint=1, 9:varint=42953967927333} --
// a state plus a reliquary guid, the client locking or starring an artifact. No generated proto and no
// known Rsp, so the state is not applied and nothing is answered; artifact state lives client-side
// until a proto exists. The handler exists to close the harvest loop: an opcode with a registered
// handler is no longer announced as unhandled, so the backlog stays a list of what is genuinely still
// missing.
@Opcodes(PacketOpcodes.SetReliquaryStarStateReq)
public class HandlerSetReliquaryStarStateReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
