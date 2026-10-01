package emu.grasscutter.game.shop;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ShopGoodsData;
import emu.grasscutter.data.excels.ShopRotateData;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.*;

public class ShopInfo {
    @Getter @Setter private int goodsId = 0;
    @Getter @Setter private ItemParamData goodsItem;
    @Getter @Setter private int scoin = 0;
    @Getter @Setter private List<ItemParamData> costItemList;
    @Getter @Setter private int boughtNum = 0;
    @Getter @Setter private int buyLimit = 0;
    @Getter @Setter private int beginTime = 0;
    @Getter @Setter private int endTime = 1924992000;
    @Getter @Setter private int minLevel = 0;
    @Getter @Setter private int maxLevel = 61;
    @Getter @Setter private List<Integer> preGoodsIdList = new ArrayList<>();
    @Getter @Setter private int mcoin = 0;
    @Getter @Setter private int hcoin = 0;
    @Getter @Setter private int disableType = 0;
    @Getter @Setter private int secondarySheetId = 0;
    @Getter @Setter private int rotateId = 0;

    private String refreshType;
    private transient ShopRefreshType shopRefreshType;
    @Getter @Setter private int shopRefreshParam;

    public ShopInfo() {
        // Gson, and the goods the server builds itself
    }

    public ShopInfo(ShopGoodsData sgd) {
        this.goodsId = sgd.getGoodsId();
        this.goodsItem = new ItemParamData(sgd.getItemId(), sgd.getItemCount());
        this.scoin = sgd.getCostScoin();
        this.mcoin = sgd.getCostMcoin();
        this.hcoin = sgd.getCostHcoin();
        this.buyLimit = sgd.getBuyLimit();
        this.rotateId = sgd.getRotateId();
        this.beginTime = resourceTime(sgd.getBeginTime(), 0);
        this.endTime = resourceTime(sgd.getEndTime(), this.endTime);

        this.minLevel = sgd.getMinPlayerLevel();
        this.maxLevel = sgd.getMaxPlayerLevel() == 0 ? 61 : sgd.getMaxPlayerLevel();
        var costItems = sgd.getCostItems();
        this.costItemList =
                costItems == null
                        ? new ArrayList<>()
                        : costItems.stream()
                                .filter(x -> x != null && x.getId() != 0)
                                .map(x -> new ItemParamData(x.getId(), x.getCount()))
                                .toList();
        this.secondarySheetId = sgd.getSubTabId();
        setShopRefreshType(sgd.getRefreshType());
        this.shopRefreshParam = sgd.getRefreshParam();
    }

    private static int resourceTime(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        return Math.toIntExact(
                LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(ZoneId.of("Asia/Shanghai"))
                        .toEpochSecond());
    }

    /** Resolve the monthly resource rotation without changing the shared catalogue. */
    public ItemParamData resolveGoodsItem(int timestamp) {
        if (rotateId == 0) return goodsItem;
        var rotation =
                GameData.getShopRotateDataMap().values().stream()
                        .filter(row -> row.getRotateId() == rotateId)
                        .sorted(Comparator.comparingInt(ShopRotateData::getRotateOrder))
                        .toList();
        if (rotation.isEmpty()) return null;
        var zone = ZoneId.of("Asia/Shanghai");
        var start = YearMonth.from(Instant.ofEpochSecond(beginTime).atZone(zone));
        var month = YearMonth.from(Instant.ofEpochSecond(timestamp).atZone(zone).minusHours(4));
        int index = Math.floorMod(ChronoUnit.MONTHS.between(start, month), rotation.size());
        return new ItemParamData(rotation.get(index).getItemId(), goodsItem.getCount());
    }

    public ShopRefreshType getShopRefreshType() {
        if (refreshType == null) return ShopRefreshType.NONE;
        return switch (refreshType) {
            case "SHOP_REFRESH_DAILY" -> ShopInfo.ShopRefreshType.SHOP_REFRESH_DAILY;
            case "SHOP_REFRESH_WEEKLY" -> ShopInfo.ShopRefreshType.SHOP_REFRESH_WEEKLY;
            case "SHOP_REFRESH_MONTHLY" -> ShopInfo.ShopRefreshType.SHOP_REFRESH_MONTHLY;
            default -> ShopInfo.ShopRefreshType.NONE;
        };
    }

    public void setShopRefreshType(ShopRefreshType type) {
        this.shopRefreshType = type;
        this.refreshType = type == null ? "NONE" : type.name();
    }

    private boolean evaluateVirtualCost(ItemParamData item) {
        return switch (item.getId()) {
            case 201 -> {
                this.hcoin += item.getCount();
                yield true;
            }
            case 203 -> {
                this.mcoin += item.getCount();
                yield true;
            }
            default -> false;
        };
    }

    public void removeVirtualCosts() {
        if (this.costItemList != null) this.costItemList.removeIf(item -> evaluateVirtualCost(item));
    }

    public enum ShopRefreshType {
        NONE(0),
        SHOP_REFRESH_DAILY(1),
        SHOP_REFRESH_WEEKLY(2),
        SHOP_REFRESH_MONTHLY(3);

        private final int value;

        ShopRefreshType(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }
}
