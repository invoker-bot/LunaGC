package emu.grasscutter.game.activity.salesman;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

/** Original Liben talks, persisted separately from the quest system. */
public final class SalesmanTalk {
    private SalesmanTalk() {}
    public static boolean isSalesmanTalk(int talkId) { return talkId >= 4100101 && talkId <= 4100114; }
    public static int complete(PlayerActivityData data, ActivityConfigItem config, int talkId,
                               int rank, long now, Runnable save) {
        int rawDay = SalesmanSchedule.dayIndex(config, now);
        if (rawDay < 1 || data == null || data.getActivityId() != SalesmanSchedule.ACTIVITY_ID
                || data.getScheduleId() != config.getScheduleId()) return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        if (rank < 12) return Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE;
        if (!isSalesmanTalk(talkId)) return Retcode.RET_NOT_CURRENT_TALK_VALUE;
        int day = Math.min(rawDay, 7);
        synchronized (data) {
            SalesmanProgress progress;
            try { progress = SalesmanSchedule.progress(data); }
            catch (RuntimeException invalid) { return Retcode.RET_SVR_ERROR_VALUE; }
            if (talkId <= 4100107) {
                if (talkId != 4100100 + day) return Retcode.RET_NOT_CURRENT_TALK_VALUE;
                if (progress.talk(day)) {
                    String before = data.getDetail();
                    data.setDetail(progress);
                    try { save.run(); }
                    catch (RuntimeException failed) { data.setDetailJson(before); throw failed; }
                }
                return 0;
            }
            if (!progress.hasTalked(day)) return Retcode.RET_NOT_CURRENT_TALK_VALUE;
            boolean canDeliver = SalesmanSchedule.canDeliver(data, config, rank, now);
            boolean canReward = progress.canTakeReward();
            boolean allowed = switch (talkId) {
                case 4100109 -> canDeliver;
                case 4100111 -> !canDeliver;
                case 4100110 -> canReward;
                case 4100114 -> !canReward;
                default -> true;
            };
            return allowed ? 0 : Retcode.RET_NOT_CURRENT_TALK_VALUE;
        }
    }
}
