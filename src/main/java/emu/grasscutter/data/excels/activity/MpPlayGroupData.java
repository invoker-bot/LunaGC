package emu.grasscutter.data.excels.activity;

import com.google.gson.annotations.SerializedName;
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
    @SerializedName(value = "centerRadius", alternate = {"radius"})
    private int radius;
    private int bornGroupId;
    private int bornConfigId;
    private int rewardGroupId;
    private int rewardConfigId;
    @SerializedName(value = "resinCost", alternate = {"PPMJCCCMJGE"})
    private int resinCost;
    private List<Reward> rewardVec;

    @Getter
    public static class Reward {
        @SerializedName(value = "dropID", alternate = {"dropId"})
        private int dropId;
        private int rewardPreview;
    }

    /** Archived events retain their highest configured reward tier in newer world levels. */
    public Reward rewardForWorldLevel(int worldLevel) {
        if (worldLevel < 0 || rewardVec == null || rewardVec.isEmpty())
            throw new IllegalStateException("MP play reward level missing: " + playId + "/" + worldLevel);
        var reward = rewardVec.get(Math.min(worldLevel, rewardVec.size() - 1));
        if (reward == null || reward.dropId <= 0 || reward.rewardPreview <= 0)
            throw new IllegalStateException("MP play reward table invalid: " + playId + "/" + worldLevel);
        return reward;
    }

    @Override public int getId() { return playId; }

    public Position centerPosition() {
        if (centerPosList == null || centerPosList.size() != 3)
            throw new IllegalStateException("MP play position missing: " + playId);
        return new Position(centerPosList.get(0), centerPosList.get(1), centerPosList.get(2));
    }
}
