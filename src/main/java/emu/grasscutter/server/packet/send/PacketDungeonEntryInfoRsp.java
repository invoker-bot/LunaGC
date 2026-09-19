package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DungeonEntryInfoOuterClass.DungeonEntryInfo;
import emu.grasscutter.net.proto.DungeonEntryInfoRspOuterClass.DungeonEntryInfoRsp;
import emu.grasscutter.net.proto.WeeklyBossResinDiscountInfoOuterClass.WeeklyBossResinDiscountInfo;
import java.util.*;

public class PacketDungeonEntryInfoRsp extends BasePacket {

    public PacketDungeonEntryInfoRsp(PointData pointData, Player player) {
        super(PacketOpcodes.DungeonEntryInfoRsp);

        DungeonEntryInfoRsp.Builder proto =
                DungeonEntryInfoRsp.newBuilder().setPointId(pointData.getId());

        if (pointData.getDungeonIds() != null) {
            for (int dungeonId : pointData.getDungeonIds()) {
                proto.addDungeonEntryList(buildEntryInfo(dungeonId, player));
            }
        }

        this.setData(proto);
    }

    /**
     * Used in conjunction with quest-related dungeons.
     *
     * @param pointData The data associated with the dungeon.
     * @param additional A collection of additional quest-related dungeon IDs.
     */
    public PacketDungeonEntryInfoRsp(PointData pointData, List<Integer> additional, Player player) {
        super(PacketOpcodes.DungeonEntryInfoRsp);

        var packet = DungeonEntryInfoRsp.newBuilder().setPointId(pointData.getId());

        // Add dungeon IDs from the point data.
        if (pointData.getDungeonIds() != null) {
            Arrays.stream(pointData.getDungeonIds())
                    .forEach(id -> packet.addDungeonEntryList(buildEntryInfo(id, player)));
        }

        // Add additional dungeon IDs.
        additional.forEach(id -> packet.addDungeonEntryList(buildEntryInfo(id, player)));

        this.setData(packet);
    }

    /**
     * One entry per dungeon the point offers. Weekly bosses need the claim counter and the resin
     * discount spelled out, otherwise the client shows a free domain and never offers the
     * discounted-claims display the trounce domains use.
     */
    private static DungeonEntryInfo buildEntryInfo(int dungeonId, Player player) {
        var info = DungeonEntryInfo.newBuilder().setDungeonId(dungeonId);

        var data = GameData.getDungeonDataMap().get(dungeonId);
        if (data == null || data.getSubType() != DungeonSubType.DUNGEON_SUB_BOSS) {
            return info.build();
        }

        int used = player.getWeeklyBossChestNum();
        int discountedLeft =
                Math.max(0, DungeonManager.MAX_WEEKLY_BOSS_DISCOUNT_COUNT - used);

        info
                // The server does not track per-domain clears, so the "completed" stamp is left off.
                .setIsPassed(false)
                .setBossChestNum(used)
                .setMaxBossChestNum(DungeonManager.MAX_WEEKLY_BOSS_DISCOUNT_COUNT)
                .setWeeklyBossResinDiscountInfo(
                        WeeklyBossResinDiscountInfo.newBuilder()
                                .setDiscountNum(discountedLeft)
                                .setDiscountNumLimit(DungeonManager.MAX_WEEKLY_BOSS_DISCOUNT_COUNT)
                                // What the next claim costs: discounted while any claims remain, full price
                                // afterwards.
                                .setResinCost(DungeonManager.nextWeeklyBossResinCost(player))
                                .setOriginalResinCost(DungeonManager.WEEKLY_BOSS_RESIN_COST)
                                .build());

        return info.build();
    }

    /** Kept for callers that do not have a player context (the invalid-point reply). */
    public PacketDungeonEntryInfoRsp() {
        super(PacketOpcodes.DungeonEntryInfoRsp);

        DungeonEntryInfoRsp proto = DungeonEntryInfoRsp.newBuilder().setRetcode(1).build();

        this.setData(proto);
    }
}
