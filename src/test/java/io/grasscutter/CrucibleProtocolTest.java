package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.game.activity.crucible.GadgetPlayState;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ExecuteGadgetLuaReqOuterClass.ExecuteGadgetLuaReq;
import emu.grasscutter.net.proto.GadgetPlayStartNotifyOuterClass.GadgetPlayStartNotify;
import emu.grasscutter.net.proto.GadgetPlayDataNotifyOuterClass.GadgetPlayDataNotify;
import emu.grasscutter.net.proto.GadgetPlayStopNotifyOuterClass.GadgetPlayStopNotify;
import emu.grasscutter.net.proto.GadgetPlayUidOpNotifyOuterClass.GadgetPlayUidOpNotify;
import emu.grasscutter.net.proto.GadgetPlayUidInfoOuterClass.GadgetPlayUidInfo;
import emu.grasscutter.net.proto.MpPlayPrepareNotifyOuterClass.MpPlayPrepareNotify;
import emu.grasscutter.net.proto.MpPlayOwnerCheckReqOuterClass.MpPlayOwnerCheckReq;
import emu.grasscutter.net.proto.MpPlayOwnerStartInviteReqOuterClass.MpPlayOwnerStartInviteReq;
import emu.grasscutter.net.proto.MpPlayGuestReplyInviteReqOuterClass.MpPlayGuestReplyInviteReq;
import emu.grasscutter.net.proto.MpPlayOwnerInviteNotifyOuterClass.MpPlayOwnerInviteNotify;
import emu.grasscutter.scripts.data.SceneGadgetCrucibleConfig;
import emu.grasscutter.server.packet.send.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tags independently observed in the supplied client's native protobuf readers. */
class CrucibleProtocolTest {
    @Test void invitationRequestsDecodeTheThreeNativeWriters() throws Exception {
        var checkBytes = nativeScalars(Map.of(4, 1L, 14, 1L));
        var check = MpPlayOwnerCheckReq.parseFrom(checkBytes);
        assertEquals(6899, PacketOpcodes.MpPlayOwnerCheckReq);
        assertTrue(check.getIsSkipMatch());
        assertEquals(1, check.getMpPlayId());
        assertEquals(UnknownFieldSet.parseFrom(checkBytes), UnknownFieldSet.parseFrom(check.toByteArray()));
        var startBytes = nativeScalars(Map.of(1, 1L, 3, 1L));
        var start = MpPlayOwnerStartInviteReq.parseFrom(startBytes);
        assertEquals(28718, PacketOpcodes.MpPlayOwnerStartInviteReq);
        assertTrue(start.getIsSkipMatch());
        assertEquals(1, start.getMpPlayId());
        assertEquals(UnknownFieldSet.parseFrom(startBytes), UnknownFieldSet.parseFrom(start.toByteArray()));
        var guestBytes = nativeScalars(Map.of(3, 1L, 14, 1L));
        var guest = MpPlayGuestReplyInviteReq.parseFrom(guestBytes);
        assertEquals(5376, PacketOpcodes.MpPlayGuestReplyInviteReq);
        assertTrue(guest.getIsAgree());
        assertEquals(1, guest.getMpPlayId());
        assertEquals(UnknownFieldSet.parseFrom(guestBytes), UnknownFieldSet.parseFrom(guest.toByteArray()));
    }

    @Test void invitationResponsesAndNotificationsMatchAllNativeReaderTags() throws Exception {
        var packets = List.of(PacketMpPlay.ownerCheck(1, true, 1212, 10002),
                PacketMpPlay.startInvite(1, true, 1219), PacketMpPlay.ownerInvite(1, 30),
                PacketMpPlay.guestReplyResponse(1, 1225), PacketMpPlay.guestReply(1, 10002, true),
                PacketMpPlay.inviteResult(1, true), PacketMpPlay.interrupt(1));
        var opcodes = List.of(8829, 8056, 25124, 2563, 21009, 22704, 7851);
        var nativeFields = List.of(Map.of(1, 10002L, 2, 1212L, 3, 1L, 11, 1L),
                Map.of(7, 1L, 8, 1L, 11, 1219L), Map.of(8, 1L, 15, 30L),
                Map.of(8, 1225L, 11, 1L), Map.of(5, 1L, 8, 1L, 10, 10002L),
                Map.of(4, 1L, 7, 1L), Map.of(9, 1L));
        for (int i = 0; i < packets.size(); i++) {
            assertEquals(opcodes.get(i), packets.get(i).getOpcode());
            assertEquals(UnknownFieldSet.parseFrom(nativeScalars(nativeFields.get(i))),
                    UnknownFieldSet.parseFrom(packets.get(i).getData()));
        }
        var decline = UnknownFieldSet.parseFrom(PacketMpPlay.inviteResult(1, false).getData());
        assertFalse(decline.hasField(4));
        assertEquals(1, decline.getField(7).getVarintList().get(0));
        assertEquals(1, rawField(MpPlayOwnerInviteNotify.newBuilder().setIsRemainReward(true).build().toByteArray(), 6));
    }

    private static byte[] nativeScalars(Map<Integer, Long> fields) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        for (var field : fields.entrySet()) wire.writeUInt64(field.getKey(), field.getValue());
        wire.flush();
        return bytes.toByteArray();
    }

    @Test void clientSubmissionUsesTheNativeWriterTagsAndSignedParameters() throws Exception {
        assertEquals(26835, PacketOpcodes.ExecuteGadgetLuaReq);
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        wire.writeUInt32(8, 0x40001001);
        wire.writeInt32(14, -1);
        wire.writeInt32(9, 1);
        wire.writeInt32(6, 0x90001001);
        wire.flush();
        var request = ExecuteGadgetLuaReq.parseFrom(bytes.toByteArray());
        assertEquals(0x40001001, request.getSourceEntityId());
        assertEquals(-1, request.getParam1());
        assertEquals(1, request.getParam2());
        assertEquals(0x90001001, request.getParam3());
        assertEquals(UnknownFieldSet.parseFrom(bytes.toByteArray()),
                UnknownFieldSet.parseFrom(request.toByteArray()));
    }

    @Test void clientSubmissionRequiresItsOwnTeamAndAnActiveRoundMember() {
        var state = new GadgetPlayState();
        var member = new GadgetPlayState.Round(5001005, 42, 8, Map.of(10001, 8));
        assertFalse(state.acceptsClientSubmission(10001, 0, 1, 90001, 90001, 1000));
        state.start(config(), 1000, member);
        assertFalse(state.acceptsClientSubmission(10001, 0, 1, 90001, 90001, 1002));
        assertTrue(state.acceptsClientSubmission(10001, 0, 1, 90001, 90001, 1003));
        assertFalse(state.acceptsClientSubmission(10002, 0, 1, 90002, 90002, 1003));
        assertFalse(state.acceptsClientSubmission(10001, 0, 1, 90002, 90001, 1003));
        assertFalse(state.acceptsClientSubmission(10001, 5001, 1, 90001, 90001, 1003),
                "Player requests must not invoke server-only random selection");
        assertFalse(state.acceptsClientSubmission(10001, 0, 9, 90001, 90001, 1003));
        assertFalse(state.acceptsClientSubmission(10001, 0, 1, 0, 0, 1003));
        assertFalse(state.acceptsClientSubmission(10001, 0, 1, 90001, 90001, 1903));
        state.stop(1004);
        assertFalse(state.acceptsClientSubmission(10001, 0, 1, 90001, 90001, 1004));
        state.start(config(), 2000, new GadgetPlayState.Round(5001006, 43, 8, Map.of(10002, 8)));
        assertFalse(state.acceptsClientSubmission(10001, 0, 1, 90001, 90001, 2003));
        assertTrue(state.acceptsClientSubmission(10002, 0, 1, 90002, 90002, 2003));
    }

    @Test void verifiedClientSchemasExistWithTheObservedTags() throws Exception {
        var schemas = Map.of(
                "GadgetPlayStartNotify", Map.of("entity_id", 3, "play_type", 5, "start_time", 15),
                "GadgetPlayDataNotify", Map.of("entity_id", 6, "play_type", 9, "progress", 10),
                "GadgetPlayStopNotify", Map.of("cost_time", 1, "uid_info_list", 2, "entity_id", 4,
                        "is_success", 10, "score", 11, "play_type", 12),
                "GadgetPlayUidOpNotify", Map.of("op_name", 1, "uid_list", 5, "entity_id", 7,
                        "param_list", 11, "play_type", 13, "op", 15),
                "MpPlayPrepareNotify", Map.of("mp_play_id", 1, "prepare_end_time", 11),
                "GadgetPlayUidInfo", Map.of("icon", 2, "uid", 3, "profile_picture", 5,
                        "score", 7, "op", 9, "online_id", 10, "nickname", 11));
        for (var schema : schemas.entrySet()) {
            var file = Path.of("src/main/proto", schema.getKey() + ".proto");
            assertTrue(Files.exists(file), "Missing verified schema: " + schema.getKey());
            var text = Files.readString(file);
            for (var field : schema.getValue().entrySet())
                assertTrue(text.matches("(?s).*\\b" + field.getKey() + "\\s*=\\s*" + field.getValue() + "\\s*;.*"),
                        schema.getKey() + "." + field.getKey() + " must match the native reader");
        }
    }

    @Test void notificationsUseTheClientPacketIdGettersAndScalarTags() throws Exception {
        var start = new PacketGadgetPlayStartNotify(1, 1000);
        assertEquals(29148, start.getOpcode());
        assertEquals(1, rawField(start.getData(), 3));
        assertEquals(1, rawField(start.getData(), 5));
        assertEquals(1000, rawField(start.getData(), 15));
        assertEquals(1000, GadgetPlayStartNotify.parseFrom(start.getData()).getStartTime());

        var data = new PacketGadgetPlayDataNotify(1, 35000);
        assertEquals(20094, data.getOpcode());
        assertEquals(1, rawField(data.getData(), 6));
        assertEquals(1, rawField(data.getData(), 9));
        assertEquals(35000, rawField(data.getData(), 10));
        assertEquals(35000, GadgetPlayDataNotify.parseFrom(data.getData()).getProgress());

        var prepare = new PacketMpPlayPrepareNotify(1, 1020);
        assertEquals(21654, prepare.getOpcode());
        assertEquals(1, rawField(prepare.getData(), 1));
        assertEquals(1020, rawField(prepare.getData(), 11));
        assertEquals(1020, MpPlayPrepareNotify.parseFrom(prepare.getData()).getPrepareEndTime());
    }

    @Test void playerOperationsAcceptBothNativeRepeatedEncodings() throws Exception {
        var packet = new PacketGadgetPlayUidOpNotify(1, List.of(10001, 10002), 2,
                "random_user", List.of(2, 1000, 60));
        assertEquals(28806, packet.getOpcode());
        var packed = GadgetPlayUidOpNotify.parseFrom(packet.getData());
        assertEquals(List.of(10001, 10002), packed.getUidListList());
        assertEquals(List.of(2, 1000, 60), packed.getParamListList());
        assertEquals(2, rawField(packet.getData(), 15));
        assertEquals(1, rawField(packet.getData(), 7));
        assertEquals(1, rawField(packet.getData(), 13));
        assertEquals("random_user", UnknownFieldSet.parseFrom(packet.getData())
                .getField(1).getLengthDelimitedList().get(0).toStringUtf8());
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        wire.writeString(1, "random_user");
        wire.writeUInt32(5, 10001); wire.writeUInt32(5, 10002);
        wire.writeUInt32(7, 1);
        wire.writeUInt32(11, 2); wire.writeUInt32(11, 1000); wire.writeUInt32(11, 60);
        wire.writeUInt32(13, 1); wire.writeUInt32(15, 2);
        wire.flush();
        assertEquals(packed, GadgetPlayUidOpNotify.parseFrom(bytes.toByteArray()));
    }

    @Test void queuedProgressAndSettlementKeepTheirOwnRoundSnapshot() throws Exception {
        var state = new GadgetPlayState();
        var config = config();
        state.start(config, 1000, new GadgetPlayState.Round(5001005, 42, 8, Map.of(10001, 8)));
        state.tick(1003);
        state.setRoundUidValue(10001, "Fire", 300, 1004);
        var first = state.addProgress(300, 1004).stream()
                .filter(change -> change.type() == GadgetPlayState.ChangeType.PROGRESS_CHANGED).findFirst().orElseThrow();
        state.setRoundUidValue(10001, "Fire", 1000, 1005);
        state.addProgress(700, 1005);
        var cancelled = state.stop(1006).get(0);
        assertEquals(3, cancelled.costTime());
        assertEquals(1000, cancelled.progress());
        assertEquals(Map.of(10001, 1000), cancelled.totalScores());
        assertThrows(UnsupportedOperationException.class, () -> cancelled.totalScores().put(10002, 1));
        state.start(config, 1100);
        assertEquals(300, first.progress());
        assertEquals(Map.of(10001, 300), first.totalScores());
        assertEquals(5001005, cancelled.round().scheduleId());

        var member = GadgetPlayUidInfo.newBuilder().setUid(10001).setNickname("Traveller").setScore(1000).build();
        var stop = new PacketGadgetPlayStopNotify(1, cancelled, List.of(member));
        assertEquals(25650, stop.getOpcode());
        assertEquals(3, rawField(stop.getData(), 1));
        assertEquals(1, rawField(stop.getData(), 4));
        assertEquals(1000, rawField(stop.getData(), 11));
        assertEquals(1, rawField(stop.getData(), 12));
        var settlement = GadgetPlayStopNotify.parseFrom(stop.getData());
        assertFalse(settlement.getIsSuccess());
        assertEquals(List.of(member), settlement.getUidInfoListList());
        var nested = UnknownFieldSet.parseFrom(member.toByteArray());
        assertEquals(10001, nested.getField(3).getVarintList().get(0));
        assertEquals(1000, nested.getField(7).getVarintList().get(0));
        assertEquals("Traveller", nested.getField(11).getLengthDelimitedList().get(0).toStringUtf8());
    }

    @Test void cancellationBeforeCountdownAndDelayedTimeoutReportBoundedTimes() {
        var state = new GadgetPlayState();
        state.start(config(), 1000);
        assertEquals(0, state.stop(1001).get(0).costTime());
        state.start(config(), 1100);
        var timeout = state.tick(3000).stream()
                .filter(change -> change.type() == GadgetPlayState.ChangeType.TIMED_OUT).findFirst().orElseThrow();
        assertEquals(900, timeout.costTime());
        assertEquals(0, timeout.remainingTime());
        assertTrue(state.tick(3001).isEmpty());
    }

    private static long rawField(byte[] data, int number) throws Exception {
        return UnknownFieldSet.parseFrom(data).getField(number).getVarintList().get(0);
    }

    private static SceneGadgetCrucibleConfig config() {
        var config = new SceneGadgetCrucibleConfig();
        config.duration = 900; config.start_cd = 3; config.mp_play_id = 1;
        config.progress_stage = List.of(0, 5000, 20000, 35000);
        return config;
    }
}
