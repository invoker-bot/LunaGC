package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-21, uid 10001) as {3:len[4]=87ade204} -- four raw bytes that
// do not parse as a message, and no name in the proto dump, so the harvest logs it as UNKNOWN and the
// opcode table pins the id itself. No generated proto, no known Rsp, so the payload is dropped
// unanswered. The handler exists to close the harvest loop: an opcode with a registered handler is no
// longer announced as unhandled, so the backlog stays a list of what is genuinely still missing.
@Opcodes(PacketOpcodes.UnnamedOpcode21870)
public class HandlerUnnamedOpcode21870 extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
