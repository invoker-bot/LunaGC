package emu.grasscutter.server.http.handlers;

import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.lang.Language;
import io.javalin.http.Context;
import java.util.*;

/** Called only through GmHandler's existing authorization guard. */
final class GmShop {
    private static ShopSystem system() {
        if (Grasscutter.getGameServer() == null) throw new IllegalArgumentException("游戏服务未启动。");
        return Grasscutter.getGameServer().getShopSystem();
    }

    private static String shopName(int type) {
        return switch (type) {
            case 902 -> "礼包商城";
            case 903 -> "创世结晶";
            case 1001 -> "派蒙兑换";
            case 1052 -> "角色装扮";
            default -> "商店 " + type;
        };
    }

    static void list(Context ctx) {
        var entries = system().getCatalog().entries();
        var names = Language.getTextMapStrings();
        var result = new ArrayList<Map<String, Object>>();
        for (var e : entries) {
            var data = GameData.getItemDataMap().get(e.goods().getGoodsItem().getId());
            String name = data == null ? "资源中未找到的物品" : GmHandler.nameOf(names, data);
            var row = new LinkedHashMap<String, Object>();
            row.put("shopType", e.shopType());
            row.put("shopName", shopName(e.shopType()));
            row.put("name", name);
            row.put("enabled", e.enabled());
            row.put("custom", e.custom());
            row.put("modified", e.modified());
            row.put("goods", e.goods());
            result.add(row);
        }
        result.sort(Comparator.comparingInt(m -> ((ShopInfo) m.get("goods")).getGoodsId()));
        ctx.json(
                Map.of(
                        "retcode",
                        0,
                        "goods",
                        result,
                        "shops",
                        entries.stream()
                                .map(ShopCatalog.Entry::shopType)
                                .distinct()
                                .sorted()
                                .map(id -> Map.of("id", id, "name", shopName(id)))
                                .toList()));
    }

    static void edit(Context ctx) throws Exception {
        var request = JsonUtils.decode(ctx.body(), JsonObject.class);
        if (request == null) throw new IllegalArgumentException("缺少商品设置。");
        int shopType = request.get("shopType").getAsInt();
        int goodsId = request.has("goodsId") ? request.get("goodsId").getAsInt() : 0;
        String action = request.get("action").getAsString();
        var goods =
                request.has("goods") ? JsonUtils.decode(request.get("goods"), ShopInfo.class) : null;
        int id = system().getCatalog().edit(shopType, goodsId, action, goods);
        ctx.json(Map.of("retcode", 0, "goodsId", id, "message", "商品设置已保存。游戏内重新打开商店即可刷新。"));
    }

    static void products(Context ctx) {
        var store = system().getFreeStore();
        ctx.json(
                Map.of(
                        "retcode",
                        0,
                        "products",
                        store.products().stream()
                                .map(p -> Map.of("product", p, "enabled", store.enabled(p), "price", 0))
                                .toList()));
    }

    static void editProduct(Context ctx) throws Exception {
        var request = JsonUtils.decode(ctx.body(), JsonObject.class);
        system()
                .getFreeStore()
                .setEnabled(request.get("key").getAsString(), request.get("enabled").getAsBoolean());
        ctx.json(Map.of("retcode", 0, "message", "免费商品状态已保存。"));
    }

    static void purchase(Context ctx) {
        var request = JsonUtils.decode(ctx.body(), JsonObject.class);
        int uid = request.get("target").getAsInt();
        var player = Grasscutter.getGameServer().getPlayerByUid(uid);
        if (player == null || !player.isOnline())
            throw new IllegalArgumentException("目标玩家未在线，请填写在线玩家 UID。");
        String message =
                system()
                        .getFreeStore()
                        .purchase(
                                player, request.get("key").getAsString(), request.get("receipt").getAsString());
        var pass = player.getBattlePassManager();
        ctx.json(
                Map.of(
                        "retcode",
                        0,
                        "message",
                        message,
                        "moonCardDays",
                        player.getMoonCardRemainDays(),
                        "primogems",
                        player.getPrimogems(),
                        "crystals",
                        player.getCrystals(),
                        "battlePassPaid",
                        pass != null && pass.isPaid(),
                        "battlePassLevel",
                        pass == null ? 0 : pass.getLevel()));
    }
}
