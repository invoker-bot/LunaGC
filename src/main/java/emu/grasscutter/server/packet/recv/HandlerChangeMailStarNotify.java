package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ChangeMailStarNotifyOuterClass.ChangeMailStarNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.ChangeMailStarNotify)
public class HandlerChangeMailStarNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = ChangeMailStarNotify.parseFrom(payload);
        session
                .getPlayer()
                .getMailHandler()
                .updateClientMail(request.getMailIdListList(), request.getIsStar());
    }
}
