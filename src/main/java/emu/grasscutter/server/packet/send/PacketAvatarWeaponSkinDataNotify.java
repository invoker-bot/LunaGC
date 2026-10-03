package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarWeaponSkinDataNotifyOuterClass.AvatarWeaponSkinDataNotify;
import emu.grasscutter.net.proto.AvatarWeaponSkinOuterClass.AvatarWeaponSkin;

public final class PacketAvatarWeaponSkinDataNotify extends BasePacket {
    public PacketAvatarWeaponSkinDataNotify(Player player) {
        super(PacketOpcodes.AvatarWeaponSkinDataNotify);
        var data = AvatarWeaponSkinDataNotify.newBuilder();
        player.getWeaponSkinList().stream()
                .sorted()
                .forEach(
                        id -> {
                            data.addWeaponSkinIdList(id);
                            var skin = AvatarWeaponSkin.newBuilder().setWeaponSkinId(id);
                            player
                                    .getAvatars()
                                    .forEach(
                                            avatar -> {
                                                if (avatar.getWeaponSkin() == id) skin.addAvatarGuidList(avatar.getGuid());
                                            });
                            data.addWeaponSkinList(skin);
                        });
        setData(data.build());
    }
}
