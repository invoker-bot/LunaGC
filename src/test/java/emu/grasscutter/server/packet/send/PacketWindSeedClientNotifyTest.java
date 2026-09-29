package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import emu.grasscutter.net.proto.WindSeedType1NotifyOuterClass.WindSeedType1Notify;
import org.junit.jupiter.api.Test;

public final class PacketWindSeedClientNotifyTest {
    @Test
    public void defaultPayloadUses71Field() throws Exception {
        byte[] lua = {1, 2, 3};
        var notify = WindSeedType1Notify.parseFrom(PacketWindSeedClientNotify.encode(lua, 0));
        assertArrayEquals(lua, notify.getPayload().toByteArray());
    }
}
