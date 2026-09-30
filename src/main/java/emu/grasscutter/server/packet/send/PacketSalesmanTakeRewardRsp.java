package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SalesmanTakeRewardRspOuterClass.SalesmanTakeRewardRsp;

public final class PacketSalesmanTakeRewardRsp extends BasePacket {
    public PacketSalesmanTakeRewardRsp(int scheduleId, int position, int rewardId, int retcode) {
        super(PacketOpcodes.SalesmanTakeRewardRsp);
        setData(SalesmanTakeRewardRsp.newBuilder().setScheduleId(scheduleId).setPosition(position)
                .setRewardId(retcode == 0 ? rewardId : 0).setRetcode(retcode).build());
    }
}
