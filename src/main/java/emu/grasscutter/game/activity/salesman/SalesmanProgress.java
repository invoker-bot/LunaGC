package emu.grasscutter.game.activity.salesman;

import java.util.*;

/** Per-player, per-replay data stored in PlayerActivityData.detail. */
public final class SalesmanProgress {
    private Set<Integer> deliveredDays = new HashSet<>();
    private Map<Integer, Integer> selectedRewardIdMap = new HashMap<>();
    private Set<Integer> talkedDays = new HashSet<>();
    private int pendingDeliveryDay;

    public Set<Integer> deliveredDays() { return Set.copyOf(deliveredDays); }
    public Map<Integer, Integer> selectedRewardIdMap() { return Map.copyOf(selectedRewardIdMap); }
    public int remainingChances() { return deliveredDays.size() - selectedRewardIdMap.size(); }
    public boolean hasTalked() { return !talkedDays.isEmpty(); }
    public boolean hasTalked(int day) { return talkedDays.contains(day); }
    public boolean talk(int day) { return day >= 1 && day <= 7 && talkedDays.add(day); }
    public int pendingDeliveryDay() { return pendingDeliveryDay; }
    public void beginDelivery(int day) {
        if (day < 1 || day > 7 || pendingDeliveryDay != 0 || deliveredDays.contains(day))
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
                && selectedRewardIdMap.size() <= deliveredDays.size()
                && new HashSet<>(selectedRewardIdMap.values()).size() == selectedRewardIdMap.size()
                && selectedRewardIdMap.entrySet().stream().allMatch(entry -> entry.getKey() != null
                        && entry.getKey() >= 0 && entry.getValue() != null
                        && entry.getValue() >= 470001 && entry.getValue() <= 470007);
    }
    public boolean deliver(int day) {
        return day >= 1 && day <= 7 && deliveredDays.add(day);
    }
}
