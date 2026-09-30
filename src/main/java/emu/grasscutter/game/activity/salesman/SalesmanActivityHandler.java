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
        if (getActivityConfigItem().getActivityId() != SalesmanSchedule.ACTIVITY_ID
                || !SalesmanSchedule.resourcesAvailable() || !SalesmanNpcScene.resourcesAvailable()) {
            throw new IllegalArgumentException("百货奇货首期材料、奖励或七套 NPC 资源缺失");
        }
    }

    @Override public void onInitPlayerActivityData(PlayerActivityData data) {
        data.setDetail(new SalesmanProgress());
    }

    @Override public void onProtoBuild(PlayerActivityData data, ActivityInfo.Builder info) {
        long now = System.currentTimeMillis();
        info.setSalesmanInfo(detail(data, getActivityConfigItem(), now));
        // The original client conditions include local NPC creation, and activity 5003 has no quest
        // document for NOT_FINISH_TALK. Its replay conditions must come from its own daily progress.
        int rank = data != null && data.getPlayer() != null ? data.getPlayer().getLevel() : 0;
        info.clearMeetCondList().addAllMeetCondList(conditions(data, getActivityConfigItem(), rank, now));
    }

    public static List<Integer> conditions(PlayerActivityData data, ActivityConfigItem config, int rank, long now) {
        int day = SalesmanSchedule.dayIndex(config, now);
        if (day < 1 || rank < 12 || data == null || data.getActivityId() != SalesmanSchedule.ACTIVITY_ID
                || data.getScheduleId() != config.getScheduleId()) return List.of();
        SalesmanProgress progress;
        try { progress = SalesmanSchedule.progress(data); }
        catch (RuntimeException invalid) { return List.of(); }
        var conditions = new ArrayList<Integer>();
        if (SalesmanSchedule.canDeliver(data, config, rank, now)) conditions.add(5003001);
        if (progress.canTakeReward()) conditions.add(5003002);
        if (!progress.hasTalked(Math.min(day, 7))) conditions.add(5003100 + Math.min(day, 7));
        return List.copyOf(conditions);
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
        day = Math.min(day, 7);
        var status = progress.deliveredDays().contains(day) ? SalesmanStatusType.SALESMAN_STATUS_DELIVERED
                : progress.hasTalked(day) ? SalesmanStatusType.SALESMAN_STATUS_STARTED
                : SalesmanStatusType.SALESMAN_STATUS_UNSTARTED;
        return SalesmanActivityDetailInfo.newBuilder().setDayIndex(Math.min(day, 7)).setStatus(status)
                .setKOPLLPLDGGH(progress.deliveredDays().contains(day))
                .putAllSelectedRewardIdMap(progress.selectedRewardIdMap()).build();
    }
}
