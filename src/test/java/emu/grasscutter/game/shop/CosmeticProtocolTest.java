package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;
import org.junit.jupiter.api.Test;

class CosmeticProtocolTest {
    @Test
    void weaponChangeUsesVerifiedNativeRequestTags() throws Exception {
        // OKMBNFFPBBD serializer 0x15182c620 and codec initializer 0x15182c9e0.
        var request =
                AvatarChangeWeaponSkinReqOuterClass.AvatarChangeWeaponSkinReq.parseFrom(
                        new byte[] {8, 1, 0x32, 2, 1, 2});
        assertEquals(1, request.getWeaponSkinId());
        assertEquals(java.util.List.of(1L, 2L), request.getAvatarGuidListList());
        assertEquals(24702, PacketOpcodes.AvatarChangeWeaponSkinReq);
        assertEquals(575, PacketOpcodes.AvatarChangeWeaponSkinRsp);
    }

    @Test
    void ownershipUsesNativeIdsAndDetailedAppearanceList() throws Exception {
        // NEHBIICNOHP parser 0x153c2da50; FGGMBMEIIAE serializer 0x15287e6d0.
        var data =
                AvatarWeaponSkinDataNotifyOuterClass.AvatarWeaponSkinDataNotify.parseFrom(
                        new byte[] {10, 4, 8, 9, 0x68, 7, 0x12, 1, 7});
        assertEquals(9, data.getWeaponSkinList(0).getExpireTime());
        assertEquals(7, data.getWeaponSkinList(0).getWeaponSkinId());
        assertEquals(java.util.List.of(7), data.getWeaponSkinIdListList());
    }

    @Test
    void traceChangeNotifyContainsSceneEntityAtFieldOne() throws Exception {
        assertEquals(
                1,
                AvatarTraceEffectChangeNotifyOuterClass.AvatarTraceEffectChangeNotify.getDescriptor()
                        .findFieldByName("entity_info")
                        .getNumber());
        assertEquals(9539, PacketOpcodes.AvatarChangeTraceEffectNotify);
        assertEquals(
                14,
                AvatarGainTraceEffectNotifyOuterClass.AvatarGainTraceEffectNotify.getDescriptor()
                        .findFieldByName("_trace_effect_id")
                        .getNumber());
    }
}
