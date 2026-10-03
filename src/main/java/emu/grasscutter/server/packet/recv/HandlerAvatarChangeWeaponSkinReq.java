package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarChangeWeaponSkinReqOuterClass.AvatarChangeWeaponSkinReq;
import emu.grasscutter.net.proto.AvatarChangeWeaponSkinRspOuterClass.AvatarChangeWeaponSkinRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.AvatarChangeWeaponSkinReq)
public final class HandlerAvatarChangeWeaponSkinReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = AvatarChangeWeaponSkinReq.parseFrom(payload);
        boolean success =
                session
                        .getPlayer()
                        .getAvatars()
                        .changeWeaponSkin(req.getAvatarGuidListList(), req.getWeaponSkinId());
        var data = AvatarChangeWeaponSkinRsp.newBuilder().setWeaponSkinId(req.getWeaponSkinId());
        if (success) data.addAllAvatarGuidList(req.getAvatarGuidListList());
        else data.setRetcode(1).addAllFailedAvatarGuidList(req.getAvatarGuidListList());
        var packet = new BasePacket(PacketOpcodes.AvatarChangeWeaponSkinRsp);
        packet.setData(data.build());
        session.send(packet);
    }
}
