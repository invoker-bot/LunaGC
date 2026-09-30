package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetPlayUidOpNotifyOuterClass.GadgetPlayUidOpNotify;
import java.util.List;

public final class PacketGadgetPlayUidOpNotify extends BasePacket {
    public PacketGadgetPlayUidOpNotify(int entityId, List<Integer> uids, int op, String name, List<Integer> params) {
        super(PacketOpcodes.GadgetPlayUidOpNotify);
        setData(GadgetPlayUidOpNotify.newBuilder().setEntityId(entityId).setPlayType(1)
                .addAllUidList(uids).setOp(op).setOpName(name).addAllParamList(params));
    }
}
