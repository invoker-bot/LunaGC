package emu.grasscutter.game.activity.salesman;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.utils.JsonUtils;
import java.time.*;
import java.util.*;

/** The original 1.0 event (5003), separate from subsequent SALESMAN_MP events. */
public final class SalesmanSchedule {
    public static final int ACTIVITY_ID = 5003;
    public static final int SOURCE_SCHEDULE_ID = 5003001;
    private SalesmanSchedule() {}

    public static SalesmanData source() {
        return GameData.getSalesmanDataMap().get(SOURCE_SCHEDULE_ID);
    }

    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");

    public static int dayIndex(ActivityConfigItem config, long now) {
        if (config == null || config.getActivityId() != ACTIVITY_ID || !config.isOpenAt(now)) return 0;
        var first = Instant.ofEpochMilli(config.getOpenTime().getTime()).atZone(CHINA).minusHours(4).toLocalDate();
        var current = Instant.ofEpochMilli(now).atZone(CHINA).minusHours(4).toLocalDate();
        return Math.toIntExact(current.toEpochDay() - first.toEpochDay() + 1);
    }

    public static SalesmanDailyData daily(ActivityConfigItem config, long now) {
        var source = source();
        int day = dayIndex(config, now);
        if (source == null || day <= 0 || source.getDailyConfigIdList().isEmpty()) return null;
        int index = Math.min(day, source.getDailyConfigIdList().size()) - 1;
        return GameData.getSalesmanDailyDataMap().get(source.getDailyConfigIdList().get(index).intValue());
    }

    public static SalesmanProgress progress(PlayerActivityData data) {
        var progress = data.getDetail() == null || data.getDetail().isBlank() ? new SalesmanProgress()
                : JsonUtils.decode(data.getDetail(), SalesmanProgress.class);
        if (progress == null || !progress.isValid()) throw new IllegalArgumentException("Invalid Salesman progress");
        return progress;
    }

    public static boolean canDeliver(PlayerActivityData data, ActivityConfigItem config,
                                     int adventureRank, long now) {
        int day = dayIndex(config, now);
        if (data == null || config == null || adventureRank < 12 || day < 1 || day > 7
                || data.getActivityId() != ACTIVITY_ID || data.getScheduleId() != config.getScheduleId()) return false;
        var daily = daily(config, now);
        if (daily == null || daily.getCostItemList().isEmpty()) return false;
        try { var progress = progress(data); return progress.pendingDeliveryDay() == 0 && !progress.hasPendingReward() && !progress.deliveredDays().contains(day); }
        catch (RuntimeException invalid) { return false; }
    }

    /** Validate the original schedule rather than looking up a generated replay ID in Excel. */
    public static boolean resourcesAvailable() {
        var source = source();
        if (source == null || !source.getDailyConfigIdList().equals(List.of(1,2,3,4,5,6,7))) return false;
        for (int id : source.getDailyConfigIdList()) {
            var daily = GameData.getSalesmanDailyDataMap().get(id);
            if (daily == null || daily.getCostItemList().isEmpty() || daily.getTracePosition() == null) return false;
        }
        var rewards = new HashSet<Integer>(source.getNormalRewardIdList());
        rewards.addAll(source.getSpecialRewardIdList());
        return rewards.size() == 7 && SalesmanRewards.resourcesAvailable(source);
    }
}
