package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetAllMailRspOuterClass.GetAllMailRsp;

public class PacketGetAllMailRsp extends BasePacket {
    public PacketGetAllMailRsp(Player player, boolean collected) {
        super(PacketOpcodes.GetAllMailRsp);
        setData(
                GetAllMailRsp.newBuilder()
                        .setIsCollected(collected)
                        .setIsTruncated(false)
                        .addAllMailList(
                                player.getMailHandler().getMail(collected).stream()
                                        .map(m -> m.toProto(player))
                                        .toList())
                        .build());
    }
}
