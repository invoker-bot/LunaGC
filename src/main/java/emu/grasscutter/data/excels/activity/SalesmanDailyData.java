package emu.grasscutter.data.excels.activity;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import java.util.*;
import lombok.Getter;

@ResourceType(name = "ActivitySalesmanDailyExcelConfigData.json")
@Getter
public final class SalesmanDailyData extends GameResource {
    private int dailyConfigId;
    private String tracePosition;
    private long npcTalkTextMapHash;
    private long clusPosTextMapHash;
    @SerializedName("IntroTextMapHash") private long introTextMapHash;
    private List<ItemParamData> costItemList = List.of();

    @Override public int getId() { return dailyConfigId; }

    @Override public void onLoad() {
        // Blank resource slots are padding. A partially populated slot is invalid.
        if (costItemList == null || costItemList.stream().anyMatch(item -> item == null
                || (item.getId() == 0) != (item.getCount() == 0)
                || item.getId() < 0 || item.getCount() < 0)) {
            throw new IllegalArgumentException("Invalid Salesman costs: " + dailyConfigId);
        }
        costItemList = costItemList.stream().filter(item -> item.getId() > 0).toList();
    }
}
