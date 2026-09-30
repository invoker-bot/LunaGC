package emu.grasscutter.game.activity.aster;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.AsterActivityDetailInfoOuterClass.AsterActivityDetailInfo;
import emu.grasscutter.net.proto.AsterLargeDetailInfoOuterClass.AsterLargeDetailInfo;
import emu.grasscutter.net.proto.AsterMidDetailInfoOuterClass.AsterMidDetailInfo;
import java.util.*;

@GameActivity(ActivityType.NEW_ACTIVITY_ASTER)
public final class AsterActivityHandler extends ActivityHandler {
    @Override
    public void initWatchers(Map<WatcherTriggerType, ConstructorAccess<?>> watcherTypes) {
        super.initWatchers(watcherTypes);
        if (getActivityConfigItem().getActivityId() != AsterSchedule.ACTIVITY_ID
                || !AsterSchedule.resourcesAvailable())
            throw new IllegalArgumentException("未归的熄星阶段、任务或地图资源缺失");
        for (var mission : GameData.getAsterMissionDataMap().values()) {
            var meta = GameData.getActivityWatcherDataMap().get(mission.getWatcherId());
            if (meta == null || meta.isDisuse())
                throw new IllegalArgumentException("未归的熄星任务资源缺失: " + mission.getWatcherId());
            var watcher =
                    new DefaultWatcher(); // Gather/reward progress only enters through validated server
            // interactions.
            watcher.setWatcherId(meta.getId());
            watcher.setActivityWatcherData(meta);
            watcher.setActivityHandler(this);
            getWatchersMap()
                    .computeIfAbsent(
                            meta.getTriggerConfig().getWatcherTriggerType(), key -> new ArrayList<>())
                    .add(watcher);
        }
    }

    @Override
    public void onInitPlayerActivityData(PlayerActivityData data) {
        data.setDetail(new AsterProgress());
    }

    @Override
    public boolean onLoadPlayerActivityData(PlayerActivityData data) {
        synchronized (data) {
            AsterSchedule.progress(data);
            boolean changed = false;
            for (var watcher : getWatchersMap().values().stream().flatMap(Collection::stream).toList()) {
                if (!data.getWatcherInfoMap().containsKey(watcher.getWatcherId())) {
                    data.getWatcherInfoMap()
                            .put(watcher.getWatcherId(), PlayerActivityData.WatcherInfo.init(watcher));
                    changed = true;
                }
            }
            return changed;
        }
    }

    @Override
    public void onProtoBuild(PlayerActivityData data, ActivityInfo.Builder info) {
        if (data == null) buildInfo(null, info);
        else
            synchronized (data) {
                buildInfo(data, info);
            }
    }

    private void buildInfo(PlayerActivityData data, ActivityInfo.Builder info) {
        long now = System.currentTimeMillis();
        int rank = data != null && data.getPlayer() != null ? data.getPlayer().getLevel() : 0;
        var detail =
                AsterActivityDetailInfo.newBuilder()
                        .setAsterLittle(AsterSchedule.littleInfo(data, getActivityConfigItem(), rank, now))
                        .setContentCloseTime(
                                (int) (AsterSchedule.contentCloseTime(getActivityConfigItem()) / 1000))
                        .setIsContentClosed(now >= AsterSchedule.contentCloseTime(getActivityConfigItem()));
        // Mid/large are deliberately not advertised as open until their actual scene gameplay is
        // restored.
        detail
                .setAsterMid(
                        AsterMidDetailInfo.newBuilder()
                                .setBeginTime((int) (AsterSchedule.beginTime(getActivityConfigItem(), 3) / 1000)))
                .setAsterLarge(
                        AsterLargeDetailInfo.newBuilder()
                                .setBeginTime((int) (AsterSchedule.beginTime(getActivityConfigItem(), 8) / 1000)));
        info.setAsterInfo(detail);
        info.clearWatcherInfoList();
        if (data != null)
            data.getWatcherInfoMap().values().stream()
                    .filter(
                            w -> {
                                var mission =
                                        GameData.getAsterMissionDataMap().values().stream()
                                                .filter(m -> m.getWatcherId() == w.getWatcherId())
                                                .findFirst()
                                                .orElse(null);
                                return mission == null
                                        || mission.getPhase() == 1
                                                && AsterSchedule.phaseOpen(data, getActivityConfigItem(), rank, now, 1);
                            })
                    .map(PlayerActivityData.WatcherInfo::toProto)
                    .forEach(info::addWatcherInfoList);
    }
}
