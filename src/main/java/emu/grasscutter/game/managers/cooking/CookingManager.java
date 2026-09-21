package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.CookRecipeDataOuterClass;
import emu.grasscutter.net.proto.PlayerCookArgsReqOuterClass.PlayerCookArgsReq;
import emu.grasscutter.net.proto.PlayerCookReqOuterClass.PlayerCookReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import io.netty.util.internal.ThreadLocalRandom;
import java.util.*;

public class CookingManager extends BasePlayerManager {
    private static final int MANUAL_PERFECT_COOK_QUALITY = 3;
    private static Set<Integer> defaultUnlockedRecipies;

    public CookingManager(Player player) {
        super(player);
    }

    public static void initialize() {
        // The GameServer constructor calls this before ResourceLoader.loadAll() runs, so the
        // excel map is still empty at that point and nothing would be collected here.  The set
        // is therefore rebuilt on demand until it actually holds recipe ids -- see
        // getDefaultUnlockedRecipies().
        computeDefaultUnlockedRecipies();
    }

    /**
     * Recipe ids every account starts with.
     *
     * <p>Recomputed on first use (and again while it is still empty) because the game data is
     * loaded after the {@code GameServer} constructor runs {@link #initialize()}, so an eager
     * scan there sees an empty {@link GameData#getCookRecipeDataMap()} and every player would
     * otherwise begin with an empty recipe list -- the client then shows no recipes at all.
     */
    public static synchronized Set<Integer> getDefaultUnlockedRecipies() {
        if (defaultUnlockedRecipies == null || defaultUnlockedRecipies.isEmpty()) {
            computeDefaultUnlockedRecipies();
        }
        return defaultUnlockedRecipies;
    }

    private static synchronized void computeDefaultUnlockedRecipies() {
        var map = GameData.getCookRecipeDataMap();
        if (map == null || map.isEmpty()) {
            // Nothing loaded yet; hand back an empty set rather than null (callers iterate it
            // during login) and let the isEmpty() check above retry on the next call.
            defaultUnlockedRecipies = new HashSet<>();
            return;
        }
        defaultUnlockedRecipies = defaultUnlockedRecipeIds(map.values());
        Grasscutter.getLogger()
                .info(
                        "Loaded {} default unlocked cooking recipes ({} recipes known).",
                        defaultUnlockedRecipies.size(),
                        map.size());
    }

    /** Select the ids of the recipes flagged default-unlocked in the excel data. */
    public static Set<Integer> defaultUnlockedRecipeIds(
            Collection<emu.grasscutter.data.excels.CookRecipeData> recipes) {
        var ids = new HashSet<Integer>();
        for (var recipe : recipes) {
            if (recipe.isDefaultUnlocked()) {
                ids.add(recipe.getId());
            }
        }
        return ids;
    }

    /********************
     * Unlocking for recipies.
     ********************/
    public boolean unlockRecipe(int id) {
        if (this.player.getUnlockedRecipies().containsKey(id)) {
            return false; // Recipe already unlocked
        }
        // Tell the client that this blueprint is now unlocked and add the unlocked item to the player.
        this.player.getUnlockedRecipies().put(id, 0);
        this.player.sendPacket(new PacketCookRecipeDataNotify(id));

        return true;
    }

    /********************
     * Perform cooking.
     ********************/
    private double getSpecialtyChance(ItemData cookedItem) {
        // Chances taken from the Wiki.
        return switch (cookedItem.getRankLevel()) {
            case 1 -> 0.25;
            case 2 -> 0.2;
            case 3 -> 0.15;
            default -> 0;
        };
    }

    /** Recipe ids whose PlayerCookReq field layout has already been dumped this session. */
    private static final Set<Integer> loggedReqLayout = new HashSet<>();

    public void handlePlayerCookReq(PlayerCookReq req) {
        // Get info from the request.
        int recipeId = req.getRecipeId();
        // qte_quality and cook_count are unnamed in the 7.0 dump and PlayerCookReq has four
        // indistinguishable uint32s, so neither can be read. Assume a single perfect dish.
        int quality = 0;
        int count = 1;
        int avatar = req.getAssistAvatar();

        // Record which of the four unnamed uint32s actually carries the QTE quality and the
        // cook count, once per recipe, so the hardcoded values above can be retired.
        if (loggedReqLayout.add(recipeId)) {
            Grasscutter.getLogger()
                    .debug(
                            "PlayerCookReq recipe={} assist={} f2={} f5={} f8={} f12={} f15={}",
                            recipeId,
                            avatar,
                            req.getKLACBPCPCMJ(),
                            req.getDDACKLBMIKL(),
                            req.getRecipeId(),
                            req.getOLLOPKLIIAC(),
                            req.getJJPABEHGMCH());
        }

        // Get recipe data.
        var recipeData = GameData.getCookRecipeDataMap().get(recipeId);
        if (recipeData == null) {
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }

        // Get proficiency for player.
        int proficiency = this.player.getUnlockedRecipies().getOrDefault(recipeId, 0);

        // Try consuming materials.
        boolean success =
                player.getInventory().payItems(recipeData.getInputVec(), count, ActionReason.Cook);
        if (!success) {
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return; // ref keeps going here, which would create the dish without paying for it
        }

        // Get result item information.
        int qualityIndex = quality == 0 ? 2 : quality - 1;

        ItemParamData resultParam = recipeData.getQualityOutputVec().get(qualityIndex);
        // Some recipes only fill one or two of the three quality slots; a meal cannot be
        // crafted from an empty {id:0,count:0} slot, so fall back to the lowest filled tier.
        if (resultParam == null || resultParam.getItemId() <= 0) {
            for (ItemParamData candidate : recipeData.getQualityOutputVec()) {
                if (candidate != null && candidate.getItemId() > 0) {
                    resultParam = candidate;
                    break;
                }
            }
        }
        ItemData resultItemData =
                resultParam == null || resultParam.getItemId() <= 0
                        ? null
                        : GameData.getItemDataMap().get(resultParam.getItemId());
        if (resultItemData == null) {
            // The recipe's output item is not in the item table, so there is nothing to hand over.
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }

        // Handle character's specialties.
        int specialtyCount = 0;
        double specialtyChance = this.getSpecialtyChance(resultItemData);

        var bonusData = GameData.getCookBonusDataMap().get(avatar);
        if (bonusData != null && recipeId == bonusData.getRecipeId()) {
            // Roll for specialy replacements.
            for (int i = 0; i < count; i++) {
                if (ThreadLocalRandom.current().nextDouble() <= specialtyChance) {
                    specialtyCount++;
                }
            }
        }

        // Obtain results.
        List<GameItem> cookResults = new ArrayList<>();

        int normalCount = count - specialtyCount;
        GameItem cookResultNormal = new GameItem(resultItemData, resultParam.getCount() * normalCount);
        cookResults.add(cookResultNormal);
        this.player.getInventory().addItem(cookResultNormal);

        if (specialtyCount > 0) {
            ItemData specialtyItemData = GameData.getItemDataMap().get(bonusData.getReplacementItemId());
            GameItem cookResultSpecialty =
                    new GameItem(specialtyItemData, resultParam.getCount() * specialtyCount);
            cookResults.add(cookResultSpecialty);
            this.player.getInventory().addItem(cookResultSpecialty);
        }

        // Increase player proficiency, if this was a manual perfect cook.
        // qte_quality is unreadable in the 7.0 dump, but qteQualityWeightVec is [0,0,100] for
        // all 281 recipes -- the QTE can only yield the top tier -- so a cook that resolved to
        // that tier counts as perfect, which covers both the manual and the auto path.
        // if (quality == MANUAL_PERFECT_COOK_QUALITY) {
        if (quality == 0 || quality == MANUAL_PERFECT_COOK_QUALITY) {
            proficiency = Math.min(proficiency + 1, recipeData.getMaxProficiency());
            this.player.getUnlockedRecipies().put(recipeId, proficiency);
        }

        // Send response.
        this.player.sendPacket(
                new PacketPlayerCookRsp(cookResults, quality, count, recipeId, proficiency));
    }

    /********************
     * Cooking arguments.
     ********************/
    public void handleCookArgsReq(PlayerCookArgsReq req) {
        this.player.sendPacket(new PacketPlayerCookArgsRsp(req.getRecipeId()));
    }

    /********************
     * Notify unlocked recipies.
     ********************/
    private void addDefaultUnlocked() {
        // Get recipies that are already unlocked.
        var unlockedRecipies = this.player.getUnlockedRecipies();

        // Get recipies that should be unlocked by default but aren't.
        var additionalRecipies = new HashSet<>(getDefaultUnlockedRecipies());
        additionalRecipies.removeAll(unlockedRecipies.keySet());

        // Add them to the player.
        for (int id : additionalRecipies) {
            unlockedRecipies.put(id, 0);
        }
    }

    public void sendCookDataNotify() {
        // Default unlocked recipes to player if they don't have them yet.
        this.addDefaultUnlocked();

        // Get unlocked recipes.
        var unlockedRecipes = this.player.getUnlockedRecipies();

        // Construct CookRecipeData protos.
        List<CookRecipeDataOuterClass.CookRecipeData> data = new ArrayList<>();
        unlockedRecipes.forEach(
                (recipeId, proficiency) ->
                        data.add(
                                CookRecipeDataOuterClass.CookRecipeData.newBuilder()
                                        .setRecipeId(recipeId)
                                        .setProficiency(proficiency)
                                        .build()));

        // Send packet.
        this.player.sendPacket(new PacketCookDataNotify(data));
    }
}
