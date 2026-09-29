package emu.grasscutter.game.dailytask;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DailyTaskDataNotifyOuterClass.DailyTaskDataNotify;
import emu.grasscutter.net.proto.DailyTaskInfoOuterClass.DailyTaskInfo;
import emu.grasscutter.net.proto.DailyTaskProgressNotifyOuterClass.DailyTaskProgressNotify;
import emu.grasscutter.net.proto.WorldOwnerDailyTaskNotifyOuterClass.WorldOwnerDailyTaskNotify;
import java.util.List;

/** Builds daily task messages with the current protocol schema. */
public final class DailyTaskProto {
    private DailyTaskProto() {}

    public static final int PROGRESS_NOTIFY_CMD = PacketOpcodes.DailyTaskProgressNotify;
    public static final int WORLD_OWNER_NOTIFY_CMD = PacketOpcodes.WorldOwnerDailyTaskNotify;
    public static final int DATA_NOTIFY_CMD = PacketOpcodes.DailyTaskDataNotify;

    private static DailyTaskInfo message(DailyTask task) {
        return DailyTaskInfo.newBuilder()
                .setIsFinished(task.isFinished())
                .setRewardId(task.getRewardId())
                .setDailyTaskId(task.getDailyTaskId())
                .setFinishProgress(task.getFinishProgress())
                .setProgress(task.getProgress())
                .build();
    }

    public static byte[] info(DailyTask task) {
        return message(task).toByteArray();
    }

    public static byte[] worldOwnerNotify(List<DailyTask> tasks, int cityId, int finished) {
        var notify = WorldOwnerDailyTaskNotify.newBuilder()
                .setFilterCityId(cityId)
                .setFinishedDailyTaskNum(finished);
        tasks.stream().map(DailyTaskProto::message).forEach(notify::addTaskList);
        return notify.build().toByteArray();
    }

    public static byte[] progressNotify(DailyTask task) {
        return DailyTaskProgressNotify.newBuilder().setInfo(message(task)).build().toByteArray();
    }

    public static byte[] dataNotify(int finished, int scoreRewardId, boolean taken) {
        return DailyTaskDataNotify.newBuilder()
                .setFinishedNum(finished)
                .setScoreRewardId(scoreRewardId)
                .setIsTakenScoreReward(taken)
                .build()
                .toByteArray();
    }
}
