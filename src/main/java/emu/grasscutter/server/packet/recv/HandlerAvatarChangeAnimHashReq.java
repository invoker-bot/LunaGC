package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-21, uid 10001) as {12:varint=42953967927298,
// 14:varint=1375957350} -- an avatar guid plus a hashed animation name, the client telling the server
// which anim state it switched to. No generated proto, so neither field can be read; the payload is
// dropped unanswered, which is safe because the client owns its own anim state. The handler exists
// to close the harvest loop: an opcode with a registered handler is no longer announced as
// unhandled, and @NoResponseExpected keeps its silence out of the unimplemented-request report
// too, so the backlog stays a list of what is genuinely still missing.
@Opcodes(PacketOpcodes.AvatarChangeAnimHashReq)
@NoResponseExpected
public class HandlerAvatarChangeAnimHashReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
