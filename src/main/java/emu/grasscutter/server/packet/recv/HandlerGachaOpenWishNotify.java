package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

// Arrived from a live 7.0 client (2026-09-23, uid 10001, scene 1004) as {10:varint=302,
// 12:varint=3703} -- a gacha schedule id paired with the scene the client opened the wish screen
// from. No generated proto, so neither field can be read, and a Notify is not something the client
// waits on. The gacha flow the server actually drives is the GetGachaInfoReq / DoGachaReq pair, so
// dropping this changes nothing the player sees. The handler exists to close the harvest loop: an
// opcode with a registered handler is no longer announced as unhandled, so the backlog stays a list
// of what is genuinely still missing.
@Opcodes(PacketOpcodes.GachaOpenWishNotify)
public class HandlerGachaOpenWishNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
    }
}
