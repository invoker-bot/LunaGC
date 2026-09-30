package emu.grasscutter.game.activity.condition.all;

import static emu.grasscutter.game.activity.condition.ActivityConditions.NEW_ACTIVITY_COND_DAYS_LESS;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.condition.*;

@ActivityCondition(NEW_ACTIVITY_COND_DAYS_LESS)
public class DayLess extends ActivityConditionBaseHandler {
    @Override
    public boolean execute(
            PlayerActivityData activityData, ActivityConfigItem activityConfig, int... params) {
        if (activityConfig.getActivityId() == emu.grasscutter.game.activity.aster.AsterSchedule.ACTIVITY_ID) {
            int day = emu.grasscutter.game.activity.aster.AsterSchedule.dayIndex(activityConfig, System.currentTimeMillis());
            return day > 0 && day < params[0];
        }
        return true; // TODO implement this and add possibility to always return true
    }
}
