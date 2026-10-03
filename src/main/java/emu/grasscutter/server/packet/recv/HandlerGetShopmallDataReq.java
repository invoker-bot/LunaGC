package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopmallDataReqOuterClass.GetShopmallDataReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetShopmallDataRsp;

@Opcodes(PacketOpcodes.GetShopmallDataReq)
public class HandlerGetShopmallDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = GetShopmallDataReq.parseFrom(payload);
        session.getServer().getShopSystem().sendProductPriceCatalog(session);
        session.send(
                new PacketGetShopmallDataRsp(
                        session.getServer().getShopSystem(), req.getShopType(), req.getParam()));
    }
}
