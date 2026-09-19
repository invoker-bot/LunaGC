package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetDungeonEntryExploreConditionReqOuterClass.GetDungeonEntryExploreConditionReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketDungeonEntryToBeExploreNotify;
import emu.grasscutter.server.packet.send.PacketGetDungeonEntryExploreConditionRsp;

@Opcodes(PacketOpcodes.GetDungeonEntryExploreConditionReq)
public class HandlerGetDungeonEntryExploreConditionReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = GetDungeonEntryExploreConditionReq.parseFrom(payload);
        var player = session.getPlayer();
        int entryId = req.getDungeonEntryConfigId();

        // Send GetDungeonEntryExploreConditionRsp if the entry conditions
        // (adventurer rank or quest completion) are not met. Parsed from
        // DungeonEntryExcelConfigData.json via DungeonEntryData.
        var data = GameData.getDungeonEntryDataMap().get(entryId);
        if (data == null) {
            // Entry id the excel does not know (newer client content or a mod). Keep the old
            // permissive behaviour so the domain is still reachable instead of soft-locking.
            // session.send(new PacketGetDungeonEntryExploreConditionRsp(entryId, false, false));
            session.send(
                    new PacketDungeonEntryToBeExploreNotify(
                            req.getDungeonEntryScenePointId(), req.getSceneId(), entryId));
            return;
        }

        int level = data.getLevelCondition();
        int quest = data.getQuestCondition();
        boolean levelOk = level <= 0 || player.getLevel() >= level;
        boolean questOk = quest <= 0 || isQuestFinished(player, quest);

        if (levelOk && questOk) {
            session.send(
                    new PacketDungeonEntryToBeExploreNotify(
                            req.getDungeonEntryScenePointId(), req.getSceneId(), entryId));
        } else {
            session.send(new PacketGetDungeonEntryExploreConditionRsp(data, levelOk, questOk));
        }
    }

    /** A main quest the player has never seen is not finished, which is what locks the entry. */
    private static boolean isQuestFinished(emu.grasscutter.game.player.Player player, int mainQuestId) {
        var mainQuest = player.getQuestManager().getMainQuestById(mainQuestId);
        return mainQuest != null && mainQuest.isFinished();
    }
}
