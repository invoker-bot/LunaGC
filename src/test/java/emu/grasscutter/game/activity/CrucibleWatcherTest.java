package emu.grasscutter.game.activity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.activity.ActivityWatcherData;
import emu.grasscutter.game.activity.watcher.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CrucibleWatcherTest {
    private final ActivityWatcherData[] metadata = loadMetadata();

    private static ActivityWatcherData[] loadMetadata() {
        try {
            var data = new Gson().fromJson(Files.readString(Path.of("resources/ExcelBinOutput/NewActivityWatcherConfigData.json")),
                    ActivityWatcherData[].class);
            Arrays.stream(data).forEach(ActivityWatcherData::onLoad);
            return data;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private ActivityWatcher watcher(int id, ActivityWatcher implementation) {
        implementation.setWatcherId(id);
        implementation.setActivityWatcherData(Arrays.stream(metadata).filter(data -> data.getId() == id).findFirst().orElseThrow());
        return implementation;
    }

    private static class Progress extends PlayerActivityData {
        final Map<Integer, WatcherInfo> missions = new HashMap<>();
        int writes;
        Progress(ActivityWatcher... watchers) {
            super(null, 10001, 5001, 5001005, new HashMap<>(), null, null, null);
            for (var watcher : watchers) missions.put(watcher.getWatcherId(), WatcherInfo.init(watcher));
        }
        @Override public synchronized void addWatcherProgress(int id, int delta) {
            if (missions.get(id).advance(delta)) writes++;
        }
        int value(int id) { return missions.get(id).getCurProgress(); }
    }

    @Test void victoryCountsOnlyTheConfiguredPlayAndKillCountsOnlyListedGroups() {
        var win = watcher(1500102, new MpPlayBattleWinWatcher());
        var kill = watcher(1500109, new KillGroupMonsterWatcher());
        var progress = new Progress(win, kill);
        win.trigger(progress, "4"); win.trigger(progress); win.trigger(progress, "1");
        assertEquals(1, progress.value(1500102));
        kill.trigger(progress, "133003554"); kill.trigger(progress, "305001001");
        kill.trigger(progress, "133003999"); kill.trigger(progress, "1");
        assertEquals(2, progress.value(1500109));
    }

    @Test void efficiencyAddsQuantitiesAndRejectsInvalidSubmission() {
        var score = watcher(1500105, new CrucibleElementScoreWatcher());
        var progress = new Progress(score);
        score.trigger(progress, "1600"); score.trigger(progress, "3200");
        assertEquals(4800, progress.value(1500105));
        for (var bad : List.of("0", "-300", "NaN", "2147483648")) score.trigger(progress, bad);
        assertEquals(4800, progress.value(1500105)); assertEquals(2, progress.writes);
        score.trigger(progress, "2147483647");
        assertEquals(10000, progress.value(1500105));
    }

    @Test void threeMinuteVictoryUsesRemainingTimeAndStartingWorldLevels() {
        var fast = watcher(1500113, new CrucibleWorldLevelScoreWatcher());
        var progress = new Progress(fast);
        fast.trigger(progress, "719", "8", "8"); // 181 seconds, too slow.
        fast.trigger(progress, "800", "7", "8"); // Guest's world level differs.
        fast.trigger(progress, "bad", "8", "8");
        assertEquals(0, progress.value(1500113));
        fast.trigger(progress, "720", "8", "8"); // Exactly three minutes.
        assertEquals(1, progress.value(1500113));
        fast.trigger(progress, "899", "8", "8"); assertEquals(1, progress.writes);
    }
}
