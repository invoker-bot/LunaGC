package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.game.dailytask.DailyTaskProto;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityMetaUpdateMoonOvergrowValueOuterClass.AbilityMetaUpdateMoonOvergrowValue;
import emu.grasscutter.net.proto.DoGachaRspOuterClass.DoGachaRsp;
import emu.grasscutter.net.proto.DailyTaskDataNotifyOuterClass.DailyTaskDataNotify;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.net.proto.GetPlayerTokenRspOuterClass.GetPlayerTokenRsp;
import emu.grasscutter.net.proto.PingReqOuterClass.PingReq;
import emu.grasscutter.net.proto.WorldOwnerDailyTaskNotifyOuterClass.WorldOwnerDailyTaskNotify;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the 7.1 wire numbers used by login, heartbeat, scene entry, and changed responses. */
public final class Protocol71CompatibilityTest {
    @Test
    public void loginAndSceneOpcodesUse71CmdIds() {
        assertEquals(23252, PacketOpcodes.GetPlayerTokenReq);
        assertEquals(3713, PacketOpcodes.GetPlayerTokenRsp);
        assertEquals(9282, PacketOpcodes.PlayerLoginReq);
        assertEquals(9582, PacketOpcodes.PlayerEnterSceneNotify);
        assertEquals(21608, PacketOpcodes.EnterSceneReadyReq);
        assertTrue(PacketOpcodes.AddCustomTeamReq < 0);
        assertTrue(PacketOpcodes.UnnamedOpcode2819 < 0);
    }

    @Test
    public void tokenRequestParses71WireFields() throws Exception {
        var output = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(output);
        wire.writeString(10, "10008");
        wire.writeString(8, "token-71");
        wire.writeUInt32(1811, 5);
        wire.writeString(651, "encrypted-client-seed");
        wire.writeString(1299, "OSRELWin7.1.0");
        wire.flush();

        var request = GetPlayerTokenReq.parseFrom(output.toByteArray());
        assertEquals("10008", request.getAccountUid());
        assertEquals("token-71", request.getAccountToken());
        assertEquals(5, request.getKeyId());
        assertEquals("encrypted-client-seed", request.getClientRandKey());
        assertEquals("OSRELWin7.1.0", request.getClientVersion());
    }

    @Test
    public void heartbeatParses71WireFields() throws Exception {
        var output = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(output);
        wire.writeUInt32(7, 1786577216);
        wire.writeUInt32(12, 60);
        wire.flush();

        var request = PingReq.parseFrom(output.toByteArray());
        assertEquals(1786577216, request.getClientTime());
        assertEquals(60, request.getSeq());
    }

    @Test
    public void changedResponsesSerialize71Fields() throws Exception {
        var token = GetPlayerTokenRsp.newBuilder().setClientVersion("OSRELWin7.1.0").build();
        assertTrue(UnknownFieldSet.parseFrom(token.toByteArray()).hasField(1856));
        assertFalse(UnknownFieldSet.parseFrom(token.toByteArray()).hasField(1840));

        var gacha = DoGachaRsp.newBuilder().setIsCapturingRadiance(true).build();
        assertTrue(UnknownFieldSet.parseFrom(gacha.toByteArray()).hasField(286));

        var overgrow = AbilityMetaUpdateMoonOvergrowValue.newBuilder().setValue(1.5f).build();
        assertTrue(UnknownFieldSet.parseFrom(overgrow.toByteArray()).hasField(6));
    }

    @Test
    public void dailyTaskNotifiesUse71Schema() throws Exception {
        var board = DailyTaskDataNotify.parseFrom(DailyTaskProto.dataNotify(3, 100, true));
        assertEquals(3, board.getFinishedNum());
        assertEquals(100, board.getScoreRewardId());
        assertTrue(board.getIsTakenScoreReward());

        var owner = WorldOwnerDailyTaskNotify.parseFrom(
                DailyTaskProto.worldOwnerNotify(List.of(), 2, 3));
        assertEquals(2, owner.getFilterCityId());
        assertEquals(3, owner.getFinishedDailyTaskNum());
    }
}
