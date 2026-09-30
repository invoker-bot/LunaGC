package emu.grasscutter.game.activity.watcher;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.props.WatcherTriggerType;

@ActivityWatcherType(WatcherTriggerType.TRIGGER_KILL_GROUP_MONSTER)
public class KillGroupMonsterWatcher extends ActivityWatcher {
    @Override protected boolean isMeet(String... params) {
        if (params.length != 1) return false;
        for (var entry : getActivityWatcherData().getTriggerConfig().getParamList())
            for (var groupId : entry.split(","))
                if (groupId.trim().equals(params[0])) return true;
        return false;
    }
}
