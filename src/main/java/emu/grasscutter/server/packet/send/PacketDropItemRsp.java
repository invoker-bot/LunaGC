package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DropItemRspOuterClass.DropItemRsp;

public class PacketDropItemRsp extends BasePacket {

    public PacketDropItemRsp(int retcode, long guid, int storeTypeValue) {
        super(PacketOpcodes.DropItemRsp);

        this.setData(
                DropItemRsp.newBuilder()
                        .setRetcode(retcode)
                        .setGuid(guid)
                        .setStoreTypeValue(storeTypeValue)
                        .build());
    }

    public PacketDropItemRsp(long guid, int storeTypeValue) {
        this(0, guid, storeTypeValue);
    }
}
