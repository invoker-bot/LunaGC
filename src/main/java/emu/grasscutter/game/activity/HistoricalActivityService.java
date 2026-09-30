package emu.grasscutter.game.activity;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.game.activity.salesman.SalesmanSchedule;
import emu.grasscutter.utils.FileUtils;
import java.io.IOException;
import java.util.*;

public final class HistoricalActivityService {
    private HistoricalActivityService() {}

    public static List<HistoricalActivity> catalogue() throws IOException {
        var entries = DataLoader.loadList("ActivityHistory.json", HistoricalActivity.class);
        entries.sort(Comparator.comparingLong((HistoricalActivity item) -> {
            long time = HistoricalActivity.epoch(item.getOfficialBeginTime());
            return time == 0 ? Long.MAX_VALUE : time;
        }).thenComparingInt(HistoricalActivity::getActivityId));
        return entries;
    }

    public static Map<String, Object> list() throws IOException {
        var configs = ActivityManager.getScheduleActivityConfigMap().values();
        var rows = new ArrayList<Map<String, Object>>();
        long now = System.currentTimeMillis();
        for (var event : catalogue()) {
            var config = configs.stream().filter(item -> item.getActivityId() == event.getActivityId()
                    && Objects.equals(item.getHistoryKey(), event.getKey())).findFirst().orElse(null);
            var row = new LinkedHashMap<String, Object>();
            row.put("key", event.getKey()); row.put("activityId", event.getActivityId());
            row.put("name", event.getName()); row.put("version", event.getVersion());
            row.put("typeName", event.getTypeName()); row.put("sourceUrl", event.getSourceUrl());
            row.put("officialBeginTime", event.getOfficialBeginTime());
            row.put("officialEndTime", event.getOfficialEndTime());
            row.put("officialBeginDescription", event.getOfficialBeginDescription());
            row.put("officialEndDescription", event.getOfficialEndDescription());
            boolean resource = GameData.getActivityDataMap().containsKey(event.getActivityId());
            row.put("resourceAvailable", resource);
            row.put("canEnable", resource && event.hasKnownType());
            row.put("active", config != null && config.isActiveAt(now));
            row.put("disabled", config != null && config.isDisabled());
            row.put("scheduleId", config == null ? 0 : config.getScheduleId());
            row.put("beginTime", config == null ? 0 : config.getBeginTime().getTime() / 1000);
            row.put("endTime", config == null ? 0 : config.getEndTime().getTime() / 1000);
            row.put("restoration", event.getActivityId() == 5001 || event.getActivityId() == 5003 ? "还原中" : "待还原");
            long titleCount = event.getActivityId() == 5001 ? GameData.getMpPlayWatcherDataMap().values().stream()
                    .filter(watcher -> watcher.getMpPlayId() == 1 && !watcher.isDisuse()).count() : 0;
            row.put("settlementTitleCount", titleCount);
            var salesman = event.getActivityId() == 5003 ? SalesmanSchedule.source() : null;
            long salesmanDays = salesman == null ? 0 : salesman.getDailyConfigIdList().stream()
                    .filter(id -> GameData.getSalesmanDailyDataMap().containsKey(id.intValue())).count();
            var salesmanRewards = new HashSet<Integer>();
            if (salesman != null) {
                salesmanRewards.addAll(salesman.getNormalRewardIdList());
                salesmanRewards.addAll(salesman.getSpecialRewardIdList());
            }
            long salesmanRewardCount = salesmanRewards.stream().filter(id -> GameData.getRewardDataMap().containsKey(id.intValue())).count();
            row.put("salesmanDayCount", salesmanDays);
            row.put("salesmanRewardCount", salesmanRewardCount);
            row.put("salesmanResourcesReady", salesman != null && SalesmanSchedule.resourcesAvailable());
            row.put("restorationNote", event.getActivityId() == 5001
                    ? "已接入场景、13 项任务、7.1 通知、整队匹配、个人树脂领奖及 " + titleCount + " 项结算称号；完整多人挑战与称号显示待实机验证"
                    : event.getActivityId() == 5003 ? "已加载 " + salesmanDays + " 天材料、" + salesmanRewardCount
                        + " 组首期奖励，接入凌晨 4 点换日和 7.1 活动详情；立本场景、材料提交与开匣发奖仍在接入"
                    : !resource ? "本资源版本缺少活动记录，暂不能开启"
                    : !event.hasKnownType() ? "活动类型编号尚未核实，暂不能开启"
                    : "可管理活动排期；专属玩法尚未完成客户端验证");
            rows.add(row);
        }
        return Map.of("retcode", 0, "activities", rows, "order", "release-ascending");
    }

    public static synchronized Map<String, Object> update(String key, String action, int days) throws IOException {
        var event = catalogue().stream().filter(item -> Objects.equals(item.getKey(), key))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("活动记录不存在"));
        var current = DataLoader.loadList("ActivityConfig.json", ActivityConfigItem.class);
        var next = ActivityScheduleStore.plan(current, event, action, days, System.currentTimeMillis());
        var prepared = ActivityManager.prepareConfiguration(next);
        ActivityScheduleStore.write(FileUtils.getDataUserPath("ActivityConfig.json"), next);
        ActivityManager.installConfiguration(prepared);
        int synced = 0;
        int failed = 0;
        for (var player : Grasscutter.getGameServer().getPlayers().values()) {
            if (player.getActivityManager() == null) continue;
            try { player.getActivityManager().refreshActivities(); synced++; }
            catch (Exception e) {
                failed++;
                Grasscutter.getLogger().warn("Activity sync failed for UID {}", player.getUid(), e);
            }
        }
        var selected = next.stream().filter(item -> item.getActivityId() == event.getActivityId()).findFirst();
        return Map.of("retcode", 0, "activityId", event.getActivityId(), "action", action,
                "syncedPlayers", synced, "syncFailures", failed,
                "scheduleId", selected.map(ActivityConfigItem::getScheduleId).orElse(0),
                "endTime", selected.map(item -> item.getEndTime().getTime() / 1000).orElse(0L));
    }
}
