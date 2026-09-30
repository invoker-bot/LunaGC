package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.GadgetInteractRspOuterClass.GadgetInteractRsp;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.MpPlayRewardInfoOuterClass.MpPlayRewardInfo;
import emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.net.proto.MpPlayOwnerInviteNotifyOuterClass.MpPlayOwnerInviteNotify;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;
import emu.grasscutter.server.packet.send.PacketMpPlay;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Independent tags from the local 7.1 native writers/readers; no gameplay claim is made here. */
class CrucibleRewardProtocolTest {
    @Test void nativeRewardResponseKeepsPreviewAndFinishSeparateAndInvitationWarnsAboutRemainingRewards() throws Exception {
        var preview = new PacketGadgetInteractRsp(0x40001001, 70330039,
                InteractType.InteractType_INTERACT_MP_PLAY_REWARD, InterOpType.InterOpType_INTER_OP_START, 660);
        var previewFields = UnknownFieldSet.parseFrom(preview.getData());
        assertEquals(List.of(1L), previewFields.getField(10).getVarintList());
        assertEquals(List.of(6L), previewFields.getField(11).getVarintList());
        assertEquals(List.of(660L), previewFields.getField(13).getVarintList());
        assertEquals(List.of(0x40001001L), previewFields.getField(15).getVarintList());
        var finish = GadgetInteractRsp.parseFrom(new PacketGadgetInteractRsp(0x40001001, 70330039,
                InteractType.InteractType_INTERACT_MP_PLAY_REWARD, InterOpType.InterOpType_INTER_OP_FINISH, 0).getData());
        assertEquals(0, finish.getRetcode());
        assertEquals(InterOpType.InterOpType_INTER_OP_FINISH, finish.getOpType());
        var invite = PacketMpPlay.ownerInvite(1, 30, true);
        assertTrue(MpPlayOwnerInviteNotify.parseFrom(invite.getData()).getIsRemainReward());
        assertEquals(List.of(1L), UnknownFieldSet.parseFrom(invite.getData()).getField(6).getVarintList());
        assertFalse(MpPlayOwnerInviteNotify.parseFrom(PacketMpPlay.ownerInvite(1, 30).getData()).getIsRemainReward());
        var ownerWarning = UnknownFieldSet.parseFrom(PacketMpPlay.ownerCheck(1, true, 1220, 10001).getData());
        assertEquals(List.of(10001L), ownerWarning.getField(1).getVarintList());
        assertEquals(List.of(1220L), ownerWarning.getField(2).getVarintList());
    }
    @Test void interactionRequestRecognizesAllNativeFieldsWithoutInventedPlaceholderTags() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        for (int tag : List.of(1, 3, 5, 6, 13, 15)) wire.writeBool(tag, true);
        wire.writeUInt32(4, 70900001);
        wire.writeEnum(7, 1);
        wire.writeEnum(9, 1);
        wire.writeUInt32(10, 300);
        wire.writeUInt32(11, 0x40001001);
        wire.writeUInt32(12, 10002);
        wire.writeUInt32(14, 5001);
        wire.flush();
        var request = GadgetInteractReq.parseFrom(bytes.toByteArray());
        assertEquals(5765, PacketOpcodes.GadgetInteractReq);
        assertEquals(70900001, request.getGadgetId());
        assertEquals(0x40001001, request.getGadgetEntityId());
        assertEquals(ResinCostType.ResinCostType_NORMAL, request.getResinCostType());
        assertEquals(InterOpType.InterOpType_INTER_OP_START, request.getOpType());
        assertEquals(UnknownFieldSet.getDefaultInstance(), request.getUnknownFields(),
                "All thirteen native scalar fields must be recognized");
        for (int tag : List.of(1, 3, 5, 6, 13, 15)) {
            var field = request.getDescriptorForType().findFieldByNumber(tag);
            assertNotNull(field);
            assertEquals(JavaType.BOOLEAN, field.getJavaType());
            assertEquals(true, request.getField(field));
        }
        assertEquals(13, request.getDescriptorForType().getFields().size());
        assertEquals(UnknownFieldSet.parseFrom(bytes.toByteArray()),
                UnknownFieldSet.parseFrom(request.toByteArray()));
        var future = UnknownFieldSet.newBuilder().addField(100,
                UnknownFieldSet.Field.newBuilder().addVarint(7).build()).build();
        var withUnknown = request.toBuilder().mergeUnknownFields(future).build();
        assertEquals(future, GadgetInteractReq.parseFrom(withUnknown.toByteArray()).getUnknownFields());
    }

    @Test void interactionResponseReadsTheNativeRewardTypeAndSignedError() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        wire.writeUInt32(3, 70900001);
        wire.writeUInt32(5, 300);
        wire.writeEnum(10, 1);
        wire.writeEnum(11, 6);
        wire.writeInt32(13, -1);
        wire.writeUInt32(15, 0x40001001);
        wire.flush();
        var response = GadgetInteractRsp.parseFrom(bytes.toByteArray());
        assertEquals(881, PacketOpcodes.GadgetInteractRsp);
        assertEquals(70900001, response.getGadgetId());
        assertEquals(0x40001001, response.getGadgetEntityId());
        assertEquals(-1, response.getRetcode());
        assertEquals(InterOpType.InterOpType_INTER_OP_START, response.getOpType());
        assertEquals(InteractType.InteractType_INTERACT_MP_PLAY_REWARD, response.getInteractType());
        assertEquals(UnknownFieldSet.getDefaultInstance(), response.getUnknownFields());
        assertEquals(UnknownFieldSet.parseFrom(bytes.toByteArray()),
                UnknownFieldSet.parseFrom(response.toByteArray()));
    }

    @Test void rewardInfoAcceptsBothNativeListEncodingsInsideSceneContent42() throws Exception {
        var reward = MpPlayRewardInfo.newBuilder().setResin(40)
                .addAllRemainUidList(List.of(10001, 10002)).addAllQualifyUidList(List.of(10001, 10002, 10003)).build();
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        wire.writeUInt32(1, 40);
        wire.writeUInt32(2, 10001); wire.writeUInt32(2, 10002);
        wire.writeUInt32(3, 10001); wire.writeUInt32(3, 10002); wire.writeUInt32(3, 10003);
        wire.flush();
        assertEquals(reward, MpPlayRewardInfo.parseFrom(bytes.toByteArray()));
        assertEquals(List.of(2, 3), UnknownFieldSet.parseFrom(reward.toByteArray()).asMap().entrySet().stream()
                .filter(field -> !field.getValue().getLengthDelimitedList().isEmpty()).map(field -> field.getKey()).toList());
        var sceneBytes = new ByteArrayOutputStream();
        var sceneWire = CodedOutputStream.newInstance(sceneBytes);
        sceneWire.writeUInt32(1, 70900001);
        sceneWire.writeByteArray(42, bytes.toByteArray());
        sceneWire.flush();
        var scene = SceneGadgetInfo.parseFrom(sceneBytes.toByteArray());
        assertEquals(SceneGadgetInfo.ContentCase.MP_PLAY_REWARD, scene.getContentCase());
        assertEquals(reward, scene.getMpPlayReward());
        assertEquals(UnknownFieldSet.getDefaultInstance(), scene.getUnknownFields());
        assertEquals(42, SceneGadgetInfo.getDescriptor().findFieldByName("mp_play_reward").getNumber());
    }
}
