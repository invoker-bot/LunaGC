package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MpPlayGuestReplyInviteReqOuterClass.MpPlayGuestReplyInviteReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketMpPlay;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

@Opcodes(PacketOpcodes.MpPlayGuestReplyInviteReq)
public final class HandlerMpPlayGuestReplyInviteReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = MpPlayGuestReplyInviteReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player == null || player.getScene() == null) {
            session.send(PacketMpPlay.guestReplyResponse(request.getMpPlayId(), Retcode.RET_MP_REPLY_TIMEOUT.getNumber()));
            return;
        }
        player.getScene().getCrucibleSceneController().replyInvitation(player,
                request.getMpPlayId(), request.getIsAgree());
    }
}
