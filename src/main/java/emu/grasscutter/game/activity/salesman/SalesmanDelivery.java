package emu.grasscutter.game.activity.salesman;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.util.List;
import java.util.function.Predicate;

/** Serializes a daily exchange against persisted replay progress and inventory payment. */
public final class SalesmanDelivery {
    private SalesmanDelivery() {}

    public static int deliver(PlayerActivityData data, ActivityConfigItem config, int rank, long now,
                              Predicate<List<ItemParamData>> pay, Runnable save) {
        int day = SalesmanSchedule.dayIndex(config, now);
        if (data == null || config == null || day < 1 || day > 7
                || data.getActivityId() != SalesmanSchedule.ACTIVITY_ID || data.getScheduleId() != config.getScheduleId())
            return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        if (rank < 12) return Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE;
        synchronized (data) {
            SalesmanProgress progress;
            try { progress = SalesmanSchedule.progress(data); }
            catch (RuntimeException invalid) { return Retcode.RET_SVR_ERROR_VALUE; }
            if (progress.pendingDeliveryDay() != 0) return Retcode.RET_SVR_ERROR_VALUE;
            if (progress.deliveredDays().contains(day)) return Retcode.RET_SALESMAN_ALREADY_DELIVERED_VALUE;
            var daily = SalesmanSchedule.daily(config, now);
            if (daily == null || daily.getCostItemList().isEmpty()) return Retcode.RET_SVR_ERROR_VALUE;
            // A durable reservation prevents replaying a debit whose result became unknown after a crash.
            // Never turn a pending reservation into a chance without a confirmed payment.
            progress.beginDelivery(day);
            data.setDetail(progress);
            save.run();
            boolean paid = pay.test(List.copyOf(daily.getCostItemList()));
            progress.finishDelivery(day, paid);
            data.setDetail(progress);
            save.run();
            return paid ? 0 : Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE;
        }
    }
}
