package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetMailItemRspOuterClass.GetMailItemRsp;
import java.util.List;

public class PacketGetMailItemRsp extends BasePacket {
    public PacketGetMailItemRsp(Player player, List<Integer> clientMailIds) {
        super(PacketOpcodes.GetMailItemRsp);
        var result = player.getMailHandler().claim(clientMailIds);
        setData(
                GetMailItemRsp.newBuilder()
                        .setRetcode(result.retcode())
                        .addAllMailIdList(result.ids())
                        .addAllItemList(result.items())
                        .build());
    }
}
