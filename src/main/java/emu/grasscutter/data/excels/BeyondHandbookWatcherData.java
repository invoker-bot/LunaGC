package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@Getter
@ResourceType(name = "BeyondHandbookWatcherExcelConfigData.json")
public class BeyondHandbookWatcherData extends GameResource {
    private int id;
    private int progress;
    private Trigger triggerConfig;

    @Getter
    public static class Trigger {
        private String triggerType;
        private List<String> paramList = List.of();
    }
}
