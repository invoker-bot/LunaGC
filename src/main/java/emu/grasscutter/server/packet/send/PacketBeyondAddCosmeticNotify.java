package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BeyondAddCosmeticNotifyOuterClass.BeyondAddCosmeticNotify;
import emu.grasscutter.net.proto.BeyondOwnedCostumeOuterClass.BeyondOwnedCostume;
import java.util.List;

public final class PacketBeyondAddCosmeticNotify extends BasePacket {
    public PacketBeyondAddCosmeticNotify(List<BeyondOwnedCostume> added) {
        super(PacketOpcodes.BeyondAddCosmeticNotify);
        setData(BeyondAddCosmeticNotify.newBuilder().addAllOwnedCostumeList(added));
    }
}
