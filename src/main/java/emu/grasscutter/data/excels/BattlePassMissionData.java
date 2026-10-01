package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.BattlePassMissionOuterClass.BattlePassMission.MissionStatus;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;

@ResourceType(name = {"BattlePassMissionExcelConfigData.json"})
@Getter
public class BattlePassMissionData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int addPoint;
    private int scheduleId;
    private int progress;
    private boolean isDisuse;
    private TriggerConfig triggerConfig;
    private BattlePassMissionRefreshType refreshType;

    private transient Set<Integer> mainParams = Set.of();

    public WatcherTriggerType getTriggerType() {
        return WatcherTriggerType.getTypeByName(getTriggerName());
    }

    public String getTriggerName() {
        return triggerConfig == null ? null : triggerConfig.getTriggerType();
    }

    public boolean isCycleRefresh() {
        return getRefreshType() == null
                || getRefreshType() == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_DAILY
                || getRefreshType()
                        == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_CYCLE_CROSS_SCHEDULE
                || getRefreshType() == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_CYCLE;
    }

    public boolean isValidRefreshType() {
        return !isDisuse
                && getTriggerName() != null
                && (getScheduleId() == 0 || getScheduleId() == BattlePassScheduleData.currentId());
    }

    @Override
    public void onLoad() {
        mainParams = Set.of();
        if (this.getTriggerConfig() != null
                && getTriggerConfig().getParamList() != null
                && getTriggerConfig().getParamList().length > 0) {
            var params = getTriggerConfig().getParamList()[0];
            if ((params != null) && !params.isEmpty()) {
                this.mainParams =
                        Arrays.stream(params.split("[:;,]"))
                                .filter(s -> s.matches("\\d+"))
                                .map(Integer::parseInt)
                                .collect(Collectors.toSet());
            }
        }
    }

    public emu.grasscutter.net.proto.BattlePassMissionOuterClass.BattlePassMission toProto() {
        var protoBuilder =
                emu.grasscutter.net.proto.BattlePassMissionOuterClass.BattlePassMission.newBuilder();

        protoBuilder
                .setMissionId(getId())
                .setTotalProgress(this.getProgress())
                .setRewardBattlePassPoint(this.getAddPoint())
                .setMissionStatus(MissionStatus.MISSION_UNFINISHED)
                .setMissionType(this.getRefreshType() == null ? 0 : this.getRefreshType().getValue());

        return protoBuilder.build();
    }

    @Getter
    public static class TriggerConfig {
        // New resource names must survive even before their numeric watcher enum is known.
        private String triggerType;
        private String[] paramList;
    }
}
