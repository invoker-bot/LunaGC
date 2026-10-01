package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DelMailRspOuterClass.DelMailRsp;
import java.util.List;

public class PacketDelMailRsp extends BasePacket {

    public PacketDelMailRsp(Player player, List<Integer> toDeleteIds) {
        this(player, toDeleteIds, 0);
    }

    public PacketDelMailRsp(Player player, List<Integer> toDeleteIds, int retcode) {
        super(PacketOpcodes.DelMailRsp);

        DelMailRsp proto =
                DelMailRsp.newBuilder().addAllMailIdList(toDeleteIds).setRetcode(retcode).build();

        this.setData(proto);
    }
}
