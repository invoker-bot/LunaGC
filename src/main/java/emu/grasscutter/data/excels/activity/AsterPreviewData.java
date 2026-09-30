package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@Getter
@ResourceType(name = "AsterActivityPerviewExcelConfigData.json")
public final class AsterPreviewData extends GameResource {
    private int activityId;
    private int unlockLevel;
    private int specialRewardId;
    private int activityStayTime;
    private List<Integer> watcherList = List.of();

    @Override
    public int getId() {
        return activityId;
    }
}
