package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SeeMonsterReqOuterClass.SeeMonsterReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSeeMonsterRsp;

@Opcodes(PacketOpcodes.SeeMonsterReq)
public class HandlerSeeMonsterReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SeeMonsterReq req = SeeMonsterReq.parseFrom(payload);

        session.send(new PacketSeeMonsterRsp());
    }
}
