package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ExecuteGadgetLuaRspOuterClass.ExecuteGadgetLuaRsp;
import emu.grasscutter.server.packet.send.PacketExecuteGadgetLuaRsp;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrucibleLuaResponseProtocolTest {
    @Test void nativeResponseRecognizesTheSignedReturnCodeAtTagNine() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        wire.writeInt32(9, -1); wire.flush();
        var response = ExecuteGadgetLuaRsp.parseFrom(bytes.toByteArray());
        assertEquals(-1, response.getRetcode(), "Native reader 0x1513b0070 accepts tag 72, not the old tag 112");
        assertEquals(UnknownFieldSet.getDefaultInstance(), response.getUnknownFields());
        assertEquals(1, response.getDescriptorForType().getFields().size());
    }
    @Test void outgoingAckUsesTheNativeRegisteredOpcodeAndPreservesFailure() throws Exception {
        var packet = new PacketExecuteGadgetLuaRsp(-1);
        assertEquals(302, packet.getOpcode(), "Native command getter 0x1513b0330 returns 302");
        assertEquals(302, PacketOpcodes.ExecuteGadgetLuaRsp);
        var fields = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(List.of(-1L), fields.getField(9).getVarintList());
        assertFalse(fields.hasField(14));
        assertEquals(-1, ExecuteGadgetLuaRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(0, new PacketExecuteGadgetLuaRsp(0).getData().length);
    }
}
