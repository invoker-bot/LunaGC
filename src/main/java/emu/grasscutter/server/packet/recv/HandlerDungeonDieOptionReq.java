package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DungeonDieOptionReqOuterClass.DungeonDieOptionReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.DungeonDieOptionReq)
public class HandlerDungeonDieOptionReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        DungeonDieOptionReq req = DungeonDieOptionReq.parseFrom(payload);
        var player = session.getPlayer();
        var dungeonSystem = player.getServer().getDungeonSystem();

        // Only the quit path was wired, so picking "restart", "abandon" or "revive" on
        // the death screen silently did nothing and left the player stuck on it.
        switch (req.getDieOption()) {
            case DIE_OPT_REPLAY -> dungeonSystem.restartDungeon(player);
            case DIE_OPT_CANCEL -> dungeonSystem.exitDungeon(player);
            case DIE_OPT_REVIVE -> player.getTeamManager().respawnTeam();
            default -> {
                // DIE_OPT_NONE / UNRECOGNIZED: the "leave immediately" checkbox the
                // death screen also sends alongside the option.
                if (req.getIsQuitImmediately()) {
                    dungeonSystem.exitDungeon(player);
                }
            }
        }

        session.getPlayer().sendPacket(new BasePacket(PacketOpcodes.DungeonDieOptionRsp));
    }
}
