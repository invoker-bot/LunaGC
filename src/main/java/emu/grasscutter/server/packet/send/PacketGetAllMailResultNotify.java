package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetAllMailResultNotifyOuterClass.GetAllMailResultNotify;
import java.util.UUID;

public final class PacketGetAllMailResultNotify extends BasePacket {
    public PacketGetAllMailResultNotify(Player player, boolean collected) {
        super(PacketOpcodes.GetAllMailResultNotify);
        setData(
                GetAllMailResultNotify.newBuilder()
                        .setTransaction(UUID.randomUUID().toString())
                        .setIsCollected(collected)
                        .setPageIndex(1)
                        .setTotalPageCount(1)
                        .addAllMailList(
                                player.getMailHandler().getMail(collected).stream()
                                        .map(m -> m.toProto(player))
                                        .toList())
                        .build());
    }
}
