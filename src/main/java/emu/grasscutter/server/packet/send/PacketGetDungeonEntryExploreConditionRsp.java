package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DungeonEntryBlockReasonOuterClass.DungeonEntryBlockReason;
import emu.grasscutter.net.proto.DungeonEntryCondOuterClass.DungeonEntryCond;
import emu.grasscutter.net.proto.GetDungeonEntryExploreConditionRspOuterClass.GetDungeonEntryExploreConditionRsp;

public class PacketGetDungeonEntryExploreConditionRsp extends BasePacket {

    /**
     * Tells the client why a domain is still locked. Only the conditions the player actually
     * failed are reported; {@code levelOk}/{@code questOk} come from the handler so this packet
     * stays a dumb builder.
     */
    public PacketGetDungeonEntryExploreConditionRsp(DungeonEntryData data, boolean levelOk, boolean questOk) {
        super(PacketOpcodes.GetDungeonEntryExploreConditionRsp);

        var builder = GetDungeonEntryExploreConditionRsp.newBuilder();

        if (data != null) {
            int level = data.getLevelCondition();
            int quest = data.getQuestCondition();

            DungeonEntryBlockReason reason;
            int param1;
            if (!levelOk && !questOk) {
                // Both unmet. There is only one param1 field, so the client shows a generic
                // "multiple conditions" message instead of listing them.
                reason = DungeonEntryBlockReason.DUNGEON_ENTRY_REASON_MULIPLE;
                param1 = level;
            } else if (!levelOk) {
                reason = DungeonEntryBlockReason.DUNGEON_ENTRY_REASON_LEVEL;
                param1 = level;
            } else {
                reason = DungeonEntryBlockReason.DUNGEON_ENTRY_REASON_QUEST;
                param1 = quest;
            }

            builder
                    .setRetcode(0)
                    .setDungeonEntryCond(
                            DungeonEntryCond.newBuilder().setCondReason(reason).setParam1(param1).build());
        } else {
            // Unknown entry id: nothing to explain, just deny.
            builder.setRetcode(1);
        }

        this.setData(builder.build());
    }

    /** Kept for callers that only know the config id; resolves it through the excel map. */
    public PacketGetDungeonEntryExploreConditionRsp(int dungeonId, boolean levelOk, boolean questOk) {
        this(GameData.getDungeonEntryDataMap().get(dungeonId), levelOk, questOk);
    }
}
