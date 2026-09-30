package emu.grasscutter.server.packet.send;

import com.google.protobuf.GeneratedMessageV3;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MpPlayOwnerCheckRspOuterClass.MpPlayOwnerCheckRsp;
import emu.grasscutter.net.proto.MpPlayOwnerStartInviteRspOuterClass.MpPlayOwnerStartInviteRsp;
import emu.grasscutter.net.proto.MpPlayOwnerInviteNotifyOuterClass.MpPlayOwnerInviteNotify;
import emu.grasscutter.net.proto.MpPlayGuestReplyInviteRspOuterClass.MpPlayGuestReplyInviteRsp;
import emu.grasscutter.net.proto.MpPlayGuestReplyNotifyOuterClass.MpPlayGuestReplyNotify;
import emu.grasscutter.net.proto.MpPlayInviteResultNotifyOuterClass.MpPlayInviteResultNotify;
import emu.grasscutter.net.proto.MpPlayPrepareInterruptNotifyOuterClass.MpPlayPrepareInterruptNotify;

public final class PacketMpPlay extends BasePacket {
    private PacketMpPlay(int opcode, GeneratedMessageV3 message) { super(opcode); setData(message); }

    public static PacketMpPlay ownerCheck(int id, boolean skipMatch, int retcode, int wrongUid) {
        return new PacketMpPlay(PacketOpcodes.MpPlayOwnerCheckRsp, MpPlayOwnerCheckRsp.newBuilder()
                .setMpPlayId(id).setIsSkipMatch(skipMatch).setRetcode(retcode).setWrongUid(wrongUid).build());
    }
    public static PacketMpPlay startInvite(int id, boolean skipMatch, int retcode) {
        return new PacketMpPlay(PacketOpcodes.MpPlayOwnerStartInviteRsp, MpPlayOwnerStartInviteRsp.newBuilder()
                .setMpPlayId(id).setIsSkipMatch(skipMatch).setRetcode(retcode).build());
    }
    public static PacketMpPlay ownerInvite(int id, int seconds) {
        return new PacketMpPlay(PacketOpcodes.MpPlayOwnerInviteNotify, MpPlayOwnerInviteNotify.newBuilder()
                .setMpPlayId(id).setCd(seconds).build());
    }
    public static PacketMpPlay guestReplyResponse(int id, int retcode) {
        return new PacketMpPlay(PacketOpcodes.MpPlayGuestReplyInviteRsp, MpPlayGuestReplyInviteRsp.newBuilder()
                .setMpPlayId(id).setRetcode(retcode).build());
    }
    public static PacketMpPlay guestReply(int id, int uid, boolean agree) {
        return new PacketMpPlay(PacketOpcodes.MpPlayGuestReplyNotify, MpPlayGuestReplyNotify.newBuilder()
                .setMpPlayId(id).setUid(uid).setIsAgree(agree).build());
    }
    public static PacketMpPlay inviteResult(int id, boolean allAgree) {
        return new PacketMpPlay(PacketOpcodes.MpPlayInviteResultNotify, MpPlayInviteResultNotify.newBuilder()
                .setMpPlayId(id).setAllAgree(allAgree).build());
    }
    public static PacketMpPlay interrupt(int id) {
        return new PacketMpPlay(PacketOpcodes.MpPlayPrepareInterruptNotify, MpPlayPrepareInterruptNotify.newBuilder()
                .setMpPlayId(id).build());
    }
}
