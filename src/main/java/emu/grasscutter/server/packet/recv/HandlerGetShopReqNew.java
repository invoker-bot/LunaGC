package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopReqNewOuterClass.GetShopReqNew;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetShopRspNew;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Opcodes(PacketOpcodes.GetShopReqNew)
public class HandlerGetShopReqNew extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        GetShopReqNew req = GetShopReqNew.parseFrom(payload);

        // The client uses this to decide which shop tabs exist. Advertise the shops the server
        // actually loaded, not a 4.x snapshot that still names three tabs the 7.0 excel dropped.
        var shopIds =
                session.getServer().getShopSystem().getShopData().keySet().stream()
                        .sorted()
                        .collect(Collectors.toCollection(ArrayList::new));

        session.send(new PacketGetShopRspNew(req.getParam(), shopIds));
    }
}