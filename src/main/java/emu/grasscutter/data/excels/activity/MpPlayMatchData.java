package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "MpPlayMatchExcelConfigData.json")
@Getter
public class MpPlayMatchData extends GameResource {
    private int id, minPlayers, maxPlayers;
    private String playType;
    private boolean isAutoMatch;
    @Override public int getId() { return id; }
}
