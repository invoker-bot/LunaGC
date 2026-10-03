package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;

public final class PacketWorldWatcherAllDataNotify extends BasePacket {
    public PacketWorldWatcherAllDataNotify(Player player) {
        super(PacketOpcodes.WorldWatcherAllDataNotify);
        setData(player.getBeyondProgress().toProto());
    }
}
