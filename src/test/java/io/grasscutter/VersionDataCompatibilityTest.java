package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BattlePassScheduleData;
import emu.grasscutter.data.excels.DailyTaskData;
import emu.grasscutter.data.excels.avatar.AvatarReplaceCostumeData;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

public final class VersionDataCompatibilityTest {
    @Test
    public void newerExcelFieldNamesLoad() {
        var task =
                JsonUtils.decode(JsonParser.parseString("{\"ID\":123}"), DailyTaskData.class);
        var costume =
                JsonUtils.decode(
                        JsonParser.parseString("{\"replaceCostumeId\":456}"),
                        AvatarReplaceCostumeData.class);

        assertEquals(123, task.getId());
        assertEquals(456, costume.getCostumeId());
    }

    @Test
    public void battlePassUsesOnlySchedulesForTheActiveClientVersion() {
        var schedules = GameData.getBattlePassScheduleDataMap();
        var previousSchedules = new Int2ObjectOpenHashMap<>(schedules);
        var previousVersion = GameConstants.VERSION_PARTS;
        try {
            schedules.clear();
            GameConstants.VERSION_PARTS = new int[] {7, 0, 0};
            assertEquals(2700, BattlePassScheduleData.currentId());

            schedules.put(7100, new BattlePassScheduleData());
            assertEquals(2700, BattlePassScheduleData.currentId());

            schedules.put(7000, new BattlePassScheduleData());
            assertEquals(7000, BattlePassScheduleData.currentId());

            GameConstants.VERSION_PARTS = new int[] {7, 1, 0};
            assertEquals(7100, BattlePassScheduleData.currentId());
        } finally {
            schedules.clear();
            schedules.putAll(previousSchedules);
            GameConstants.VERSION_PARTS = previousVersion;
        }
    }
}
