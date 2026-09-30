package emu.grasscutter.game.activity.watcher;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.props.WatcherTriggerType;

@ActivityWatcherType(WatcherTriggerType.TRIGGER_CRUCIBLE_ELEMENT_SCORE)
public class CrucibleElementScoreWatcher extends ActivityWatcher {
    @Override protected boolean isMeet(String... params) { return getProgressDelta(params) > 0; }

    @Override protected int getProgressDelta(String... params) {
        if (params.length != 1) return 0;
        try { return Math.max(0, Integer.parseInt(params[0])); }
        catch (NumberFormatException e) { return 0; }
    }
}
