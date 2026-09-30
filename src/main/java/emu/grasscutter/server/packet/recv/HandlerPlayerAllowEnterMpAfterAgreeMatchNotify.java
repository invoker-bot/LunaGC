package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerAllowEnterMpAfterAgreeMatchNotifyOuterClass.PlayerAllowEnterMpAfterAgreeMatchNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.PlayerAllowEnterMpAfterAgreeMatchNotify)
public final class HandlerPlayerAllowEnterMpAfterAgreeMatchNotify extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = PlayerAllowEnterMpAfterAgreeMatchNotify.parseFrom(payload);
        var player = session.getPlayer();
        if (player != null) player.getServer().getCrucibleMatchSystem().allowEnter(player, request.getTargetUid());
    }
}
