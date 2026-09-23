package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-23, uid 10001, scene 1004) as {3:varint=1,
// 12:varint=2097983} -- a room id and the entity crossing into it, sent as the client crosses a
// room boundary. No generated proto, so neither field can be read, and a Notify is not something
// the client waits on, so dropping it unanswered costs it nothing. The handler exists to close the
// harvest loop: an opcode with a registered handler is no longer announced as unhandled, so the
// backlog stays a list of what is genuinely still missing.
@Opcodes(PacketOpcodes.EntityMoveRoomNotify)
public class HandlerEntityMoveRoomNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
