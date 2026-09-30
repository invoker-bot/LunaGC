package emu.grasscutter.game.activity.condition.all;

import static emu.grasscutter.game.activity.condition.ActivityConditions.NEW_ACTIVITY_COND_SALESMAN_CAN_DELIVER;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.condition.*;
import emu.grasscutter.game.activity.salesman.SalesmanSchedule;

@ActivityCondition(NEW_ACTIVITY_COND_SALESMAN_CAN_DELIVER)
public class SalesmanCanDeliver extends ActivityConditionBaseHandler {
    @Override
    public boolean execute(
            PlayerActivityData activityData, ActivityConfigItem activityConfig, int... params) {
        return activityData != null && activityData.getPlayer() != null
                && SalesmanSchedule.canDeliver(activityData, activityConfig,
                        activityData.getPlayer().getLevel(), System.currentTimeMillis());
    }
}
