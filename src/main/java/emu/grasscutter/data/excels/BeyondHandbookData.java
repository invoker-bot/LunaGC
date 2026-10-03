package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@Getter
@ResourceType(name = "BeyondHandbookExcelConfigData.json")
public class BeyondHandbookData extends GameResource {
    @SerializedName(
            value = "groupId",
            alternate = {"KGEHGPFLIBH"})
    private int groupId;

    private int rewardId;
    private int score;
    private List<Integer> watcherIdList = List.of();

    @SerializedName(
            value = "logic",
            alternate = {"ILBBEFKCLGD"})
    private String logic;

    @Override
    public int getId() {
        return groupId;
    }
}
