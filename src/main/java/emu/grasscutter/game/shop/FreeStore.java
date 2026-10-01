package emu.grasscutter.game.shop;

import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.ShopCardProductOuterClass.ShopCardProduct;
import emu.grasscutter.net.proto.ShopMcoinProductOuterClass.ShopMcoinProduct;
import emu.grasscutter.net.proto.ShopOuterClass.Shop;
import emu.grasscutter.utils.*;
import java.io.IOException;
import java.util.*;

/** Resource-backed cash products. No payment gateway: every supported product costs zero. */
public class FreeStore {
    public record Product(
            String key,
            String productId,
            String name,
            String kind,
            int amount,
            int bonus,
            int firstBonus,
            int days,
            int dailyAmount,
            int maxDays,
            int playType,
            String priceTier) {}

    private final ShopCatalog catalog;
    private volatile List<Product> products = List.of();
    private volatile Map<String, Product> aliases = Map.of();

    public FreeStore(ShopCatalog catalog) {
        this.catalog = catalog;
    }

    private static int number(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsInt() : 0;
    }

    private static List<JsonObject> rows(String file) throws IOException {
        return JsonUtils.loadToList(FileUtils.getExcelPath(file + ".json"), JsonObject.class);
    }

    public void load() {
        try {
            var ids = rows("ProductIdConfigData");
            var all = new ArrayList<Product>();
            var index = new HashMap<String, Product>();
            for (var table :
                    List.of(
                            "ProductCardDetailConfigData",
                            "ProductMcoinDetailConfigData",
                            "ProductPlayDetailConfigData")) {
                for (var row : rows(table)) {
                    int config = number(row, "configId");
                    var matching = ids.stream().filter(i -> number(i, "configId") == config).toList();
                    if (matching.isEmpty()) continue;
                    var primary =
                            matching.stream()
                                    .filter(
                                            i ->
                                                    i.has("isInternal")
                                                            && i.get("isInternal").getAsBoolean()
                                                            && !i.get("productId").getAsString().startsWith("cloud"))
                                    .findFirst()
                                    .orElse(matching.get(0));
                    String id = primary.get("productId").getAsString();
                    Product product;
                    if (table.contains("Card")) {
                        if (!row.get("cardProductType").getAsString().equals("CARD_PRODUCT_TYPE_HCOIN"))
                            continue;
                        product =
                                new Product(
                                        "card:" + config,
                                        id,
                                        "空月祝福（小月卡）",
                                        "card",
                                        number(row, "mcoinBase"),
                                        0,
                                        0,
                                        number(row, "days"),
                                        number(row, "hcoinPerDay"),
                                        number(row, "totalLimitDays"),
                                        0,
                                        row.get("priceTier").getAsString());
                    } else if (table.contains("Mcoin")) {
                        int amount = number(row, "mcoinNum");
                        product =
                                new Product(
                                        "crystals:" + config,
                                        id,
                                        "创世结晶 × " + amount,
                                        "crystals",
                                        amount,
                                        number(row, "mcoinNonFirst"),
                                        number(row, "mcoinFirst"),
                                        0,
                                        0,
                                        0,
                                        0,
                                        row.get("priceTier").getAsString());
                        all.add(
                                new Product(
                                        "primogems:" + config,
                                        "",
                                        "原石 × " + amount,
                                        "primogems",
                                        amount,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        ""));
                    } else {
                        String type = row.get("play_type").getAsString();
                        int play =
                                switch (type) {
                                    case "PRODUCT_PLAY_TYPE_BATTLE_PASS_NORMAL" -> 1;
                                    case "PRODUCT_PLAY_TYPE_BATTLE_PASS_EXTRA" -> 2;
                                    case "PRODUCT_PLAY_TYPE_BATTLE_PASS_UPGRADE" -> 3;
                                    case "PRODUCT_PLAY_TYPE_BATTLE_PASS_NORMAL_DISCOUNT" -> 4;
                                    case "PRODUCT_PLAY_TYPE_BATTLE_PASS_EXTRA_DISCOUNT" -> 5;
                                    default -> 0;
                                };
                        if (play == 0) continue;
                        String name = play == 3 ? "珍珠之歌升级" : play == 2 || play == 5 ? "珍珠之歌（大月卡）" : "珍珠纪行（大月卡）";
                        product =
                                new Product(
                                        "pass:" + config,
                                        id,
                                        name + (play >= 4 ? " · 折扣款" : ""),
                                        "pass",
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        play,
                                        row.get("priceTier").getAsString());
                    }
                    all.add(product);
                    for (var match : matching) index.put(match.get("productId").getAsString(), product);
                }
            }
            products = List.copyOf(all);
            aliases = Map.copyOf(index);
        } catch (IOException | RuntimeException e) {
            Grasscutter.getLogger().error("Unable to load free store products", e);
        }
    }

    public List<Product> products() {
        return products;
    }

    /** Preserve resource tier names so the native shop can match its product configuration. */
    public Map<String, Object> priceTiers(boolean overseas) {
        String currency = overseas ? "USD" : "CNY";
        var price =
                Map.of(
                        "enable",
                        1,
                        "country",
                        overseas ? "US" : "CN",
                        "currency",
                        currency,
                        "price",
                        "0",
                        "symbol",
                        overseas ? "$" : "￥",
                        "amount_display",
                        "0.00");
        var tiers =
                products.stream()
                        .filter(this::enabled)
                        .map(Product::priceTier)
                        .filter(t -> !t.isEmpty())
                        .distinct()
                        .sorted()
                        .map(t -> Map.of("tier_id", t, "t_price", List.of(price)))
                        .toList();
        return Map.of("suggest_currency", currency, "tiers", tiers, "price_tier_version", "1");
    }

    public Product productForPlayType(int type) {
        return products.stream()
                .filter(p -> p.playType == type && catalog.productEnabled(p.key))
                .findFirst()
                .orElse(null);
    }

    public boolean enabled(Product p) {
        return catalog.productEnabled(p.key);
    }

    public void setEnabled(String key, boolean enabled) throws IOException {
        find(key);
        catalog.setProductEnabled(key, enabled);
    }

    private Product find(String key) {
        return products.stream()
                .filter(p -> p.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("充值商品不存在。"));
    }

    public Product forProductId(String id) {
        return aliases.get(id);
    }

    public String purchaseById(Player player, String id, String kind) {
        var p = aliases.get(id);
        if (p == null || !p.kind.equals(kind)) throw new IllegalArgumentException("客户端商品与资源不匹配。");
        return purchase(player, p.key, UUID.randomUUID().toString());
    }
    /** An operator's receipt makes retrying a timed-out request safe. */
    public String purchase(Player player, String key, String receipt) {
        var p = find(key);
        if (receipt == null || !receipt.matches("[a-zA-Z0-9-]{16,80}"))
            throw new IllegalArgumentException("购买凭据无效。");
        synchronized (player) {
            var receipts = player.getFreePurchaseReceipts();
            if (receipts.containsKey(receipt)) {
                if (!receipts.get(receipt).equals(key)) throw new IllegalArgumentException("凭据已用于其他商品。");
                persist(player);
                return "该次购买已经完成，未重复发放。";
            }
            if (!catalog.productEnabled(key)) throw new IllegalArgumentException("商品已下架。");
            switch (p.kind) {
                case "card" -> {
                    if (!player.rechargeMoonCard(p.days, p.amount, p.dailyAmount, p.maxDays))
                        throw new IllegalArgumentException("月卡剩余天数不能超过 " + p.maxDays + " 天。");
                }
                case "pass" -> {
                    var pass = player.getBattlePassManager();
                    if (pass == null) throw new IllegalArgumentException("纪行尚未加载。");
                    if (p.playType == 3 && !pass.isPaid()) throw new IllegalArgumentException("请先解锁珍珠纪行。");
                    if (!pass.unlockPaid(p.playType == 2 || p.playType == 3 || p.playType == 5))
                        throw new IllegalArgumentException("当前纪行已解锁该档位。");
                }
                case "primogems", "crystals" -> {
                    int bought = player.getFreeProductPurchases().getOrDefault(p.key, 0);
                    int amount =
                            p.amount + (p.kind.equals("crystals") ? bought == 0 ? p.firstBonus : p.bonus : 0);
                    if ((long) (p.kind.equals("primogems") ? player.getPrimogems() : player.getCrystals())
                                    + amount
                            > Integer.MAX_VALUE) throw new IllegalArgumentException("货币数量已达到上限。");
                    if (!player
                            .getInventory()
                            .addItem(
                                    new GameItem(p.kind.equals("primogems") ? 201 : 203, amount), ActionReason.Shop))
                        throw new IllegalArgumentException("物品发放失败。");
                }
                default -> throw new IllegalArgumentException("未支持的商品类型。");
            }
            player.getFreeProductPurchases().merge(p.key, 1, Integer::sum);
            receipts.put(receipt, key);
            while (receipts.size() > 512) receipts.remove(receipts.keySet().iterator().next());
            persist(player);
            return "已免费购买：" + p.name + "（价格 0）。";
        }
    }

    protected void persist(Player player) {
        if (player.getBattlePassManager() != null)
            DatabaseHelper.saveGameSync(player.getBattlePassManager());
        DatabaseHelper.saveGameSync(player);
    }

    public void addProducts(Shop.Builder shop, Player player) {
        for (var p : products) {
            if (!catalog.productEnabled(p.key)) continue;
            if (shop.getShopType() == 902 && p.kind.equals("card")) {
                shop.addCardProductList(
                        ShopCardProduct.newBuilder()
                                .setProductId(p.productId)
                                .setPriceTier(p.priceTier)
                                .setMcoinBase(p.amount)
                                .setHcoinPerDay(p.dailyAmount)
                                .setDays(p.days)
                                .setRemainRewardDays(player.getMoonCardRemainDays())
                                .setCardProductType(1));
            } else if (shop.getShopType() == 903 && p.kind.equals("crystals")) {
                shop.addMcoinProductList(
                        ShopMcoinProduct.newBuilder()
                                .setProductId(p.productId)
                                .setPriceTier(p.priceTier)
                                .setMcoinBase(p.amount)
                                .setMcoinFirst(p.firstBonus)
                                .setMcoinNonFirst(p.bonus)
                                .setBoughtNum(player.getFreeProductPurchases().getOrDefault(p.key, 0)));
            }
        }
    }
}
