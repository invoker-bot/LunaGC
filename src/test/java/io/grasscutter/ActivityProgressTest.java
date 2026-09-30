package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.activity.PlayerActivityData.WatcherInfo;
import org.junit.jupiter.api.Test;

class ActivityProgressTest {
    @Test void emptyOrNegativeScoreDoesNotCompleteAMission() {
        var mission = WatcherInfo.of().totalProgress(10000).curProgress(9999).build();
        assertFalse(mission.advance(0));
        assertFalse(mission.advance(-300));
        assertEquals(9999, mission.getCurProgress());
    }

    @Test void LargeScoreSaturatesWithoutOverflowAndFinishedRewardsRemainUnchanged() {
        var mission = WatcherInfo.of().totalProgress(160000).curProgress(10000).build();
        assertTrue(mission.advance(Integer.MAX_VALUE));
        assertEquals(160000, mission.getCurProgress());
        mission.setTakenReward(true);
        assertFalse(mission.advance(300));
        assertTrue(mission.isTakenReward());
    }
}
