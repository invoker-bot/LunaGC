package emu.grasscutter.server.packet.send;

import com.google.protobuf.GeneratedMessageV3;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MatchTypeOuterClass.MatchType;
import emu.grasscutter.net.proto.PlayerStartMatchRspOuterClass.PlayerStartMatchRsp;
import emu.grasscutter.net.proto.PlayerCancelMatchRspOuterClass.PlayerCancelMatchRsp;
import emu.grasscutter.net.proto.PlayerMatchInfoNotifyOuterClass.PlayerMatchInfoNotify;
import emu.grasscutter.net.proto.PlayerMatchSuccNotifyOuterClass.PlayerMatchSuccNotify;
import emu.grasscutter.net.proto.PlayerMatchStopNotifyOuterClass.PlayerMatchStopNotify;
import emu.grasscutter.net.proto.PlayerMatchAgreedResultNotifyOuterClass.PlayerMatchAgreedResultNotify;
import emu.grasscutter.net.proto.PlayerConfirmMatchRspOuterClass.PlayerConfirmMatchRsp;
import emu.grasscutter.net.proto.PlayerGuestConfirmMatchRspOuterClass.PlayerGuestConfirmMatchRsp;

public final class PacketCrucibleMatch extends BasePacket {
    private static final MatchType TYPE = MatchType.MatchType_MP_PLAY;
    private PacketCrucibleMatch(int opcode, GeneratedMessageV3 message) { super(opcode); setData(message); }

    public static PacketCrucibleMatch start(int type, int playId, int matchId, int dungeonId, int retcode) {
        return new PacketCrucibleMatch(PacketOpcodes.PlayerStartMatchRsp, PlayerStartMatchRsp.newBuilder()
                .setMatchTypeValue(type).setMpPlayId(playId).setMatchId(matchId).setDungeonId(dungeonId).setRetcode(retcode).build());
    }
    public static PacketCrucibleMatch cancel(int type, int retcode) {
        return new PacketCrucibleMatch(PacketOpcodes.PlayerCancelMatchRsp, PlayerCancelMatchRsp.newBuilder()
                .setMatchTypeValue(type).setRetcode(retcode).build());
    }
    public static PacketCrucibleMatch info(int ownerUid) {
        return new PacketCrucibleMatch(PacketOpcodes.PlayerMatchInfoNotify, PlayerMatchInfoNotify.newBuilder()
                .setMatchType(TYPE).setMpPlayId(1).setHostUid(ownerUid).build());
    }
    public static PacketCrucibleMatch success(int hostUid, int deadline) {
        return new PacketCrucibleMatch(PacketOpcodes.PlayerMatchSuccNotify, PlayerMatchSuccNotify.newBuilder()
                .setMatchType(TYPE).setMpPlayId(1).setHostUid(hostUid).setConfirmEndTime(deadline).build());
    }
    public static PacketCrucibleMatch stop(int hostUid, int reason) {
        return new PacketCrucibleMatch(PacketOpcodes.PlayerMatchStopNotify, PlayerMatchStopNotify.newBuilder()
                .setMatchType(TYPE).setHostUid(hostUid).setReason(reason).build());
    }
    public static PacketCrucibleMatch agreed(int hostUid) {
        return new PacketCrucibleMatch(PacketOpcodes.PlayerMatchAgreedResultNotify, PlayerMatchAgreedResultNotify.newBuilder()
                .setMatchType(TYPE).setTargetUid(hostUid).build());
    }
    public static PacketCrucibleMatch confirm(int type, boolean agree, int retcode, boolean guest) {
        if (guest) return new PacketCrucibleMatch(PacketOpcodes.PlayerGuestConfirmMatchRsp,
                PlayerGuestConfirmMatchRsp.newBuilder().setMatchTypeValue(type).setIsAgreed(agree).setRetcode(retcode).build());
        return new PacketCrucibleMatch(PacketOpcodes.PlayerConfirmMatchRsp,
                PlayerConfirmMatchRsp.newBuilder().setMatchTypeValue(type).setIsAgreed(agree).setRetcode(retcode).build());
    }
}
