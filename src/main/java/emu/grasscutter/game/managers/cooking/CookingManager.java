package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.WatcherTriggerType;
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
        int avatar = req.getAssistAvatar();

        // The 7.0 client dump renamed both cooking quantities, but kept their roles:
        // KLACBPCPCMJ (field 2) is how many dishes the client asked for -- 1 for a manual cook,
        // the slider value for an auto cook -- and JJPABEHGMCH (field 15) is the QTE result,
        // whose 1/2/3 select the recipe's 奇怪/普通/美味 output tiers.
        int count = req.getKLACBPCPCMJ();
        int quality = req.getJJPABEHGMCH();

        // A cook always has to yield at least one dish; the client omits the field entirely
        // only when it is broken, so never hand out a zero-count stack.
        if (count < 1) {
            count = 1;
        }

        // Trace the raw request once per recipe, and on every auto cook (count != 1), so the
        // field mapping above stays verifiable from the logs.  DDACKLBMIKL/OLLOPKLIIAC are two
        // 7.0-only context fields that are still unidentified.
        if (loggedReqLayout.add(recipeId) || count != 1) {
            Grasscutter.getLogger()
                    .debug(
                            "PlayerCookReq recipe={} assist={} count={} quality={} f5={} f12={}",
                            recipeId,
                            avatar,
                            count,
                            quality,
                            req.getDDACKLBMIKL(),
                            req.getOLLOPKLIIAC());
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

        // Get result item information.  A quality of 0 means the client sent no QTE result,
        // which only an auto cook does; those resolve to the top tier.
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

        // Increase player proficiency, if this was a manual perfect cook.  Auto cooks also
        // report the top tier, but only recipes whose proficiency is already capped offer auto
        // cooking, so the +1 they take here is harmless -- it clamps straight back to the cap.
        if (quality == 0 || quality == MANUAL_PERFECT_COOK_QUALITY) {
            proficiency = Math.min(proficiency + 1, recipeData.getMaxProficiency());
            this.player.getUnlockedRecipies().put(recipeId, proficiency);
        }

        // Send response.
        this.player.getBattlePassManager().triggerMission(WatcherTriggerType.TRIGGER_DO_COOK, 1, count);
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
