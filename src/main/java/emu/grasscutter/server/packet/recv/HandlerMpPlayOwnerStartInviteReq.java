package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MpPlayOwnerStartInviteReqOuterClass.MpPlayOwnerStartInviteReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketMpPlay;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

@Opcodes(PacketOpcodes.MpPlayOwnerStartInviteReq)
public final class HandlerMpPlayOwnerStartInviteReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = MpPlayOwnerStartInviteReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player == null || player.getScene() == null) {
            session.send(PacketMpPlay.startInvite(request.getMpPlayId(), request.getIsSkipMatch(),
                    Retcode.RET_MP_NOT_IN_MY_WORLD.getNumber()));
            return;
        }
        player.getScene().getCrucibleSceneController().startInvitation(player,
                request.getMpPlayId(), request.getIsSkipMatch());
    }
}
