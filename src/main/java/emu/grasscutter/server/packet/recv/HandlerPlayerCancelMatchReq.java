package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerCancelMatchReqOuterClass.PlayerCancelMatchReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.PlayerCancelMatchReq)
public final class HandlerPlayerCancelMatchReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = PlayerCancelMatchReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player != null) player.getServer().getCrucibleMatchSystem().cancelRequest(player, request.getMatchTypeValue());
    }
}
