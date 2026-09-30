package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SalesmanActivityDetailInfoOuterClass.SalesmanActivityDetailInfo;
import emu.grasscutter.net.proto.SalesmanTakeRewardReqOuterClass.SalesmanTakeRewardReq;
import emu.grasscutter.net.proto.SalesmanTakeRewardRspOuterClass.SalesmanTakeRewardRsp;
import emu.grasscutter.net.proto.SalesmanDeliverItemReqOuterClass.SalesmanDeliverItemReq;
import emu.grasscutter.net.proto.SalesmanDeliverItemRspOuterClass.SalesmanDeliverItemRsp;
import emu.grasscutter.server.packet.send.PacketSalesmanDeliverItemRsp;
import emu.grasscutter.net.proto.SalesmanStatusTypeOuterClass.SalesmanStatusType;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.Test;

class SalesmanProtocolTest {
    @Test void deliveryRequestAndResponseUseRecoveredNativeTags() throws Exception {
        assertEquals(988, PacketOpcodes.SalesmanDeliverItemReq);
        assertEquals(28807, PacketOpcodes.SalesmanDeliverItemRsp);
        var bytes = new ByteArrayOutputStream(); var out = CodedOutputStream.newInstance(bytes);
        out.writeUInt32(15, 5003009); out.flush();
        var request = SalesmanDeliverItemReq.parseFrom(bytes.toByteArray());
        assertEquals(5003009, request.getScheduleId()); assertArrayEquals(bytes.toByteArray(), request.toByteArray());
        bytes.reset(); out = CodedOutputStream.newInstance(bytes);
        out.writeInt32(12, -1); out.writeUInt32(14, 5003009); out.flush();
        var response = SalesmanDeliverItemRsp.parseFrom(bytes.toByteArray());
        assertEquals(-1, response.getRetcode()); assertEquals(5003009, response.getScheduleId());
        assertArrayEquals(bytes.toByteArray(), response.toByteArray());
        var packet = new PacketSalesmanDeliverItemRsp(5003009, -1);
        assertEquals(28807, packet.getOpcode()); assertArrayEquals(bytes.toByteArray(), packet.getData());
    }
    @Test void rewardRequestUsesNativeSevenOneTagsAndPacketId() throws Exception {
        assertEquals(21130, PacketOpcodes.SalesmanTakeRewardReq);
        var bytes = new ByteArrayOutputStream(); var out = CodedOutputStream.newInstance(bytes);
        out.writeUInt32(9, 5003009); out.writeUInt32(13, 3); out.flush();
        var request = SalesmanTakeRewardReq.parseFrom(bytes.toByteArray());
        assertEquals(5003009, request.getScheduleId()); assertEquals(3, request.getPosition());
        assertArrayEquals(bytes.toByteArray(), request.toByteArray());
    }
    @Test void rewardResponseRetcodeAndPositionAreNotTheOldThreeFiveTags() throws Exception {
        assertEquals(29368, PacketOpcodes.SalesmanTakeRewardRsp);
        var bytes = new ByteArrayOutputStream(); var out = CodedOutputStream.newInstance(bytes);
        out.writeUInt32(2, 5003009); out.writeUInt32(10, 470001); out.writeInt32(14, -1); out.writeUInt32(15, 3); out.flush();
        var response = SalesmanTakeRewardRsp.parseFrom(bytes.toByteArray());
        assertEquals(5003009, response.getScheduleId()); assertEquals(470001, response.getRewardId());
        assertEquals(-1, response.getRetcode()); assertEquals(3, response.getPosition());
        assertArrayEquals(bytes.toByteArray(), response.toByteArray());
    }
    @Test void detailReplacesPlaceholderTagsWithoutInventingUnresolvedMeanings() throws Exception {
        var detail=SalesmanActivityDetailInfo.newBuilder().setDayIndex(7).setStatus(SalesmanStatusType.SALESMAN_STATUS_DELIVERED)
                .setKOPLLPLDGGH(true).putSelectedRewardIdMap(3,470001).setAMMODINPKOF(1).setFLILLCHPLLH(2)
                .setMOPCCGMDDAE(3).setIKOGFJJCIJD(true).setFBDGELFLLGI(4).setPGMJOECPHIN(5).build();
        var wire=UnknownFieldSet.parseFrom(detail.toByteArray());
        assertEquals(Set.of(1,3,4,6,7,8,9,10,12,15),wire.asMap().keySet());
        assertEquals(List.of(7L),wire.getField(15).getVarintList());
        assertEquals(List.of(3L),wire.getField(10).getVarintList());
        assertEquals(List.of(1L),wire.getField(12).getVarintList());
        var entry=UnknownFieldSet.parseFrom(wire.getField(4).getLengthDelimitedList().get(0));
        assertEquals(List.of(3L),entry.getField(1).getVarintList());
        assertEquals(List.of(470001L),entry.getField(2).getVarintList());
    }
}
