package emu.grasscutter.game.dungeons.dungeon_results;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonEndStats;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.tower.TowerManager;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.TowerLevelEndNotifyOuterClass.TowerLevelEndNotify;
import java.util.List;

public class TowerResult extends BaseDungeonResult {
    WorldChallenge challenge;
    boolean canJump;
    boolean hasNextLevel;
    int nextFloorId;
    int currentStars;
    /** Items handed over for clearing this chamber, already added to the inventory. */
    List<ItemParamData> firstPassReward;

    public TowerResult(
            DungeonData dungeonData,
            DungeonEndStats dungeonStats,
            TowerManager towerManager,
            WorldChallenge challenge,
            int currentStars,
            List<ItemParamData> firstPassReward) {
        super(dungeonData, dungeonStats);
        this.challenge = challenge;
        this.canJump = towerManager.hasNextFloor();
        this.hasNextLevel = towerManager.hasNextLevel();
        this.nextFloorId = hasNextLevel ? 0 : towerManager.getNextFloorId();
        this.currentStars = currentStars;
        this.firstPassReward = firstPassReward;
    }

    // 7.0 declares continue_state as a plain uint32 rather than the nested ContinueStateType enum
    // the 6.7 protos had, so the constants are spelled out here. The values are the ones the 6.7
    // generated enum carried, read back off it rather than assumed.
    private static final int CONTINUE_STATE_CAN_NOT_CONTINUE = 0;
    private static final int CONTINUE_STATE_CAN_ENTER_NEXT_LEVEL = 1;
    private static final int CONTINUE_STATE_CAN_ENTER_NEXT_FLOOR = 2;

    @Override
    protected void onProto(DungeonSettleNotifyOuterClass.DungeonSettleNotify.Builder builder) {
        var continueStatus = CONTINUE_STATE_CAN_NOT_CONTINUE;
        // getCurLevelStars already treats a missing scene challenge as possible, and this result is
        // built on COMPLETED, so the two can meet.
        if (challenge != null && challenge.isSuccess()) {
            if (hasNextLevel) {
                continueStatus = CONTINUE_STATE_CAN_ENTER_NEXT_LEVEL;
            } else if (canJump) {
                continueStatus = CONTINUE_STATE_CAN_ENTER_NEXT_FLOOR;
            }
        }

        var towerLevelEndNotify =
                TowerLevelEndNotify.newBuilder()
                        .setIsSuccess(challenge.isSuccess())
                        .setContinueState(continueStatus);

        // The settle screen lists what the chamber paid out. A repeat clear of an already-rewarded
        // chamber grants nothing, so the list is empty rather than a placeholder pile of Mora.
        if (firstPassReward != null) {
            for (var item : firstPassReward) {
                if (item.getId() == 0 || item.getCount() <= 0) continue;
                towerLevelEndNotify.addRewardItemList(
                        ItemParamOuterClass.ItemParam.newBuilder()
                                .setItemId(item.getId())
                                .setCount(item.getCount()));
            }
        }

        for (int i = 1; i <= currentStars; i++) {
            towerLevelEndNotify.addFinishedStarCondList(i);
        }

        if (nextFloorId > 0 && canJump) {
            towerLevelEndNotify.setNextFloorId(nextFloorId);
        }
        builder.setTowerLevelEndNotify(towerLevelEndNotify.build());
    }
}
