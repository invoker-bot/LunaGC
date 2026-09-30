package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetPlayDataNotifyOuterClass.GadgetPlayDataNotify;

public final class PacketGadgetPlayDataNotify extends BasePacket {
    public PacketGadgetPlayDataNotify(int entityId, int progress) {
        super(PacketOpcodes.GadgetPlayDataNotify);
        setData(GadgetPlayDataNotify.newBuilder().setEntityId(entityId).setPlayType(1).setProgress(progress));
    }
}
