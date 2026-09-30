package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;
import emu.grasscutter.game.world.Position;
import java.util.List;
import lombok.Getter;

@ResourceType(name = "MpPlayGroupExcelConfigData.json")
@Getter
public class MpPlayGroupData extends GameResource {
    private int playId;
    private List<Float> centerPosList;
    private List<Integer> groupList;
    private int prepareTime;
    private int bornGroupId;
    private int bornConfigId;

    @Override public int getId() { return playId; }

    public Position centerPosition() {
        if (centerPosList == null || centerPosList.size() != 3)
            throw new IllegalStateException("MP play position missing: " + playId);
        return new Position(centerPosList.get(0), centerPosList.get(1), centerPosList.get(2));
    }
}
