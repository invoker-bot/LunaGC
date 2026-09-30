package emu.grasscutter.game.activity.salesman;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.SalesmanActivityDetailInfoOuterClass.SalesmanActivityDetailInfo;
import emu.grasscutter.net.proto.SalesmanStatusTypeOuterClass.SalesmanStatusType;
import java.util.*;

@GameActivity(ActivityType.NEW_ACTIVITY_SALESMAN)
public final class SalesmanActivityHandler extends ActivityHandler {
    @Override public void initWatchers(Map<WatcherTriggerType, ConstructorAccess<?>> watcherTypes) {
        super.initWatchers(watcherTypes);
        if (getActivityConfigItem().getActivityId() != SalesmanSchedule.ACTIVITY_ID || !SalesmanSchedule.resourcesAvailable()) {
            throw new IllegalArgumentException("百货奇货首期配置或奖励资源缺失");
        }
    }

    @Override public void onInitPlayerActivityData(PlayerActivityData data) {
        data.setDetail(new SalesmanProgress());
    }

    @Override public void onProtoBuild(PlayerActivityData data, ActivityInfo.Builder info) {
        info.setSalesmanInfo(detail(data, getActivityConfigItem(), System.currentTimeMillis()));
    }

    public static SalesmanActivityDetailInfo detail(PlayerActivityData data, ActivityConfigItem config, long now) {
        int day = SalesmanSchedule.dayIndex(config, now);
        if (day == 0 || data == null || data.getActivityId() != SalesmanSchedule.ACTIVITY_ID
                || data.getScheduleId() != config.getScheduleId()) {
            return SalesmanActivityDetailInfo.getDefaultInstance();
        }
        SalesmanProgress progress;
        try { progress = SalesmanSchedule.progress(data); }
        catch (RuntimeException invalid) { return SalesmanActivityDetailInfo.getDefaultInstance(); }
        var status = progress.deliveredDays().contains(day) ? SalesmanStatusType.SALESMAN_STATUS_DELIVERED
                : progress.hasTalked(day) ? SalesmanStatusType.SALESMAN_STATUS_STARTED
                : SalesmanStatusType.SALESMAN_STATUS_UNSTARTED;
        return SalesmanActivityDetailInfo.newBuilder().setDayIndex(Math.min(day, 7)).setStatus(status)
                .setKOPLLPLDGGH(progress.deliveredDays().contains(day))
                .putAllSelectedRewardIdMap(progress.selectedRewardIdMap()).build();
    }
}
