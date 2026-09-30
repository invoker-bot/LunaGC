package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ActivityTakeWatcherRewardRspOuterClass;

public class PacketActivityTakeWatcherRewardRsp extends BasePacket {

    public PacketActivityTakeWatcherRewardRsp(int activityId, int watcherId) {
        this(activityId, watcherId, 0);
    }

    public PacketActivityTakeWatcherRewardRsp(int activityId, int watcherId, int retcode) {
        super(PacketOpcodes.ActivityTakeWatcherRewardRsp);

        var proto = ActivityTakeWatcherRewardRspOuterClass.ActivityTakeWatcherRewardRsp.newBuilder();

        proto.setActivityId(activityId).setWatcherId(watcherId).setRetcode(retcode);

        this.setData(proto);
    }
}
