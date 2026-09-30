package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.AsterActivityDetailInfoOuterClass.AsterActivityDetailInfo;
import emu.grasscutter.net.proto.AsterLittleDetailInfoOuterClass.AsterLittleDetailInfo;
import emu.grasscutter.net.proto.AsterLittleInfoNotifyOuterClass.AsterLittleInfoNotify;
import emu.grasscutter.net.proto.AsterMiscInfoNotifyOuterClass.AsterMiscInfoNotify;
import emu.grasscutter.net.proto.SelectAsterMidDifficultyReqOuterClass.SelectAsterMidDifficultyReq;
import emu.grasscutter.net.proto.SelectAsterMidDifficultyRspOuterClass.SelectAsterMidDifficultyRsp;
import org.junit.jupiter.api.Test;

class AsterProtocolTest {
    @Test
    void nativeMidDifficultyPacketsUseTheirOwnRequestAndResponseTags() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        out.writeUInt32(2, 42);
        out.writeUInt32(7, 2001009);
        out.writeUInt32(12, 5);
        out.flush();
        var request = SelectAsterMidDifficultyReq.parseFrom(bytes.toByteArray());
        assertEquals(42, request.getGadgetEntityId());
        assertEquals(2001009, request.getScheduleId());
        assertEquals(5, request.getDifficultyId());
        bytes.reset();
        out = CodedOutputStream.newInstance(bytes);
        out.writeUInt32(1, 42);
        out.writeUInt32(3, 2001009);
        out.writeInt32(12, -1);
        out.writeUInt32(15, 5);
        out.flush();
        var response = SelectAsterMidDifficultyRsp.parseFrom(bytes.toByteArray());
        assertEquals(42, response.getGadgetEntityId());
        assertEquals(2001009, response.getScheduleId());
        assertEquals(-1, response.getRetcode());
        assertEquals(5, response.getDifficultyId());
        assertEquals(29428, emu.grasscutter.net.packet.PacketOpcodes.SelectAsterMidDifficultyReq);
        assertEquals(2401, emu.grasscutter.net.packet.PacketOpcodes.SelectAsterMidDifficultyRsp);
    }

    @Test
    void nativeBalanceNotificationUsesTheSameUnresolvedScalarNames() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        out.writeUInt32(13, 23);
        out.writeUInt32(15, 29);
        out.flush();
        var notification = AsterMiscInfoNotify.parseFrom(bytes.toByteArray());
        assertEquals(23, notification.getDKOEMPLNJBP());
        assertEquals(29, notification.getJAHBDIPMKMI());
        assertEquals(24878, emu.grasscutter.net.packet.PacketOpcodes.AsterMiscInfoNotify);
    }

    @Test
    void nativeLittleNotificationReaderAcceptsFieldFourInsidePacket20608() throws Exception {
        var nested =
                AsterLittleDetailInfo.newBuilder().setBeginTime(123).setStageId(2).setIsOpen(true).build();
        var bytes = new java.io.ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        out.writeMessage(4, nested);
        out.flush();
        assertEquals(nested, AsterLittleInfoNotify.parseFrom(bytes.toByteArray()).getInfo());
        assertEquals(20608, emu.grasscutter.net.packet.PacketOpcodes.AsterLittleInfoNotify);
    }

    @Test
    void nativeDetailCurrencySlotsAndActivityOneofUseVerifiedWireTags() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        out.writeUInt32(1, 13);
        out.writeUInt32(5, 17);
        out.flush();
        var detail = AsterActivityDetailInfo.parseFrom(bytes.toByteArray());
        assertEquals(13, detail.getDKOEMPLNJBP());
        assertEquals(17, detail.getJAHBDIPMKMI());
        bytes.reset();
        out = CodedOutputStream.newInstance(bytes);
        out.writeMessage(1167, detail);
        out.flush();
        assertEquals(detail, ActivityInfo.parseFrom(bytes.toByteArray()).getAsterInfo());
    }
}
