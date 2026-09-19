package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.UseWidgetCreateGadgetRspOuterClass.UseWidgetCreateGadgetRsp;

public class PacketUseWidgetCreateGadgetRsp extends BasePacket {

    public PacketUseWidgetCreateGadgetRsp(int retcode, int materialId) {
        super(PacketOpcodes.UseWidgetCreateGadgetRsp);

        this.setData(
                UseWidgetCreateGadgetRsp.newBuilder()
                        .setRetcode(retcode)
                        .setMaterialId(materialId)
                        .build());
    }

    public PacketUseWidgetCreateGadgetRsp(int materialId) {
        this(0, materialId);
    }
}
