package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client with an empty payload and no name in the proto dump -- see the
// pinned-opcode block in PacketOpcodes. There is no known Rsp and no proto to parse against, so
// the payload is dropped unanswered. The handler exists to close the harvest loop: an opcode with
// a registered handler is no longer announced as unhandled, so the backlog stays a list of what
// is genuinely still missing.
@Opcodes(PacketOpcodes.UnnamedOpcode26079)
public class HandlerUnnamedOpcode26079 extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
