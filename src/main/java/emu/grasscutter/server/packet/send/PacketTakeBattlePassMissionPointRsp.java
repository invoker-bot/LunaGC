package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TakeBattlePassMissionPointRspOuterClass.TakeBattlePassMissionPointRsp;
import java.util.List;

public class PacketTakeBattlePassMissionPointRsp extends BasePacket {
    public PacketTakeBattlePassMissionPointRsp(List<Integer> claimedMissions) {
        super(PacketOpcodes.TakeBattlePassMissionPointRsp);
        setData(
                TakeBattlePassMissionPointRsp.newBuilder().addAllMissionIdList(claimedMissions).build());
    }
}
