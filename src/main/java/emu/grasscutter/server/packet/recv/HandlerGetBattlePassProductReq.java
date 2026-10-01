package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetBattlePassProductReqOuterClass.GetBattlePassProductReq;
import emu.grasscutter.net.proto.GetBattlePassProductRspOuterClass.GetBattlePassProductRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.GetBattlePassProductReq)
public class HandlerGetBattlePassProductReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int type = GetBattlePassProductReq.parseFrom(payload).getPlayType();
        var product = session.getServer().getShopSystem().getFreeStore().productForPlayType(type);
        var response = GetBattlePassProductRsp.newBuilder().setPlayType(type);
        if (product == null) response.setRetcode(1);
        else response.setProductId(product.productId()).setPriceTier(product.priceTier());
        var packet = new BasePacket(PacketOpcodes.GetBattlePassProductRsp);
        packet.setData(response.build());
        session.send(packet);
    }
}
