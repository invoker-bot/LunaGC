package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.activity.crucible.GadgetPlayState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetPlayStopNotifyOuterClass.GadgetPlayStopNotify;
import emu.grasscutter.net.proto.GadgetPlayUidInfoOuterClass.GadgetPlayUidInfo;
import java.util.List;

public final class PacketGadgetPlayStopNotify extends BasePacket {
    public PacketGadgetPlayStopNotify(int entityId, GadgetPlayState.Change change, List<GadgetPlayUidInfo> members) {
        super(PacketOpcodes.GadgetPlayStopNotify);
        setData(GadgetPlayStopNotify.newBuilder().setEntityId(entityId).setPlayType(1)
                .setIsSuccess(change.type() == GadgetPlayState.ChangeType.SUCCEEDED)
                .setScore(change.progress()).setCostTime(change.costTime()).addAllUidInfoList(members));
    }
}
