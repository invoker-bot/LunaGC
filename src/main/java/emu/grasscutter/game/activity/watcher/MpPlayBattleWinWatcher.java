package emu.grasscutter.game.activity.watcher;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.props.WatcherTriggerType;

@ActivityWatcherType(WatcherTriggerType.TRIGGER_MP_PLAY_BATTLE_WIN)
public class MpPlayBattleWinWatcher extends ActivityWatcher {
    @Override protected boolean isMeet(String... params) {
        var configured = getActivityWatcherData().getTriggerConfig().getParamList();
        return params.length == 1 && !configured.isEmpty() && configured.get(0).equals(params[0]);
    }
}
