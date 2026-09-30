package emu.grasscutter.game.activity.aster;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

public final class AsterGather {
    private AsterGather() {}

    public static int collect(
            PlayerActivityData data,
            ActivityConfigItem config,
            int rank,
            long now,
            int groupId,
            int configId,
            Runnable save) {
        if (!AsterSchedule.phaseOpen(data, config, rank, now, 1))
            return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        var area = AsterFragments.areas().get(groupId);
        if (area == null || !area.configIds().contains(configId))
            return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
        synchronized (data) {
            var stage = emu.grasscutter.data.GameData.getAsterLittleDataMap().get(area.stageId());
            if (stage == null
                    || area.stageId() > AsterSchedule.currentStage(data)
                    || now < AsterSchedule.beginTime(config, stage.getOpenDay()))
                return Retcode.RET_ACTIVITY_CLOSE_VALUE;
            var progress = AsterSchedule.progress(data);
            var watcher = data.getWatcherInfoMap().get(area.watcherId());
            if (watcher == null) return Retcode.RET_SVR_ERROR_VALUE;
            if (!progress.collect(groupId, configId)) return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
            String before = data.getDetail();
            int beforeCount = watcher.getCurProgress();
            watcher.advance(1);
            data.setDetail(progress);
            try {
                save.run();
            } catch (RuntimeException failure) {
                data.setDetailJson(before);
                watcher.setCurProgress(beforeCount);
                throw failure;
            }
            return 0;
        }
    }
}
