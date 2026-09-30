package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerGuestConfirmMatchReqOuterClass.PlayerGuestConfirmMatchReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.PlayerGuestConfirmMatchReq)
public final class HandlerPlayerGuestConfirmMatchReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = PlayerGuestConfirmMatchReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player != null) player.getServer().getCrucibleMatchSystem().confirm(player, request.getMatchTypeValue(), request.getIsAgreed(), true);
    }
}
