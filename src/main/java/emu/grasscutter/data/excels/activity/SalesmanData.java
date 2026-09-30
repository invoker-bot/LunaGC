package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;
import java.util.*;
import lombok.Getter;

/** Client schedules are source definitions; GM replay schedule IDs are independent. */
@ResourceType(name = "ActivitySalesmanExcelConfigData.json")
@Getter
public final class SalesmanData extends GameResource {
    private int scheduleId;
    private List<Integer> dailyConfigIdList = List.of();
    private List<Integer> normalRewardIdList = List.of();
    private List<Integer> specialRewardIdList = List.of();
    private List<Double> specialProbList = List.of();

    @Override public int getId() { return scheduleId; }
}
