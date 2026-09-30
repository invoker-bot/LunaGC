package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.salesman.*;
import emu.grasscutter.net.proto.SalesmanStatusTypeOuterClass.SalesmanStatusType;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class SalesmanActivityTest {
    private static final long NOW = Instant.parse("2026-09-30T12:00:00Z").toEpochMilli();
    private static ActivityConfigItem config(int schedule) {
        var config=new ActivityConfigItem(); config.setActivityId(5003); config.setActivityType(3); config.setScheduleId(schedule);
        config.setBeginTime(new Date(NOW-1000)); config.setEndTime(new Date(NOW+86400000)); config.onLoad(); return config;
    }
    @BeforeEach void resources() throws Exception {
        var gson=new Gson(); var root=Path.of("resources/ExcelBinOutput");
        for (var row:gson.fromJson(Files.readString(root.resolve("ActivitySalesmanExcelConfigData.json")),SalesmanData[].class)) GameData.getSalesmanDataMap().put(row.getId(),row);
        for (var row:gson.fromJson(Files.readString(root.resolve("ActivitySalesmanDailyExcelConfigData.json")),SalesmanDailyData[].class)) {row.onLoad();GameData.getSalesmanDailyDataMap().put(row.getId(),row);}
        for (var row:gson.fromJson(Files.readString(root.resolve("RewardExcelConfigData.json")),RewardData[].class)) if(row.getId()>=470001&&row.getId()<=470007) {row.onLoad();GameData.getRewardDataMap().put(row.getId(),row);}
        var data=gson.fromJson("{activityId:5003,activityType:'NEW_ACTIVITY_SALESMAN',watcherId:[],condGroupId:[]}",ActivityData.class);
        data.onLoad(); GameData.getActivityDataMap().put(5003,data);
    }
    @AfterEach void cleanup() {
        GameData.getSalesmanDataMap().clear(); GameData.getSalesmanDailyDataMap().clear(); GameData.getActivityDataMap().remove(5003);
        for(int id=470001;id<=470007;id++) GameData.getRewardDataMap().remove(id);
    }
    @Test void configurationInstallsDedicatedHandlerAndRejectsMissingRewardsBeforePublish() {
        var prepared=ActivityManager.prepareConfiguration(List.of(config(5003009)));
        assertInstanceOf(SalesmanActivityHandler.class,prepared.activities().get(5003).getActivityHandler());
        GameData.getRewardDataMap().remove(470007);
        assertThrows(IllegalArgumentException.class,()->ActivityManager.prepareConfiguration(List.of(config(5003010))));
        assertEquals(5003009,prepared.activities().get(5003).getScheduleId());
    }
    @Test void detailFollowsPersistedProgressAndFreshScheduleGetsEmptyState() {
        var config=config(5003009); var handler=new SalesmanActivityHandler(); handler.setActivityConfigItem(config);
        var data=PlayerActivityData.of().activityId(5003).scheduleId(5003009).build(); handler.onInitPlayerActivityData(data);
        var initial=SalesmanActivityHandler.detail(data,config,NOW);
        assertEquals(1,initial.getDayIndex()); assertEquals(SalesmanStatusType.SALESMAN_STATUS_UNSTARTED,initial.getStatus());
        var progress=SalesmanSchedule.progress(data); progress.talk(1); progress.deliver(1); data.setDetail(progress);
        var saved=PlayerActivityData.of().activityId(5003).scheduleId(5003009).detail(data.getDetail()).build();
        var delivered=SalesmanActivityHandler.detail(saved,config,NOW);
        assertEquals(SalesmanStatusType.SALESMAN_STATUS_DELIVERED,delivered.getStatus()); assertTrue(delivered.getHasTalked());
        long tomorrow=Instant.parse("2026-09-30T20:00:00Z").toEpochMilli();
        var nextDay=SalesmanActivityHandler.detail(saved,config,tomorrow);
        assertEquals(2,nextDay.getDayIndex());
        assertEquals(SalesmanStatusType.SALESMAN_STATUS_UNSTARTED,nextDay.getStatus(),"Find Liben at the new day's location");
        assertTrue(nextDay.getHasTalked(),"Previously encountered flag is independent of today's encounter");
        assertEquals(1,SalesmanSchedule.progress(saved).remainingChances());
        config.setDisabled(true); assertEquals(SalesmanStatusType.SALESMAN_STATUS_NONE,SalesmanActivityHandler.detail(saved,config,NOW).getStatus());
        config.setDisabled(false); assertEquals(delivered,SalesmanActivityHandler.detail(saved,config,NOW));
        saved.setScheduleId(5003010); handler.onInitPlayerActivityData(saved);
        assertTrue(SalesmanSchedule.progress(saved).deliveredDays().isEmpty());
        assertEquals(SalesmanStatusType.SALESMAN_STATUS_NONE,SalesmanActivityHandler.detail(saved,config,NOW).getStatus());
    }
}
