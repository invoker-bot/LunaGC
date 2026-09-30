package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MpPlayOwnerCheckReqOuterClass.MpPlayOwnerCheckReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketMpPlay;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

@Opcodes(PacketOpcodes.MpPlayOwnerCheckReq)
public final class HandlerMpPlayOwnerCheckReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = MpPlayOwnerCheckReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player == null || player.getScene() == null) {
            session.send(PacketMpPlay.ownerCheck(request.getMpPlayId(), request.getIsSkipMatch(),
                    Retcode.RET_MP_NOT_IN_MY_WORLD.getNumber(), 0));
            return;
        }
        player.getScene().getCrucibleSceneController().checkOwnerRequest(player,
                request.getMpPlayId(), request.getIsSkipMatch());
    }
}
