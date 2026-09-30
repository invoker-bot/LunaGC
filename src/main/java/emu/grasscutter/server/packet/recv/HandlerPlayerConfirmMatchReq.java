package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerConfirmMatchReqOuterClass.PlayerConfirmMatchReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.PlayerConfirmMatchReq)
public final class HandlerPlayerConfirmMatchReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = PlayerConfirmMatchReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player != null) player.getServer().getCrucibleMatchSystem().confirm(player, request.getMatchTypeValue(), request.getIsAgreed(), false);
    }
}
