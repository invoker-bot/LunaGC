package emu.grasscutter.game.activity.salesman;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.SalesmanData;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.util.*;
import java.util.function.*;

/** Original seven boxes: positions are independent choices, rewards are drawn without replacement. */
public final class SalesmanRewards {
    private SalesmanRewards() {}
    public record Result(int retcode, int rewardId) {}
    private static Result error(int retcode) { return new Result(retcode, 0); }
    public static boolean resourcesAvailable(SalesmanData source) {
        if (source == null || source.getNormalRewardIdList() == null || source.getSpecialRewardIdList() == null
                || source.getSpecialProbList() == null || source.getNormalRewardIdList().size() != 6
                || source.getSpecialRewardIdList().size() != 1 || source.getSpecialProbList().size() != 7) return false;
        var ids = new HashSet<Integer>(source.getNormalRewardIdList()); ids.addAll(source.getSpecialRewardIdList());
        if (!ids.equals(Set.of(470001,470002,470003,470004,470005,470006,470007))
                || !source.getSpecialRewardIdList().equals(List.of(470007))) return false;
        if (source.getSpecialProbList().stream().anyMatch(p -> p == null || !Double.isFinite(p) || p < 0 || p > 1)
                || source.getSpecialProbList().get(6) != 1.0) return false;
        return ids.stream().allMatch(id -> {
            var reward = GameData.getRewardDataMap().get(id.intValue());
            return reward != null && reward.getRewardItemList() != null && !reward.getRewardItemList().isEmpty()
                    && reward.getRewardItemList().stream().allMatch(item -> item != null && item.getId() > 0 && item.getCount() > 0);
        });
    }
    public static Result take(PlayerActivityData data, ActivityConfigItem config, int rank, long now,
                              int position, DoubleSupplier probability, IntUnaryOperator randomIndex,
                              IntUnaryOperator validate, IntConsumer grant, Runnable save) {
        if (data == null || config == null || SalesmanSchedule.dayIndex(config, now) == 0
                || config.getActivityType() != 3 || data.getActivityId() != SalesmanSchedule.ACTIVITY_ID
                || data.getScheduleId() != config.getScheduleId()) return error(Retcode.RET_ACTIVITY_CLOSE_VALUE);
        if (rank < 12) return error(Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE);
        synchronized (data) {
            SalesmanProgress progress;
            try { progress = SalesmanSchedule.progress(data); }
            catch (RuntimeException invalid) { return error(Retcode.RET_SVR_ERROR_VALUE); }
            if (progress.hasPendingReward() || progress.pendingDeliveryDay() != 0) return error(Retcode.RET_SVR_ERROR_VALUE);
            if (position < 1 || position > 7 || progress.selectedRewardIdMap().containsKey(position))
                return error(Retcode.RET_SALESMAN_POSITION_INVALID_VALUE);
            if (progress.remainingChances() <= 0) return error(Retcode.RET_SALESMAN_REWARD_COUNT_NOT_ENOUGH_VALUE);
            var source = SalesmanSchedule.source();
            if (!resourcesAvailable(source)) return error(Retcode.RET_SVR_ERROR_VALUE);
            var obtained = progress.selectedRewardIdMap().values();
            var special = source.getSpecialRewardIdList().stream().filter(id -> !obtained.contains(id)).toList();
            var candidates = source.getNormalRewardIdList().stream().filter(id -> !obtained.contains(id)).toList();
            if (!special.isEmpty()) {
                double chance = source.getSpecialProbList().get(obtained.size());
                double draw = chance >= 1 ? 0 : probability.getAsDouble();
                if (!Double.isFinite(draw) || draw < 0 || draw >= 1) return error(Retcode.RET_SVR_ERROR_VALUE);
                if (draw < chance) candidates = special;
            }
            if (candidates.isEmpty()) return error(Retcode.RET_SVR_ERROR_VALUE);
            int index = candidates.size() == 1 ? 0 : randomIndex.applyAsInt(candidates.size());
            if (index < 0 || index >= candidates.size()) return error(Retcode.RET_SVR_ERROR_VALUE);
            int rewardId = candidates.get(index);
            int result = validate.applyAsInt(rewardId);
            if (result != 0) return error(result);
            progress.beginReward(position, rewardId);
            data.setDetail(progress);
            // Both writes must be acknowledged. An unknown grant stays pending for manual reconciliation;
            // retrying it could duplicate a partially delivered reward after a crash or DB interruption.
            save.run();
            String reservation = data.getDetail();
            grant.accept(rewardId);
            progress.finishReward();
            data.setDetail(progress);
            try { save.run(); }
            catch (RuntimeException failed) { data.setDetailJson(reservation); throw failed; }
            return new Result(0, rewardId);
        }
    }
}
