package io.grasscutter;

import static emu.grasscutter.game.activity.condition.ActivityConditions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.activity.ActivityCondExcelConfigData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.condition.*;
import emu.grasscutter.game.activity.condition.all.*;
import emu.grasscutter.game.player.Player;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CrucibleOpeningConditionsTest {
    @BeforeAll
    static void initializeRuntime() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    private ActivityConditionExecutor conditions(int level, boolean introductionComplete)
            throws Exception {
        var rows =
                new Gson()
                        .fromJson(
                                Files.readString(
                                        Path.of("resources/ExcelBinOutput/NewActivityCondExcelConfigData.json")),
                                ActivityCondExcelConfigData[].class);
        var conditions = new Int2ObjectOpenHashMap<ActivityCondExcelConfigData>();
        var mapping = new Int2ObjectOpenHashMap<PlayerActivityData>();
        var player =
                new Player() {
                    @Override
                    public int getLevel() {
                        return level;
                    }
                };
        var progress =
                PlayerActivityData.of().activityId(5001).scheduleId(5001005).player(player).build();
        for (var row : rows)
            if (row.getId() >= 500101 && row.getId() <= 500103) {
                row.onLoad();
                conditions.put(row.getId(), row);
                mapping.put(row.getId(), progress);
            }
        var config = new ActivityConfigItem();
        config.setActivityId(5001);
        config.setBeginTime(new Date(System.currentTimeMillis() - 86400000L));
        var quest =
                new ActivityConditionBaseHandler() {
                    @Override
                    public boolean execute(PlayerActivityData data, ActivityConfigItem item, int... params) {
                        assertArrayEquals(new int[] {4091105}, params);
                        return introductionComplete;
                    }
                };
        return new BasicActivityConditionExecutor(
                Map.of(5001, config),
                conditions,
                mapping,
                Map.of(
                        NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL,
                        new DaysGreatEqual(),
                        NEW_ACTIVITY_COND_PLAYER_LEVEL_GREAT_EQUAL,
                        new PlayerLevelGreatEqualActivityActivityCondition(),
                        NEW_ACTIVITY_COND_QUEST_FINISH,
                        quest));
    }

    @Test
    void openingDoesNotGrantAdventureRankOrCompleteTheIntroduction() throws Exception {
        assertEquals(
                List.of(500103),
                conditions(8, false).getMeetActivitiesConditions(List.of(500101, 500102, 500103)));
        assertEquals(
                List.of(500103),
                conditions(15, false).getMeetActivitiesConditions(List.of(500101, 500102, 500103)));
        assertEquals(
                List.of(500101, 500103),
                conditions(16, false).getMeetActivitiesConditions(List.of(500101, 500102, 500103)));
        assertEquals(
                List.of(500101, 500102, 500103),
                conditions(16, true).getMeetActivitiesConditions(List.of(500101, 500102, 500103)));
    }
}
