package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopBatchReqOuterClass.GetShopBatchReq;
import emu.grasscutter.net.proto.GetShopBatchRspOuterClass.GetShopBatchRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetShopRsp;

@Opcodes(PacketOpcodes.GetShopBatchReq)
public class HandlerGetShopBatchReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = GetShopBatchReq.parseFrom(payload);
        var response = GetShopBatchRsp.newBuilder();
        for (int type : request.getShopTypeListList().stream().distinct().limit(256).toList()) {
            response.addShopList(
                    PacketGetShopRsp.buildShop(
                            session.getPlayer(), session.getServer().getShopSystem(), type));
        }
        session.getPlayer().save();
        var packet = new BasePacket(PacketOpcodes.GetShopBatchRsp);
        packet.setData(response.build());
        session.send(packet);
    }
}
