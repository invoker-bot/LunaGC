package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;
import lombok.Getter;

@Getter
@ResourceType(name = "AsterStageExcelConfigData.json")
public final class AsterStageData extends GameResource {
    private int id;
    private int activityId;
    private int chapterId;
    private int openday;
    private int openQuestId;

    @Override
    public int getId() {
        return id;
    }
}
