package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.ShopGoodsOuterClass.ShopGoods;
import emu.grasscutter.net.proto.ShopOuterClass.Shop;
import emu.grasscutter.utils.Utils;
import java.util.*;
import java.util.stream.Collectors;

public class PacketGetShopRsp extends BasePacket {
    public PacketGetShopRsp(Player player, int shopType) {
        super(PacketOpcodes.GetShopRsp);

        this.setData(
                GetShopRspOuterClass.GetShopRsp.newBuilder()
                        .setShop(buildShop(player, Grasscutter.getGameServer().getShopSystem(), shopType))
                        .build());
        player.save();
    }

    public static Shop buildShop(Player player, ShopSystem manager, int shopType) {
        Shop.Builder shop =
                Shop.newBuilder().setShopType(shopType).setCityId(1).setCityReputationLevel(10);

        if (manager.getShopData().get(shopType) != null) {
            List<ShopInfo> list = manager.getShopData().get(shopType);
            List<ShopGoods> goodsList = new ArrayList<>();

            for (ShopInfo info : list) {
                int currentTs = Utils.getCurrentSeconds();
                var item = info.resolveGoodsItem(currentTs);
                if (currentTs < info.getBeginTime()
                        || currentTs >= info.getEndTime()
                        || item == null
                        || item.getId() <= 0
                        || item.getCount() <= 0) continue;
                ShopGoods.Builder goods =
                        ShopGoods.newBuilder()
                                .setGoodsId(info.getGoodsId())
                                .setGoodsItem(
                                        ItemParamOuterClass.ItemParam.newBuilder()
                                                .setItemId(item.getId())
                                                .setCount(item.getCount())
                                                .build())
                                .setScoin(info.getScoin())
                                .setHcoin(info.getHcoin())
                                .setBeginTime(info.getBeginTime())
                                .setEndTime(info.getEndTime())
                                .setMinLevel(info.getMinLevel())
                                .setMaxLevel(info.getMaxLevel())
                                .setMcoin(info.getMcoin())
                                .setSingleLimit(info.getBuyLimit());

                if (info.getCostItemList() != null) {
                    goods.addAllCostItemList(
                            info.getCostItemList().stream()
                                    .map(
                                            x ->
                                                    ItemParamOuterClass.ItemParam.newBuilder()
                                                            .setItemId(x.getId())
                                                            .setCount(x.getCount())
                                                            .build())
                                    .collect(Collectors.toList()));
                }

                // Native clients append scalar currency costs themselves. Duplicating those
                // currencies in cost_item_list displays them twice in the UI.
                ShopLimit currentShopLimit = player.getGoodsLimit(info.getGoodsId());
                int nextRefreshTime = ShopSystem.getShopNextRefreshTime(info);

                if (currentShopLimit != null) {
                    if (currentShopLimit.getNextRefreshTime() > 0
                            && currentShopLimit.getNextRefreshTime() <= currentTs) {
                        currentShopLimit.setHasBoughtInPeriod(0);
                        currentShopLimit.setNextRefreshTime(nextRefreshTime);
                    }
                    goods.setBoughtNum(currentShopLimit.getHasBoughtInPeriod());
                    goods.setNextRefreshTime(currentShopLimit.getNextRefreshTime());
                } else {
                    player.addShopLimit(goods.getGoodsId(), 0, nextRefreshTime);
                    goods.setNextRefreshTime(nextRefreshTime);
                }

                goodsList.add(goods.build());
            }

            shop.addAllGoodsList(goodsList);
        }

        manager.getFreeStore().addProducts(shop, player);
        return shop.build();
    }
}
