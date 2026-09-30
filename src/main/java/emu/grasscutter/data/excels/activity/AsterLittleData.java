package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@Getter
@ResourceType(name = "AsterLittleExcelConfigData.json")
public final class AsterLittleData extends GameResource {
    private int stageId;
    private int openDay;
    private List<Integer> missionVec = List.of();
    private List<Integer> nextStageIdVec = List.of();

    @Override
    public int getId() {
        return stageId;
    }
}
