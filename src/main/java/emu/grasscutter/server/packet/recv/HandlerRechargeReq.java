package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RechargeReqOuterClass.RechargeReq;
import emu.grasscutter.net.proto.RechargeRspOuterClass.RechargeRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.RechargeReq)
public class HandlerRechargeReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = RechargeReq.parseFrom(payload);
        var rsp = RechargeRsp.newBuilder();
        String id = "", kind = "";
        int count =
                (req.hasCardProduct() ? 1 : 0)
                        + (req.hasMcoinProduct() ? 1 : 0)
                        + (req.hasPlayProduct() ? 1 : 0)
                        + (req.hasBeyondMcoinProduct() ? 1 : 0);
        if (req.hasCardProduct()) {
            id = req.getCardProduct().getProductId();
            kind = "card";
        }
        if (req.hasMcoinProduct()) {
            id = req.getMcoinProduct().getProductId();
            kind = "crystals";
        }
        if (req.hasPlayProduct()) {
            id = req.getPlayProduct().getProductId();
            kind = "pass";
        }
        if (req.hasBeyondMcoinProduct()) {
            id = req.getBeyondMcoinProduct().getProductId();
            kind = "beyond_crystals";
        }
        rsp.setProductId(id);
        try {
            if (count != 1) throw new IllegalArgumentException("Exactly one product is required");
            session
                    .getServer()
                    .getShopSystem()
                    .getFreeStore()
                    .purchaseById(session.getPlayer(), id, kind);
        } catch (IllegalArgumentException e) {
            rsp.setRetcode(1);
        }
        var packet = new BasePacket(PacketOpcodes.RechargeRsp);
        packet.setData(rsp.build());
        session.send(packet);
    }
}
