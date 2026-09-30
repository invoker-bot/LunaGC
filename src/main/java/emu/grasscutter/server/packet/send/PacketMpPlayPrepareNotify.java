package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.MpPlayPrepareNotifyOuterClass.MpPlayPrepareNotify;

public final class PacketMpPlayPrepareNotify extends BasePacket {
    public PacketMpPlayPrepareNotify(int playId, int prepareEndTime) {
        super(PacketOpcodes.MpPlayPrepareNotify);
        setData(MpPlayPrepareNotify.newBuilder().setMpPlayId(playId).setPrepareEndTime(prepareEndTime));
    }
}
