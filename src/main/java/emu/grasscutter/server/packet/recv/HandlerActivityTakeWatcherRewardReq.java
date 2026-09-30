package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ActivityTakeWatcherRewardReqOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketActivityTakeWatcherRewardRsp;
import java.util.Optional;

@Opcodes(PacketOpcodes.ActivityTakeWatcherRewardReq)
public class HandlerActivityTakeWatcherRewardReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req =
                ActivityTakeWatcherRewardReqOuterClass.ActivityTakeWatcherRewardReq.parseFrom(payload);

        int retcode = Optional.ofNullable(
                        session
                                .getPlayer()
                                .getActivityManager()
                                .getPlayerActivityDataMap()
                                .get(req.getActivityId()))
                .map(x -> x.takeWatcherReward(req.getWatcherId())).orElse(Retcode.RET_ACTIVITY_CLOSE_VALUE);

        session.send(new PacketActivityTakeWatcherRewardRsp(req.getActivityId(), req.getWatcherId(), retcode));
    }
}
