package emu.grasscutter.game.activity.watcher;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.props.WatcherTriggerType;

/** Called on victory with remaining seconds, the member's starting world level and battle level. */
@ActivityWatcherType(WatcherTriggerType.TRIGGER_CRUCIBLE_WORLD_LEVEL_SCORE)
public class CrucibleWorldLevelScoreWatcher extends ActivityWatcher {
    @Override protected boolean isMeet(String... params) {
        var configured = getActivityWatcherData().getTriggerConfig().getParamList();
        if (params.length != 3 || configured.isEmpty()) return false;
        try {
            int remaining = Integer.parseInt(params[0]), ownLevel = Integer.parseInt(params[1]);
            int battleLevel = Integer.parseInt(params[2]), minimum = Integer.parseInt(configured.get(0));
            return minimum >= 0 && remaining >= minimum && ownLevel >= 0 && ownLevel == battleLevel;
        } catch (NumberFormatException e) { return false; }
    }
}
