package emu.grasscutter.game.shop;

import emu.grasscutter.data.GameData;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persistent operator overrides, applied after the resource and generated catalogues. */
public final class ShopCatalog {
    public static final int CUSTOM_ID_BASE = 150_000_000;

    public record Entry(
            int shopType, boolean enabled, boolean custom, boolean modified, ShopInfo goods) {}

    private static class Edit {
        int shopType;
        int goodsId;
        boolean enabled = true;
        ShopInfo goods;
    }

    private static class Settings {
        List<Edit> edits = new ArrayList<>();
        Set<String> disabledProducts = new HashSet<>();
        int nextCustomGoodsId = CUSTOM_ID_BASE;
    }

    private final Path path;
    private Settings settings = new Settings();
    private List<Entry> base = List.of();
    private volatile List<Entry> entries = List.of();
    private volatile Int2ObjectMap<List<ShopInfo>> active = Int2ObjectMaps.emptyMap();

    public ShopCatalog(Path path) {
        this.path = path;
        if (Files.exists(path)) {
            try {
                settings = JsonUtils.loadToClass(path, Settings.class);
                if (settings == null || settings.edits == null || settings.disabledProducts == null)
                    throw new IOException("商城覆盖配置不完整。");
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("无法读取商城配置 " + path, e);
            }
        }
    }

    public synchronized void replaceBase(Int2ObjectMap<List<ShopInfo>> source) {
        var rows = new ArrayList<Entry>();
        source.forEach(
                (shop, goods) ->
                        goods.forEach(g -> rows.add(new Entry(shop, true, false, false, copy(g)))));
        base = List.copyOf(rows);
        publish();
    }

    public Int2ObjectMap<List<ShopInfo>> active() {
        return active;
    }

    public List<Entry> entries() {
        return entries;
    }

    public synchronized boolean productEnabled(String key) {
        return !settings.disabledProducts.contains(key);
    }

    public synchronized void setProductEnabled(String key, boolean enabled) throws IOException {
        var next = copySettings();
        if (enabled) next.disabledProducts.remove(key);
        else next.disabledProducts.add(key);
        persist(next);
    }

    public synchronized int edit(int shopType, int goodsId, String action, ShopInfo goods)
            throws IOException {
        if (shopType <= 0) throw new IllegalArgumentException("商店 ID 必须大于 0。");
        if (goodsId < 0) throw new IllegalArgumentException("商品编号无效。");
        if (goodsId == 0 && action.equals("save")) {
            goodsId =
                    Math.max(
                            settings.nextCustomGoodsId,
                            entries.stream()
                                            .mapToInt(e -> e.goods.getGoodsId())
                                            .filter(id -> id >= CUSTOM_ID_BASE && id < ArtifactShop.GOODS_ID_BASE)
                                            .max()
                                            .orElse(CUSTOM_ID_BASE - 1)
                                    + 1);
            if (goodsId >= ArtifactShop.GOODS_ID_BASE) throw new IllegalArgumentException("自定义商品编号已用完。");
        }
        final int id = goodsId;
        var current =
                entries.stream()
                        .filter(e -> e.shopType == shopType && e.goods.getGoodsId() == id)
                        .findFirst()
                        .orElse(null);
        if (!action.equals("save") && current == null) throw new IllegalArgumentException("商品不存在。");
        var next = copySettings();
        if (action.equals("save") && goodsId >= CUSTOM_ID_BASE && goodsId < ArtifactShop.GOODS_ID_BASE)
            next.nextCustomGoodsId = Math.max(next.nextCustomGoodsId, goodsId + 1);
        var previous =
                next.edits.stream()
                        .filter(e -> e.shopType == shopType && e.goodsId == id)
                        .findFirst()
                        .orElse(null);
        if (action.equals("reset")) {
            next.edits.removeIf(e -> e.shopType == shopType && e.goodsId == id);
        } else {
            var edit = previous == null ? new Edit() : previous;
            edit.shopType = shopType;
            edit.goodsId = id;
            switch (action) {
                case "save" -> {
                    if (goods == null) throw new IllegalArgumentException("缺少商品内容。");
                    goods = copy(goods);
                    goods.setGoodsId(id);
                    validate(goods);
                    if (current == null && entries.stream().anyMatch(e -> e.goods.getGoodsId() == id))
                        throw new IllegalArgumentException("商品编号已在其他商店使用。");
                    if (id >= ArtifactShop.GOODS_ID_BASE
                            && (current == null
                                    || goods.getGoodsItem().getId() != current.goods.getGoodsItem().getId()
                                    || goods.getGoodsItem().getCount() != 1))
                        throw new IllegalArgumentException("自动圣遗物商品只能修改价格和限购。");
                    edit.goods = goods;
                }
                case "enable" -> edit.enabled = true;
                case "disable" -> edit.enabled = false;
                default -> throw new IllegalArgumentException("未知商城操作。");
            }
            if (previous == null) next.edits.add(edit);
        }
        persist(next);
        publish();
        return id;
    }

    private static void validate(ShopInfo g) {
        if (g.getGoodsItem() == null
                || !GameData.getItemDataMap().containsKey(g.getGoodsItem().getId()))
            throw new IllegalArgumentException("物品 ID 不在当前资源中。");
        if (g.getGoodsItem().getCount() < 1
                || g.getGoodsItem().getCount() > 1_000_000
                || g.getScoin() < 0
                || g.getHcoin() < 0
                || g.getMcoin() < 0
                || g.getBuyLimit() < 0
                || g.getMinLevel() < 0
                || g.getMaxLevel() < g.getMinLevel()
                || g.getMaxLevel() > 61
                || g.getBeginTime() < 0
                || g.getEndTime() <= g.getBeginTime())
            throw new IllegalArgumentException("数量、价格、限购、等级或时间范围无效。");
        if (g.getCostItemList() == null) g.setCostItemList(new ArrayList<>());
        for (var cost : g.getCostItemList()) {
            if (cost == null
                    || cost.getCount() <= 0
                    || !GameData.getItemDataMap().containsKey(cost.getId()))
                throw new IllegalArgumentException("额外兑换材料无效。");
        }
        var refresh = g.getShopRefreshType();
        int param = g.getShopRefreshParam();
        if ((refresh == ShopInfo.ShopRefreshType.SHOP_REFRESH_DAILY && (param < 1 || param > 365))
                || (refresh == ShopInfo.ShopRefreshType.SHOP_REFRESH_WEEKLY && (param < 1 || param > 52))
                || (refresh == ShopInfo.ShopRefreshType.SHOP_REFRESH_MONTHLY && (param < 1 || param > 12)))
            throw new IllegalArgumentException("刷新间隔无效：天数 1–365，周数 1–52，月数 1–12。");
    }

    private void publish() {
        var rows = new LinkedHashMap<String, Entry>();
        for (var e : base) rows.put(e.shopType + ":" + e.goods.getGoodsId(), e);
        for (var edit : settings.edits) {
            var key = edit.shopType + ":" + edit.goodsId;
            var original = rows.get(key);
            if (original == null && edit.goods == null) continue;
            rows.put(
                    key,
                    new Entry(
                            edit.shopType,
                            edit.enabled,
                            original == null,
                            true,
                            copy(edit.goods == null ? original.goods : edit.goods)));
        }
        var result = new Int2ObjectOpenHashMap<List<ShopInfo>>();
        rows.values().stream()
                .filter(Entry::enabled)
                .forEach(e -> result.computeIfAbsent(e.shopType, x -> new ArrayList<>()).add(e.goods));
        result.replaceAll((k, v) -> List.copyOf(v));
        entries = List.copyOf(rows.values());
        active = Int2ObjectMaps.unmodifiable(result);
    }

    private Settings copySettings() {
        return JsonUtils.decode(JsonUtils.toJson(settings), Settings.class);
    }

    public static ShopInfo copy(ShopInfo g) {
        return JsonUtils.decode(JsonUtils.toJson(g), ShopInfo.class);
    }

    private void persist(Settings next) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        var temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "shop-", ".tmp");
        try {
            Files.writeString(temporary, JsonUtils.encode(next), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            settings = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
