package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.GetActivityInfoReqOuterClass.GetActivityInfoReq;
import emu.grasscutter.server.packet.recv.HandlerGetActivityInfoReq;
import emu.grasscutter.server.packet.send.PacketActivityInfoNotify;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Fixtures use tags observed in the supplied 7.1 client's activity module and codecs. */
class ActivityDisplayProtocolTest {
    @Test
    void activityModuleRequestUsesOpcode186AndPackedField14() throws Exception {
        // Native codec at 0x149ee49e0 uses tag 0x72; ids are 5001 and 5003.
        var bytes = HexFormat.of().parseHex("720489278b27");
        var request = GetActivityInfoReq.parseFrom(bytes);
        assertEquals(List.of(5001, 5003), request.getActivityIdListList());
        assertEquals(186, PacketOpcodes.GetActivityInfoReq);
        assertEquals(186, HandlerGetActivityInfoReq.class.getAnnotation(Opcodes.class).value());
        assertEquals(UnknownFieldSet.getDefaultInstance(), request.getUnknownFields());
        assertArrayEquals(bytes, request.toByteArray());
    }

    @Test
    void metConditionsAreReadableByNativeField1917AndNotifyField6() throws Exception {
        var info =
                ActivityInfo.newBuilder()
                        .setActivityId(5001)
                        .setActivityType(2)
                        .setScheduleId(5001005)
                        .addMeetCondList(500101)
                        .addMeetCondList(500103)
                        .build();
        var packet = new PacketActivityInfoNotify(info);
        assertEquals(8184, packet.getOpcode());
        var outer = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(Set.of(6), outer.asMap().keySet());
        var wire = UnknownFieldSet.parseFrom(outer.getField(6).getLengthDelimitedList().get(0));
        assertEquals(Set.of(2, 3, 10, 1917), wire.asMap().keySet());
        assertEquals(List.of(5001L), wire.getField(2).getVarintList());
        assertEquals(List.of(5001005L), wire.getField(3).getVarintList());
        assertEquals(List.of(2L), wire.getField(10).getVarintList());
        // Native reader handles tags 0x3be8 and 0x3bea (unpacked and packed).
        var conditions =
                CodedInputStream.newInstance(
                        wire.getField(1917).getLengthDelimitedList().get(0).toByteArray());
        assertEquals(500101, conditions.readUInt32());
        assertEquals(500103, conditions.readUInt32());
        assertTrue(conditions.isAtEnd());
        var unpacked = ActivityInfo.parseFrom(HexFormat.of().parseHex("e87785c31ee87787c31e"));
        assertEquals(List.of(500101, 500103), unpacked.getMeetCondListList());
        assertEquals(UnknownFieldSet.getDefaultInstance(), unpacked.getUnknownFields());
    }
}
