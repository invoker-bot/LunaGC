package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import emu.grasscutter.data.excels.activity.ActivityData;
import emu.grasscutter.data.excels.tower.TowerScheduleData;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class ResourceCompatibilityTest {
    @Test
    void towerScheduleSkipsEntriesWithoutFloors() {
        var json = JsonParser.parseString(
                "{\"scheduleId\":1,\"schedules\":[{\"floorList\":null},{\"floorList\":[101]}]}");
        var data = JsonUtils.decode(json, TowerScheduleData.class);

        data.onLoad();

        assertEquals(1, data.getSchedules().size());
        assertEquals(101, data.getSchedules().get(0).getFloorList().get(0));
    }

    @Test
    void activityWithoutWatchersLoadsAsEmpty() {
        var json = JsonParser.parseString("{\"activityId\":1}");
        var data = JsonUtils.decode(json, ActivityData.class);

        data.onLoad();

        assertTrue(data.getWatcherDataList().isEmpty());
    }
}
