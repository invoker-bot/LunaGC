package emu.grasscutter.game.beyond;

import dev.morphia.annotations.Entity;
import emu.grasscutter.data.GameData;
import emu.grasscutter.net.proto.BeyondOwnedCostumeOuterClass.BeyondOwnedCostume;
import java.util.*;

/** Permanent purchased ownership, independent of Teyvat inventory and avatar costumes. */
@Entity(useDiscriminator = false)
public final class BeyondCloset {
    private Set<Integer> ownedCostumes = new HashSet<>();

    public static List<Integer> resolveCostumes(int itemId) {
        var material = GameData.getBydMaterialDataMap().get(itemId);
        if (material == null) return List.of();
        var ids = new TreeSet<Integer>();
        for (var use : material.getItemUse()) {
            if (use.getUseParam().isEmpty() || use.getUseParam().get(0).isBlank()) continue;
            int id;
            try {
                id = Integer.parseInt(use.getUseParam().get(0));
            } catch (NumberFormatException e) {
                return List.of();
            }
            if ("BYD_MATERIAL_USE_GAIN_COSTUME".equals(use.getUseOp())) {
                if (!GameData.getBeyondCostumeDataMap().containsKey(id)) return List.of();
                ids.add(id);
            } else if ("BYD_MATERIAL_USE_GAIN_COSTUME_SUIT".equals(use.getUseOp())) {
                GameData.getBeyondCostumeDataMap().values().stream()
                        .filter(c -> c.getSuitId() == id)
                        .forEach(c -> ids.add(c.getId()));
            } else if (use.getUseOp() != null && !use.getUseOp().isBlank()) return List.of();
        }
        return List.copyOf(ids);
    }

    public synchronized boolean canGrant(List<Integer> costumes) {
        return !costumes.isEmpty()
                && costumes.stream().allMatch(id -> id > 0)
                && costumes.stream().anyMatch(id -> !ownedCostumes.contains(id));
    }

    public synchronized List<BeyondOwnedCostume> grant(List<Integer> costumes) {
        if (!canGrant(costumes)) return List.of();
        var added = new ArrayList<BeyondOwnedCostume>();
        for (int id : costumes) if (ownedCostumes.add(id)) added.add(toProto(id));
        return List.copyOf(added);
    }

    public synchronized List<BeyondOwnedCostume> toProto() {
        return ownedCostumes.stream().sorted().map(BeyondCloset::toProto).toList();
    }

    private static BeyondOwnedCostume toProto(int id) {
        // Expiry 0 is permanent ownership in this list. Unknown parameters remain unset.
        return BeyondOwnedCostume.newBuilder().setCostumeId(id).build();
    }
}
