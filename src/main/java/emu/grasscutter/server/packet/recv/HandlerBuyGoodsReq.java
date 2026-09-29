package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BuyGoodsReqOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBuyGoodsRsp;
import emu.grasscutter.utils.Utils;
import java.util.*;
import java.util.stream.Stream;

@Opcodes(PacketOpcodes.BuyGoodsReq)
public class HandlerBuyGoodsReq extends PacketHandler {

    // putItem refuses to store these material types (see Inventory.putItem: it warns about a
    // resources error and returns null), so a useOnGain good of one of them can never be
    // delivered by dropping it in the bag. The item's own use action is the only delivery the
    // player ever sees - for a costume that unlocks the skin, for a namecard it adds the card.
    private static final Set<MaterialType> NON_STORABLE_USE_ON_GAIN =
            EnumSet.of(
                    MaterialType.MATERIAL_AVATAR,
                    MaterialType.MATERIAL_FLYCLOAK,
                    MaterialType.MATERIAL_COSTUME,
                    MaterialType.MATERIAL_NAMECARD);

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        BuyGoodsReqOuterClass.BuyGoodsReq buyGoodsReq =
                BuyGoodsReqOuterClass.BuyGoodsReq.parseFrom(payload);
        List<ShopInfo> configShop =
                session.getServer().getShopSystem().getShopData().get(buyGoodsReq.getShopType());
        if (configShop == null) {
            session.send(new PacketBuyGoodsRsp(Retcode.RET_SVR_ERROR));
            return;
        }

        // Don't trust your users' input
        var player = session.getPlayer();

        // A non-positive count buys nothing and cannot be honest. Worse, it inverts the whole
        // purchase: payItems checks `held < cost * count`, which is never true for a negative
        // count, and payVirtualItem then subtracts that negative - handing out mora and
        // primogems instead of taking them.
        int buyCount = buyGoodsReq.getBuyCount();
        if (buyCount <= 0) {
            session.send(new PacketBuyGoodsRsp(Retcode.RET_SVR_ERROR));
            return;
        }

        List<Integer> targetShopGoodsId = List.of(buyGoodsReq.getGoods().getGoodsId());
        for (int goodsId : targetShopGoodsId) {
            Optional<ShopInfo> sg2 =
                    configShop.stream().filter(x -> x.getGoodsId() == goodsId).findFirst();
            if (sg2.isEmpty()) {
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SVR_ERROR));
                continue;
            }
            ShopInfo sg = sg2.get();

            int currentTs = Utils.getCurrentSeconds();
            ShopLimit shopLimit = player.getGoodsLimit(sg.getGoodsId());
            int bought = 0;
            if (shopLimit != null) {
                if (currentTs > shopLimit.getNextRefreshTime()) {
                    shopLimit.setNextRefreshTime(ShopSystem.getShopNextRefreshTime(sg));
                } else {
                    bought = shopLimit.getHasBoughtInPeriod();
                }
                player.save();
            }

            if ((bought + buyCount > sg.getBuyLimit()) && sg.getBuyLimit() != 0) {
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT));
                continue;
            }

            var artifactShop = session.getServer().getShopSystem().getArtifactShop();
            var piece = artifactShop.getPiece(sg.getGoodsId());
            if (piece != null) {
                // Artifacts do not stack, so a batch buy needs that many free slots. Asking before
                // the payment keeps a full bag from swallowing the mora and handing back nothing.
                var relics = player.getInventory().getInventoryTab(ItemType.ITEM_RELIQUARY);
                if (buyCount > relics.getMaxCapacity() - relics.getSize()) {
                    session.send(new PacketBuyGoodsRsp(Retcode.RET_PACK_EXCEED_MAX_WEIGHT));
                    continue;
                }
            }

            List<ItemParamData> costs =
                    new ArrayList<ItemParamData>(sg.getCostItemList()); // Can this even be null?
            costs.add(new ItemParamData(202, sg.getScoin()));
            costs.add(new ItemParamData(201, sg.getHcoin()));
            costs.add(new ItemParamData(203, sg.getMcoin()));
            if (!player.getInventory().payItems(costs, buyCount)) {
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SHOP_CONTENT_NOT_MATCH));
                continue;
            }

            int itemId = sg.getGoodsItem().getId();
            int itemCount;
            try {
                // A free good passes payItems whatever the count, so this product is the only
                // thing standing between a crafted request and an overflowed stack.
                itemCount = Math.multiplyExact(buyCount, sg.getGoodsItem().getCount());
            } catch (ArithmeticException overflow) {
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SVR_ERROR));
                continue;
            }
            if (piece != null) {
                // An artifact never comes out the same twice, so a batch buy is that many
                // separately rolled pieces rather than one piece counted up.
                var rolled = new ArrayList<GameItem>(buyCount);
                for (int i = 0; i < buyCount; i++) {
                    rolled.add(artifactShop.roll(piece));
                }
                player.getInventory().addItems(rolled, ActionReason.Shop);
            } else {
                var itemData = GameData.getItemDataMap().get(itemId);
                if (itemData != null
                        && itemData.isUseOnGain()
                        && NON_STORABLE_USE_ON_GAIN.contains(itemData.getMaterialType())) {
                    // A costume pack (shop 1052) is useOnGain MATERIAL_COSTUME, so the bag path
                    // below would refuse it, the purchase would "fail", and the player would walk
                    // away with a refund instead of the skin. The good delivers itself: run its use
                    // action once per unit bought.
                    int delivered = 0;
                    for (int i = 0; i < itemCount; i++) {
                        var params = new UseItemParams(player, itemData.getUseTarget());
                        params.usedItemId = itemId;
                        if (!session.getServer().getInventorySystem().useItemDirect(itemData, params)) {
                            break;
                        }
                        delivered++;
                    }
                    if (delivered == 0) {
                        // The use action rejected the good, so the player got nothing for the
                        // currency they handed over.
                        costs.forEach(
                                cost ->
                                        player.getInventory()
                                                .addItem(cost.getId(), cost.getCount() * buyCount));
                        session.send(new PacketBuyGoodsRsp(Retcode.RET_SHOP_CONTENT_NOT_MATCH));
                        continue;
                    }
                    if (delivered < itemCount) {
                        Grasscutter.getLogger()
                                .warn(
                                        "Item use consumed {} of the {} units of item {} bought by player {}.",
                                        delivered,
                                        itemCount,
                                        itemId,
                                        player.getUid());
                        int undelivered = itemCount - delivered;
                        for (var cost : costs) {
                            int perUnit = cost.getCount() * buyCount / itemCount;
                            if (perUnit > 0) {
                                player.getInventory().addItem(cost.getId(), perUnit * undelivered);
                            }
                        }
                    }
                } else {
                    GameItem item = new GameItem(itemId, itemCount);
                    // A bundle is useOnGain, so the default path consumes it and hands out whatever
                    // the chest table holds - and if that table is missing the purchase just
                    // vanishes. The player bought the item, so put the item in the bag and let them
                    // open it.
                    boolean delivered =
                            player.getInventory().addItem(item, ActionReason.Shop, true, true);
                    if (!delivered) {
                        // The bag was full or the stack could not take the count. Hand the currency
                        // back and answer failure, rather than charging for a good that never
                        // arrived.
                        costs.forEach(
                                cost ->
                                        player.getInventory()
                                                .addItem(cost.getId(), cost.getCount() * buyCount));
                        session.send(new PacketBuyGoodsRsp(Retcode.RET_PACK_EXCEED_MAX_WEIGHT));
                        continue;
                    }
                }
            }
            // Only now that the goods are in the bag does the purchase count against the refresh
            // limit. Recording it earlier would burn a player's limited buys on a failed delivery.
            player.addShopLimit(
                    sg.getGoodsId(), buyCount, ShopSystem.getShopNextRefreshTime(sg));
            session.send(
                    new PacketBuyGoodsRsp(
                            buyGoodsReq.getShopType(),
                            player.getGoodsLimit(sg.getGoodsId()).getHasBoughtInPeriod(),
                            Stream.of(buyGoodsReq.getGoods())
                                    .filter(x -> x.getGoodsId() == goodsId)
                                    .findFirst()
                                    .get()));
        }

        player.save();
    }
}
