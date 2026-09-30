package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;
import lombok.Getter;

@Getter
@ResourceType(name = "AsterMissionExcelConfigData.json")
public final class AsterMissionData extends GameResource {
    private int missionId;
    private int phase;
    private int watcherId;
    private int transPointId;
    private String tracePoint;

    @Override
    public int getId() {
        return missionId;
    }
}
