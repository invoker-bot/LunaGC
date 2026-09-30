package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetPlayStartNotifyOuterClass.GadgetPlayStartNotify;

public final class PacketGadgetPlayStartNotify extends BasePacket {
    public PacketGadgetPlayStartNotify(int entityId, int startTime) {
        super(PacketOpcodes.GadgetPlayStartNotify);
        setData(GadgetPlayStartNotify.newBuilder().setEntityId(entityId).setPlayType(1).setStartTime(startTime));
    }
}
