package emu.grasscutter.game.activity.salesman;

import java.util.*;

/** Per-player, per-replay data stored in PlayerActivityData.detail. */
public final class SalesmanProgress {
    private Set<Integer> deliveredDays = new HashSet<>();
    private Map<Integer, Integer> selectedRewardIdMap = new HashMap<>();
    private Set<Integer> talkedDays = new HashSet<>();
    private int pendingDeliveryDay;
    private int pendingRewardPosition;
    private int pendingRewardId;

    public Set<Integer> deliveredDays() { return Set.copyOf(deliveredDays); }
    public Map<Integer, Integer> selectedRewardIdMap() { return Map.copyOf(selectedRewardIdMap); }
    public int remainingChances() { return deliveredDays.size() - selectedRewardIdMap.size(); }
    public boolean hasPendingReward() { return pendingRewardPosition != 0; }
    public boolean canTakeReward() { return pendingDeliveryDay == 0 && !hasPendingReward() && remainingChances() > 0; }
    public void beginReward(int position, int rewardId) {
        if (!canTakeReward() || position < 1 || position > 7 || selectedRewardIdMap.containsKey(position)
                || rewardId < 470001 || rewardId > 470007 || selectedRewardIdMap.containsValue(rewardId))
            throw new IllegalStateException("Salesman reward is unavailable");
        pendingRewardPosition = position;
        pendingRewardId = rewardId;
    }
    public void finishReward() {
        if (!hasPendingReward()) throw new IllegalStateException("Missing Salesman reward reservation");
        selectedRewardIdMap.put(pendingRewardPosition, pendingRewardId);
        pendingRewardPosition = 0;
        pendingRewardId = 0;
    }
    public boolean hasTalked() { return !talkedDays.isEmpty(); }
    public boolean hasTalked(int day) { return talkedDays.contains(day); }
    public boolean talk(int day) { return day >= 1 && day <= 7 && talkedDays.add(day); }
    public int pendingDeliveryDay() { return pendingDeliveryDay; }
    public void beginDelivery(int day) {
        if (day < 1 || day > 7 || pendingDeliveryDay != 0 || hasPendingReward() || deliveredDays.contains(day))
            throw new IllegalStateException("Salesman delivery is unavailable");
        pendingDeliveryDay = day;
    }
    public void finishDelivery(int day, boolean paid) {
        if (pendingDeliveryDay != day) throw new IllegalStateException("Different Salesman delivery reservation");
        if (paid) deliveredDays.add(day);
        pendingDeliveryDay = 0;
    }
    public boolean isValid() {
        return deliveredDays != null && selectedRewardIdMap != null && talkedDays != null
                && deliveredDays.stream().allMatch(day -> day != null && day >= 1 && day <= 7)
                && talkedDays.stream().allMatch(day -> day != null && day >= 1 && day <= 7)
                && pendingDeliveryDay >= 0 && pendingDeliveryDay <= 7 && !deliveredDays.contains(pendingDeliveryDay)
                && ((pendingRewardPosition == 0 && pendingRewardId == 0)
                    || (pendingRewardPosition >= 1 && pendingRewardPosition <= 7 && pendingRewardId >= 470001
                        && pendingRewardId <= 470007 && pendingDeliveryDay == 0 && remainingChances() > 0
                        && !selectedRewardIdMap.containsKey(pendingRewardPosition) && !selectedRewardIdMap.containsValue(pendingRewardId)))
                && selectedRewardIdMap.size() <= deliveredDays.size()
                && new HashSet<>(selectedRewardIdMap.values()).size() == selectedRewardIdMap.size()
                && selectedRewardIdMap.entrySet().stream().allMatch(entry -> entry.getKey() != null
                        && entry.getKey() >= 1 && entry.getKey() <= 7 && entry.getValue() != null
                        && entry.getValue() >= 470001 && entry.getValue() <= 470007);
    }
    public boolean deliver(int day) {
        return day >= 1 && day <= 7 && deliveredDays.add(day);
    }
}
