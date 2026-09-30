package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerStartMatchReqOuterClass.PlayerStartMatchReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.PlayerStartMatchReq)
public final class HandlerPlayerStartMatchReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = PlayerStartMatchReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player != null) player.getServer().getCrucibleMatchSystem().start(player, request.getMatchTypeValue(), request.getMpPlayId(), request.getMatchId(), request.getDungeonId());
    }
}
