package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BeyondCosmeticDataNotifyOuterClass.BeyondCosmeticDataNotify;

public final class PacketBeyondCosmeticDataNotify extends BasePacket {
    public PacketBeyondCosmeticDataNotify(Player player) {
        super(PacketOpcodes.BeyondCosmeticDataNotify);
        setData(
                BeyondCosmeticDataNotify.newBuilder()
                        .addAllOwnedCostumeList(player.getBeyondCloset().toProto()));
    }
}
