package emu.grasscutter.game.activity.aster;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.net.proto.AsterLittleDetailInfoOuterClass.AsterLittleDetailInfo;
import emu.grasscutter.net.proto.AsterLittleStageStateOuterClass.AsterLittleStageState;
import emu.grasscutter.utils.JsonUtils;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public final class AsterSchedule {
    public static final int ACTIVITY_ID = 2001;
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");

    private AsterSchedule() {}
    /** Historical BaseActivity::getBeginTimeByOpenDay uses daily reset after the first day. */
    public static long beginTime(ActivityConfigItem config, int day) {
        long begin = config.getBeginTime().getTime();
        if (day <= 1) return begin;
        return Instant.ofEpochMilli(begin)
                .atZone(CHINA)
                .minusHours(4)
                .toLocalDate()
                .plusDays(day - 1L)
                .atTime(4, 0)
                .atZone(CHINA)
                .toInstant()
                .toEpochMilli();
    }

    public static int dayIndex(ActivityConfigItem config, long now) {
        if (config == null || !config.isOpenAt(now)) return 0;
        var first =
                Instant.ofEpochMilli(config.getBeginTime().getTime())
                        .atZone(CHINA)
                        .minusHours(4)
                        .toLocalDate();
        var current = Instant.ofEpochMilli(now).atZone(CHINA).minusHours(4).toLocalDate();
        return Math.toIntExact(current.toEpochDay() - first.toEpochDay() + 1);
    }

    public static long contentCloseTime(ActivityConfigItem config) {
        var preview = GameData.getAsterPreviewDataMap().get(ACTIVITY_ID);
        if (preview == null || config == null || config.getCloseTime() == null) return 0;
        return Math.min(
                beginTime(config, preview.getActivityStayTime() + 1), config.getCloseTime().getTime());
    }

    public static AsterProgress progress(PlayerActivityData data) {
        var value =
                data.getDetail() == null || data.getDetail().isBlank()
                        ? new AsterProgress()
                        : JsonUtils.decode(data.getDetail(), AsterProgress.class);
        if (value == null || !value.isValid())
            throw new IllegalArgumentException("Invalid Aster progress");
        return value;
    }

    public static Set<Integer> stageWatchers(int stageId) {
        var stage = GameData.getAsterLittleDataMap().get(stageId);
        if (stage == null) return Set.of();
        return stage.getMissionVec().stream()
                .map(id -> GameData.getAsterMissionDataMap().get(id.intValue()))
                .filter(Objects::nonNull)
                .map(m -> m.getWatcherId())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean phaseOpen(
            PlayerActivityData data, ActivityConfigItem config, int rank, long now, int phase) {
        var preview = GameData.getAsterPreviewDataMap().get(ACTIVITY_ID);
        var chapter =
                GameData.getAsterStageDataMap().values().stream()
                        .filter(c -> c.getActivityId() == ACTIVITY_ID && c.getChapterId() == phase)
                        .findFirst()
                        .orElse(null);
        return data != null
                && config != null
                && config.getActivityId() == ACTIVITY_ID
                && data.getActivityId() == ACTIVITY_ID
                && data.getScheduleId() == config.getScheduleId()
                && preview != null
                && rank >= preview.getUnlockLevel()
                && config.isOpenAt(now)
                && now < contentCloseTime(config)
                && chapter != null
                && now >= beginTime(config, chapter.getOpenday());
    }

    public static int currentStage(PlayerActivityData data) {
        for (int stage : List.of(1, 2)) {
            if (stageWatchers(stage).stream()
                    .anyMatch(
                            id ->
                                    data.getWatcherInfoMap().get(id) == null
                                            || !data.getWatcherInfoMap().get(id).isFinished())) return stage;
        }
        return 2;
    }

    public static AsterLittleDetailInfo littleInfo(
            PlayerActivityData data, ActivityConfigItem config, int rank, long now) {
        boolean open = phaseOpen(data, config, rank, now, 1);
        int stageId = open ? currentStage(data) : 0;
        var stage = GameData.getAsterLittleDataMap().get(stageId);
        long stageTime = stage == null ? 0 : beginTime(config, stage.getOpenDay());
        var state =
                !open
                        ? AsterLittleStageState.AsterLittleStageState_ASTER_LITTLE_STAGE_NONE
                        : now < stageTime
                                ? AsterLittleStageState.AsterLittleStageState_ASTER_LITTLE_STAGE_UNSTARTED
                                : stageWatchers(stageId).stream()
                                                .allMatch(id -> data.getWatcherInfoMap().get(id).isFinished())
                                        ? AsterLittleStageState.AsterLittleStageState_ASTER_LITTLE_STAGE_FINISHED
                                        : AsterLittleStageState.AsterLittleStageState_ASTER_LITTLE_STAGE_STARTED;
        return AsterLittleDetailInfo.newBuilder()
                .setBeginTime((int) (beginTime(config, 1) / 1000))
                .setIsOpen(open)
                .setStageId(stageId)
                .setStageBeginTime((int) (stageTime / 1000))
                .setStageState(state)
                .build();
    }

    public static boolean resourcesAvailable() {
        var preview = GameData.getAsterPreviewDataMap().get(ACTIVITY_ID);
        if (preview == null
                || preview.getUnlockLevel() != 20
                || preview.getActivityStayTime() != 14
                || !preview.getWatcherList().equals(List.of(1200130, 1200131, 1200132))) return false;
        for (int id = 1; id <= 3; id++) {
            var chapter = GameData.getAsterStageDataMap().get(id);
            if (chapter == null
                    || chapter.getActivityId() != ACTIVITY_ID
                    || chapter.getChapterId() != id
                    || chapter.getOpenday() != new int[] {1, 3, 8}[id - 1]) return false;
        }
        for (int stageId : List.of(1, 2)) {
            var stage = GameData.getAsterLittleDataMap().get(stageId);
            if (stage == null
                    || stage.getOpenDay() != stageId
                    || stage.getMissionVec().size() != 3
                    || stageWatchers(stageId).size() != 3) return false;
            for (int missionId : stage.getMissionVec()) {
                var mission = GameData.getAsterMissionDataMap().get(missionId);
                if (mission == null || mission.getPhase() != 1) return false;
            }
        }
        return GameData.getAsterMissionDataMap().size() == 12
                && AsterFragments.areas().values().stream()
                        .allMatch(
                                a -> {
                                    var watcher = GameData.getActivityWatcherDataMap().get(a.watcherId());
                                    var reward =
                                            watcher == null
                                                    ? null
                                                    : GameData.getRewardDataMap().get(watcher.getRewardID());
                                    return watcher != null
                                            && !watcher.isDisuse()
                                            && watcher.getProgress() == 7
                                            && reward != null
                                            && reward.getRewardItemList() != null
                                            && !reward.getRewardItemList().isEmpty()
                                            && reward.getRewardItemList().stream()
                                                    .allMatch(p -> p.getId() > 0 && p.getCount() > 0)
                                            && stageWatchers(a.stageId()).contains(a.watcherId())
                                            && watcher
                                                    .getTriggerConfig()
                                                    .getParamList()
                                                    .equals(List.of(String.valueOf(a.groupId())));
                                });
    }
}
