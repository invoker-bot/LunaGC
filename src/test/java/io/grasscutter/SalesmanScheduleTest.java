package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.salesman.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class SalesmanScheduleTest {
    private static final Gson GSON = new Gson();
    private static long time(String value) { return ZonedDateTime.parse(value + "+08:00[Asia/Shanghai]").toInstant().toEpochMilli(); }
    private static ActivityConfigItem config() {
        var config = new ActivityConfigItem();
        config.setActivityId(5003); config.setActivityType(3); config.setScheduleId(5003009);
        config.setBeginTime(new Date(time("2026-09-30T10:14:23")));
        config.setEndTime(new Date(time("2026-10-07T10:14:23"))); config.onLoad();
        return config;
    }
    private static PlayerActivityData player() {
        return PlayerActivityData.of().activityId(5003).scheduleId(5003009).uid(10001).build();
    }
    @BeforeEach void resources() throws Exception {
        var sources = GSON.fromJson(Files.readString(Path.of("resources/ExcelBinOutput/ActivitySalesmanExcelConfigData.json")), SalesmanData[].class);
        Arrays.stream(sources).forEach(row -> GameData.getSalesmanDataMap().put(row.getId(), row));
        var days = GSON.fromJson(Files.readString(Path.of("resources/ExcelBinOutput/ActivitySalesmanDailyExcelConfigData.json")), SalesmanDailyData[].class);
        Arrays.stream(days).forEach(row -> { row.onLoad(); GameData.getSalesmanDailyDataMap().put(row.getId(), row); });
    }
    @AfterEach void cleanup() {
        GameData.getSalesmanDataMap().clear(); GameData.getSalesmanDailyDataMap().clear();
    }
    @Test void resourceDefinitionsAreIndependentOfGmReplayId() {
        assertNotEquals(5003001, config().getScheduleId());
        assertEquals(List.of(1,2,3,4,5,6,7), SalesmanSchedule.source().getDailyConfigIdList());
        assertEquals(1, SalesmanSchedule.daily(config(), time("2026-09-30T12:00:00")).getId());
        assertSame(GameData.getSalesmanDataMap(), GameData.getMapByResourceDef(SalesmanData.class));
        assertSame(GameData.getSalesmanDailyDataMap(), GameData.getMapByResourceDef(SalesmanDailyData.class));
    }
    @Test void daysResetAtFourAmChinaTimeAcrossMidnightAndContainerTimezone() {
        assertEquals(0, SalesmanSchedule.dayIndex(config(), time("2026-09-30T10:14:22")));
        assertEquals(1, SalesmanSchedule.dayIndex(config(), time("2026-09-30T10:14:23")));
        assertEquals(1, SalesmanSchedule.dayIndex(config(), time("2026-10-01T03:59:59")));
        assertEquals(2, SalesmanSchedule.dayIndex(config(), time("2026-10-01T04:00:00")));
        assertEquals(2, SalesmanSchedule.dayIndex(config(), time("2026-10-01T10:14:23")));
        assertEquals(0, SalesmanSchedule.dayIndex(config(), time("2026-10-07T10:14:23")));
    }
    @Test void allSevenDailyCostsAndRewardTotalsMatchOriginalResources() throws Exception {
        var expected = List.of(100011,100020,100051,100002,100001,100013,100014);
        for (int day=1; day<=7; day++) {
            var row = SalesmanSchedule.daily(config(), time("2026-10-0" + Math.max(1, day-1) + "T12:00:00"));
            if (day==1) row = SalesmanSchedule.daily(config(), time("2026-09-30T12:00:00"));
            assertEquals(expected.get(day-1), row.getCostItemList().get(0).getId());
            assertEquals(1, row.getCostItemList().size());
            assertEquals(10, row.getCostItemList().get(0).getCount());
            assertEquals("Activity_5003_0" + day, row.getTracePosition());
        }
        var rewards = Arrays.stream(GSON.fromJson(Files.readString(Path.of("resources/ExcelBinOutput/RewardExcelConfigData.json")), RewardData[].class))
                .filter(row -> row.getId()>=470001 && row.getId()<=470007).toList();
        assertEquals(7, rewards.size());
        assertEquals(210, rewards.stream().flatMap(row -> row.getRewardItemList().stream()).filter(item -> item.getId()==201).mapToInt(item -> item.getCount()).sum());
        assertEquals(List.of(470001,470002,470003,470004,470005,470006), SalesmanSchedule.source().getNormalRewardIdList());
        assertEquals(List.of(470007), SalesmanSchedule.source().getSpecialRewardIdList());
        assertEquals(1.0, SalesmanSchedule.source().getSpecialProbList().get(6));
    }
    @Test void deliveryDoesNotCatchUpMissedDaysAndUnusedChancesSurviveRefreshAndSave() {
        var data = player(); var progress = new SalesmanProgress();
        assertTrue(progress.deliver(1)); assertFalse(progress.deliver(1));
        data.setDetail(progress);
        assertFalse(SalesmanSchedule.canDeliver(data, config(), 12, time("2026-09-30T12:00:00")));
        assertTrue(SalesmanSchedule.canDeliver(data, config(), 12, time("2026-10-02T12:00:00")));
        assertEquals(Set.of(1), SalesmanSchedule.progress(data).deliveredDays());
        assertEquals(1, SalesmanSchedule.progress(data).remainingChances());
        assertTrue(SalesmanSchedule.progress(data).deliver(3));
        assertFalse(progress.deliver(0)); assertFalse(progress.deliver(8));
        assertThrows(UnsupportedOperationException.class, () -> progress.deliveredDays().add(2));
    }
    @Test void closedExpiredWrongScheduleLowRankAndExtendedEighthDayCannotDeliver() {
        var config = config(); var data = player(); long now=time("2026-09-30T12:00:00");
        assertTrue(SalesmanSchedule.canDeliver(data, config, 12, now));
        assertFalse(SalesmanSchedule.canDeliver(data, config, 11, now));
        data.setScheduleId(5003008); assertFalse(SalesmanSchedule.canDeliver(data, config, 12, now));
        data.setScheduleId(5003009); config.setDisabled(true); assertFalse(SalesmanSchedule.canDeliver(data, config, 12, now));
        config.setDisabled(false); assertFalse(SalesmanSchedule.canDeliver(data, config, 12, config.getEndTime().getTime()));
        config.setEndTime(new Date(time("2026-10-30T12:00:00")));
        config.setCloseTime(config.getEndTime());
        assertFalse(SalesmanSchedule.canDeliver(data, config, 12, time("2026-10-07T12:00:00")));
        assertEquals(7, SalesmanSchedule.daily(config, time("2026-10-07T12:00:00")).getId(), "Final-day hint remains available when opening accumulated chances");
    }
    @Test void blankCostSlotsAreFilteredButCorruptCostsAreRejected() {
        var row=GSON.fromJson("{dailyConfigId:1,costItemList:[{id:100011,count:10},{}]}", SalesmanDailyData.class);
        row.onLoad(); assertEquals(1, row.getCostItemList().size());
        var corrupt=GSON.fromJson("{dailyConfigId:1,costItemList:[{id:100011}]}", SalesmanDailyData.class);
        assertThrows(IllegalArgumentException.class, corrupt::onLoad);
    }
    @Test void invalidSavedProgressIsRejectedWithoutResettingItOrRestoringChances() {
        for (String invalid : List.of("null", "{deliveredDays:[0]}", "{deliveredDays:[8]}", "{deliveredDays:null}",
                "{selectedRewardIdMap:{0:470001}}", "not-json")) {
            var data = PlayerActivityData.of().activityId(5003).scheduleId(5003009).uid(10001).detail(invalid).build();
            assertFalse(SalesmanSchedule.canDeliver(data, config(), 12, time("2026-09-30T12:00:00")));
            assertThrows(IllegalArgumentException.class, () -> SalesmanSchedule.progress(data));
            assertEquals(invalid, data.getDetail(), "Do not silently reset damaged saved progress");
        }
    }
}
