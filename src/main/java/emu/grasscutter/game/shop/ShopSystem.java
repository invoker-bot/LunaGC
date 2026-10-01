package emu.grasscutter.game.shop;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ShopGoodsData;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import lombok.Getter;

public class ShopSystem extends BaseGameSystem {
    private static final int REFRESH_HOUR = 4; // In GMT+8 server
    private static final String TIME_ZONE = "Asia/Shanghai"; // GMT+8 Timezone
    private final Int2ObjectMap<List<ShopInfo>> shopData;
    private final Int2ObjectMap<List<ItemParamData>> shopChestData;

    @Getter
    private final ShopCatalog catalog =
            new ShopCatalog(FileUtils.getDataUserPath("ShopOverrides.json"));

    @Getter private final FreeStore freeStore = new FreeStore(catalog);

    @Getter private final ArtifactShop artifactShop = new ArtifactShop();

    public ShopSystem(GameServer server) {
        super(server);
        this.shopData = new Int2ObjectOpenHashMap<>();
        this.shopChestData = new Int2ObjectOpenHashMap<>();
        this.load();
    }

    public static int getShopNextRefreshTime(ShopInfo shopInfo) {
        return switch (shopInfo.getShopRefreshType()) {
            case SHOP_REFRESH_DAILY -> Utils.getNextTimestampOfThisHour(
                    REFRESH_HOUR, TIME_ZONE, shopInfo.getShopRefreshParam());
            case SHOP_REFRESH_WEEKLY -> Utils.getNextTimestampOfThisHourInNextWeek(
                    REFRESH_HOUR, TIME_ZONE, shopInfo.getShopRefreshParam());
            case SHOP_REFRESH_MONTHLY -> Utils.getNextTimestampOfThisHourInNextMonth(
                    REFRESH_HOUR, TIME_ZONE, shopInfo.getShopRefreshParam());
            default -> 0;
        };
    }

    public Int2ObjectMap<List<ShopInfo>> getShopData() {
        return catalog.active();
    }

    public List<ItemParamData> getShopChestData(int chestId) {
        return this.shopChestData.get(chestId);
    }

    private void loadShop() {
        shopData.clear();
        try {
            List<ShopTable> banners = DataLoader.loadList("Shop.json", ShopTable.class);
            if (banners.size() > 0) {
                for (ShopTable shopTable : banners) {
                    shopTable.getItems().forEach(ShopInfo::removeVirtualCosts);
                    shopData.put(shopTable.getShopId(), shopTable.getItems());
                }
                Grasscutter.getLogger().debug("Shop data successfully loaded.");
            } else {
                Grasscutter.getLogger().error("Unable to load shop data. Shop data size is 0.");
            }

        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load curated shop data", e);
        }
        try {
            if (GAME_OPTIONS.enableShopItems) {
                // Shop.json is a curated snapshot, not the whole catalogue: it trails the excel by
                // hundreds of goods in the city shops and misses 53 shops outright. Fill what it
                // lacks - a shop it defines gets only the goodsIds it does not already list, so a
                // curated price or level override is never duplicated or overwritten.
                GameData.getShopGoodsDataEntries()
                        .forEach(
                                (k, v) -> {
                                    int shopId = k.intValue();
                                    // The fork snapshot replaces rotating Paimon goods with unrelated
                                    // characters/weapons. Use the official rows; GM overrides apply later.
                                    if (shopId == ShopType.SHOP_TYPE_PAIMON.shopTypeId) {
                                        shopData.put(
                                                shopId,
                                                v.stream()
                                                        .map(ShopInfo::new)
                                                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
                                        return;
                                    }
                                    var items = shopData.computeIfAbsent(shopId, x -> new ArrayList<>());
                                    var known = new HashMap<Integer, ShopInfo>();
                                    for (ShopInfo curated : items) known.put(curated.getGoodsId(), curated);

                                    for (ShopGoodsData sgd : v) {
                                        var curated = known.get(sgd.getGoodsId());
                                        if (curated == null) {
                                            items.add(new ShopInfo(sgd));
                                            continue;
                                        }
                                        // A curated good that costs nothing at all is not a giveaway, it
                                        // is a price someone scrubbed: Shop.json ships shop 902 (the
                                        // package shop) and 1052 with every good free. Charge nothing and
                                        // payItems waves any count through, so this was the other half of
                                        // the purchase exploit. Only currency is restored - costItemList
                                        // is left alone because the fork prices some shops in materials on
                                        // purpose, and those goods already fail the all-free test.
                                        if (curated.getScoin() == 0
                                                && curated.getHcoin() == 0
                                                && curated.getMcoin() == 0
                                                && (curated.getCostItemList() == null
                                                        || curated.getCostItemList().isEmpty())) {
                                            curated.setScoin(sgd.getCostScoin());
                                            curated.setHcoin(sgd.getCostHcoin());
                                            curated.setMcoin(sgd.getCostMcoin());
                                        }
                                    }
                                });
                Grasscutter.getLogger().debug("Shop data filled with excel goods.");
            }
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load shop data.", e);
        }
    }

    private void loadShopChest() {
        shopChestData.clear();
        try {
            Map<Integer, String> chestMap =
                    DataLoader.loadMap("ShopChest.v2.json", Integer.class, String.class);
            chestMap.forEach(
                    (chestId, itemStr) -> {
                        if (itemStr.isEmpty()) return;
                        var entries = itemStr.split(",");
                        var list = new ArrayList<ItemParamData>(entries.length);
                        for (var entry : entries) {
                            var idAndCount = entry.split(":");
                            int id = Integer.parseInt(idAndCount[0]);
                            int count = Integer.parseInt(idAndCount[1]);
                            list.add(new ItemParamData(id, count));
                        }
                        this.shopChestData.put((int) chestId, list);
                    });
            Grasscutter.getLogger().debug("Loaded " + chestMap.size() + " ShopChest entries.");
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load ShopChest data.", e);
        }
    }

    public synchronized void load() {
        loadShop();
        loadShopChest();
        loadArtifactShop();
        freeStore.load();
    }

    /**
     * Lists the 5-star artifacts. Called on its own after the resources finish loading, because the
     * shop system is built before them and has no item data to work from yet.
     */
    public synchronized void loadArtifactShop() {
        this.artifactShop.install(shopData);
        catalog.replaceBase(shopData);
    }

    public GameServer getServer() {
        return server;
    }
}
