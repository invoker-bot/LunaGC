package emu.grasscutter.game.tower;

import dev.morphia.annotations.Entity;
import java.util.*;

@Entity
public class TowerLevelRecord {
    /** floorId in config */
    private int floorId;
    /** LevelId - Stars */
    private Map<Integer, Integer> passedLevelMap;

    private int floorStarRewardProgress;

    /** LevelIds whose first-pass reward has already been handed out for this floor. */
    private Set<Integer> rewardedLevelIds;

    public TowerLevelRecord() {}

    public TowerLevelRecord(int floorId) {
        this.floorId = floorId;
        this.passedLevelMap = new HashMap<>();
        this.floorStarRewardProgress = 0;
    }

    /** Whether the first-pass reward for this chamber was already granted. */
    public boolean isLevelRewarded(int levelId) {
        return rewardedLevelIds != null && rewardedLevelIds.contains(levelId);
    }

    public void markLevelRewarded(int levelId) {
        if (rewardedLevelIds == null) rewardedLevelIds = new HashSet<>();
        rewardedLevelIds.add(levelId);
    }

    public TowerLevelRecord setLevelStars(int levelId, int stars) {
        passedLevelMap.put(levelId, stars);
        return this;
    }

    public int getLevelStars(int levelId) {
        return passedLevelMap.getOrDefault(levelId, 0);
    }

    public int getStarCount() {
        return passedLevelMap.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getFloorId() {
        return floorId;
    }

    public void setFloorId(int floorId) {
        this.floorId = floorId;
    }

    public Map<Integer, Integer> getPassedLevelMap() {
        return passedLevelMap;
    }

    public void setPassedLevelMap(Map<Integer, Integer> passedLevelMap) {
        this.passedLevelMap = passedLevelMap;
    }

    public int getFloorStarRewardProgress() {
        return floorStarRewardProgress;
    }

    public void setFloorStarRewardProgress(int floorStarRewardProgress) {
        this.floorStarRewardProgress = floorStarRewardProgress;
    }
}
