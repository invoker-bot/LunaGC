package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SalesmanDeliverItemRspOuterClass.SalesmanDeliverItemRsp;

public final class PacketSalesmanDeliverItemRsp extends BasePacket {
    public PacketSalesmanDeliverItemRsp(int scheduleId, int retcode) {
        super(PacketOpcodes.SalesmanDeliverItemRsp);
        setData(SalesmanDeliverItemRsp.newBuilder().setScheduleId(scheduleId).setRetcode(retcode).build());
    }
}
