package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-21, uid 10001) as {11:varint=356, 12:msg[26]={1:msg[15]={
// 1:fixed32, 2:fixed32, 3:fixed32}, 2:msg[5]={2:fixed32}, 4:varint=2}, 13:varint=617518,
// 15:varint=2097155} -- a motion state holding a quaternion, a velocity and an entity id, the client
// asking the server to force a position reconciliation. No generated proto, so nothing can be read or
// reconciled; the payload is dropped unanswered, and in single-player the client's own position is
// already the truth. The handler exists to close the harvest loop: an opcode with a registered
// handler is no longer announced as unhandled, so the backlog stays a list of what is genuinely still
// missing.
@Opcodes(PacketOpcodes.EntityForceSyncReq)
public class HandlerEntityForceSyncReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
