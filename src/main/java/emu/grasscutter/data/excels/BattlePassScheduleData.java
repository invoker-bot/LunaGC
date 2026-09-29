package emu.grasscutter.data.excels;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@ResourceType(name = "BattlePassScheduleExcelConfigData.json")
@Getter
public class BattlePassScheduleData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private List<Integer> cycleList;
    private int cyclePointUpperLimit;

    // Schedules are keyed by version, so 7.0 is 7000.
    public static int currentId() {
        int wanted = GameConstants.VERSION_PARTS[0] * 1000 + GameConstants.VERSION_PARTS[1] * 100;
        int best = 0;
        for (int id : GameData.getBattlePassScheduleDataMap().keySet()) {
            if (id <= wanted && id > best) best = id;
        }
        // Keep the existing schedule when the bundle has no entry for this client version.
        return best != 0 ? best : 2700;
    }
}
