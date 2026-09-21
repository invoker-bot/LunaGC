package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAnecdoteGetDataRsp;

// The anecdote ("story snapshot") request has no generated proto on this version,
// so the payload is left unparsed and an empty Rsp is returned. What matters is
// that the client receives a reply and the opcode stops filling the backlog.
@Opcodes(PacketOpcodes.AnecdoteGetDataReq)
public class HandlerAnecdoteGetDataReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(new PacketAnecdoteGetDataRsp());
    }
}
