package emu.grasscutter.game.activity.aster;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.salesman.SalesmanRewardDelivery;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.util.function.*;

/** Acknowledged reservation prevents a partially granted reward from being replayed. */
public final class AsterWatcherRewards {
    private AsterWatcherRewards() {}

    public static int take(
            PlayerActivityData data,
            ActivityConfigItem config,
            int rank,
            long now,
            int id,
            IntUnaryOperator validate,
            IntConsumer grant,
            Runnable save) {
        if (data == null
                || config == null
                || !config.isActiveAt(now)
                || config.getActivityId() != AsterSchedule.ACTIVITY_ID
                || data.getActivityId() != AsterSchedule.ACTIVITY_ID
                || config.getScheduleId() != data.getScheduleId()) return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        if (rank < 20) return Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE;
        // Phase two rewards are enabled only when that gameplay has its own validated result path.
        if (id < 1200101 || id > 1200106) return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
        synchronized (data) {
            var watcher = data.getWatcherInfoMap().get(id);
            var meta = GameData.getActivityWatcherDataMap().get(id);
            if (watcher == null || meta == null || meta.isDisuse())
                return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
            if (watcher.isTakenReward()) return Retcode.RET_ACTIVITY_WATCHER_REWARD_TAKEN_VALUE;
            if (!watcher.isFinished()) return Retcode.RET_ACTIVITY_WATCHER_REWARD_NOT_FINISHED_VALUE;
            var progress = AsterSchedule.progress(data);
            if (progress.pendingWatcherReward() != 0) return Retcode.RET_SVR_ERROR_VALUE;
            int code = validate.applyAsInt(meta.getRewardID());
            if (code != 0) return code;
            String original = data.getDetail();
            progress.beginWatcherReward(id);
            data.setDetail(progress);
            try {
                save.run();
            } catch (RuntimeException failure) {
                data.setDetailJson(original);
                throw failure;
            }
            String reservation = data.getDetail();
            try {
                grant.accept(meta.getRewardID());
            } catch (RuntimeException failure) {
                emu.grasscutter.Grasscutter.getLogger()
                        .error(
                                "Aster reward needs reconciliation: UID {}, schedule {}, watcher {}",
                                data.getUid(),
                                data.getScheduleId(),
                                id,
                                failure);
                throw failure;
            }
            watcher.setTakenReward(true);
            progress.finishWatcherReward();
            data.setDetail(progress);
            try {
                save.run();
            } catch (RuntimeException failure) {
                watcher.setTakenReward(false);
                data.setDetailJson(reservation);
                emu.grasscutter.Grasscutter.getLogger()
                        .error(
                                "Aster granted reward acknowledgement failed: UID {}, schedule {}, watcher {}",
                                data.getUid(),
                                data.getScheduleId(),
                                id,
                                failure);
                throw failure;
            }
            return 0;
        }
    }

    public static int take(PlayerActivityData data, int id) {
        var player = data.getPlayer();
        var config = ActivityManager.getScheduleActivityConfigMap().get(data.getScheduleId());
        if (player == null
                || player.getActivityManager().getPlayerActivityDataMap().get(AsterSchedule.ACTIVITY_ID)
                        != data) return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        synchronized (player.getInventory()) {
            return take(
                    data,
                    config,
                    player.getLevel(),
                    System.currentTimeMillis(),
                    id,
                    reward -> SalesmanRewardDelivery.validate(player, reward),
                    reward -> SalesmanRewardDelivery.grant(player, reward, ActionReason.ActivityWatcher),
                    data::saveSync);
        }
    }
}
