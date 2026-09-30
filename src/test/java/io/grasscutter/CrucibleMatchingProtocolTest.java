package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.*;
import emu.grasscutter.data.excels.activity.MpPlayMatchData;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PlayerStartMatchReqOuterClass.PlayerStartMatchReq;
import emu.grasscutter.net.proto.PlayerCancelMatchReqOuterClass.PlayerCancelMatchReq;
import emu.grasscutter.net.proto.PlayerConfirmMatchReqOuterClass.PlayerConfirmMatchReq;
import emu.grasscutter.net.proto.PlayerGuestConfirmMatchReqOuterClass.PlayerGuestConfirmMatchReq;
import emu.grasscutter.net.proto.PlayerAllowEnterMpAfterAgreeMatchNotifyOuterClass.PlayerAllowEnterMpAfterAgreeMatchNotify;
import emu.grasscutter.server.packet.send.PacketCrucibleMatch;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.Test;

class CrucibleMatchingProtocolTest {
    private static byte[] scalars(Map<Integer, Long> fields) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        for (var entry : fields.entrySet()) wire.writeUInt64(entry.getKey(), entry.getValue());
        wire.flush(); return bytes.toByteArray();
    }

    @Test void startAndCancelRequestsDecodeTheNativeTags() throws Exception {
        byte[] nativeRequest = scalars(Map.of(7, 1L, 8, 100L, 11, 200L, 12, 2L));
        var request = PlayerStartMatchReq.parseFrom(nativeRequest);
        assertEquals(25847, PacketOpcodes.PlayerStartMatchReq);
        assertEquals(1, request.getMpPlayId());
        assertEquals(100, request.getMatchId());
        assertEquals(200, request.getDungeonId());
        assertEquals(2, request.getMatchTypeValue());
        assertEquals(UnknownFieldSet.parseFrom(nativeRequest), UnknownFieldSet.parseFrom(request.toByteArray()));
        assertEquals(7618, PacketOpcodes.PlayerCancelMatchReq);
        assertEquals(2, PlayerCancelMatchReq.parseFrom(scalars(Map.of(15, 2L))).getMatchTypeValue());
    }

    @Test void repeatedMatchParametersAcceptPackedAndUnpackedNativeEncodings() throws Exception {
        for (boolean packed : List.of(false, true)) {
            var bytes = new ByteArrayOutputStream();
            var wire = CodedOutputStream.newInstance(bytes);
            if (packed) { wire.writeTag(2, 2); wire.writeUInt32NoTag(3); wire.writeUInt32NoTag(1); wire.writeUInt32NoTag(300); }
            else { wire.writeUInt32(2, 1); wire.writeUInt32(2, 300); }
            wire.flush();
            assertEquals(List.of(1, 300), PlayerStartMatchReq.parseFrom(bytes.toByteArray()).getMatchParamListList());
        }
    }

    @Test void ownWorldAndGuestConfirmationsUseDifferentPacketIdsAndTags() throws Exception {
        assertEquals(3359, PacketOpcodes.PlayerConfirmMatchReq);
        var owner = PlayerConfirmMatchReq.parseFrom(scalars(Map.of(2, 2L, 12, 1L)));
        assertEquals(2, owner.getMatchTypeValue()); assertTrue(owner.getIsAgreed());
        assertEquals(21621, PacketOpcodes.PlayerGuestConfirmMatchReq);
        var guest = PlayerGuestConfirmMatchReq.parseFrom(scalars(Map.of(11, 2L, 7, 1L)));
        assertEquals(2, guest.getMatchTypeValue()); assertTrue(guest.getIsAgreed());
        assertEquals(24608, PacketOpcodes.PlayerAllowEnterMpAfterAgreeMatchNotify);
        assertEquals(10002, PlayerAllowEnterMpAfterAgreeMatchNotify.parseFrom(scalars(Map.of(10, 10002L))).getTargetUid());
    }

    @Test void outgoingPacketsUseAllObservedReaderTags() throws Exception {
        var packets = List.of(PacketCrucibleMatch.start(2, 1, 100, 200, 1562), PacketCrucibleMatch.cancel(2, 1563),
                PacketCrucibleMatch.info(10001), PacketCrucibleMatch.success(10002, 1234567890),
                PacketCrucibleMatch.stop(10002, 3), PacketCrucibleMatch.agreed(10002),
                PacketCrucibleMatch.confirm(2, true, 1563, false), PacketCrucibleMatch.confirm(2, true, 1563, true));
        var ids = List.of(1006, 24288, 28008, 29718, 20667, 6439, 2735, 27953);
        var fields = List.of(Map.of(3, 1562L, 4, 2L, 8, 200L, 11, 100L, 15, 1L), Map.of(10, 2L, 12, 1563L),
                Map.of(2, 1L, 3, 2L, 6, 10001L), Map.of(1, 2L, 4, 1L, 6, 1234567890L, 10, 10002L),
                Map.of(1, 3L, 4, 2L, 6, 10002L), Map.of(10, 10002L, 15, 2L),
                Map.of(5, 2L, 9, 1L, 13, 1563L), Map.of(4, 1L, 7, 1563L, 11, 2L));
        for (int i = 0; i < packets.size(); i++) {
            assertEquals(ids.get(i), packets.get(i).getOpcode());
            assertEquals(UnknownFieldSet.parseFrom(scalars(fields.get(i))), UnknownFieldSet.parseFrom(packets.get(i).getData()));
        }
    }

    @Test void futureMatchingPayloadsRemainUnknownInsteadOfBeingAssignedHistoricalTags() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        wire.writeUInt32(7, 1); wire.writeUInt32(12, 2);
        // The 7.1 request has an additional nested message at tag 0x370a (1761).
        wire.writeByteArray(1761, new byte[]{8, 42}); wire.flush();
        var request = PlayerStartMatchReq.parseFrom(bytes.toByteArray());
        assertTrue(request.getUnknownFields().hasField(1761));
        assertEquals(UnknownFieldSet.parseFrom(bytes.toByteArray()), UnknownFieldSet.parseFrom(request.toByteArray()));
    }

    @Test void matchingLimitsComeFromTheActualResourceShape() {
        var data = new Gson().fromJson("""
                {"id":1,"minPlayers":2,"maxPlayers":4,"isAutoMatch":true,"playType":"MP_PLAY_CRUCIBLE"}
                """, MpPlayMatchData.class);
        assertEquals(1, data.getId()); assertEquals(2, data.getMinPlayers()); assertEquals(4, data.getMaxPlayers());
        assertTrue(data.isAutoMatch()); assertEquals("MP_PLAY_CRUCIBLE", data.getPlayType());
    }
}
