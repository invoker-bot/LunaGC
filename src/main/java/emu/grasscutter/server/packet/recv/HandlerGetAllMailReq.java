package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetAllMailReqOuterClass.GetAllMailReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetAllMailRsp;

@Opcodes(PacketOpcodes.GetAllMailReq)
public class HandlerGetAllMailReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        GetAllMailReq req = GetAllMailReq.parseFrom(payload);

        // The client refreshes its inbox view with this request; the mail list is also pushed
        // unsolicited via MailChangeNotify, so this just re-sends the current state on demand.
        session.getPlayer().sendPacket(new PacketGetAllMailRsp(session.getPlayer(), req.getIsCollected()));
    }
}
