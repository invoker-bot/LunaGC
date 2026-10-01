# 7.1 store protocol evidence

The fields below were checked against the provided local `YuanShen.exe`, SHA256
`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`.
The read-only extractor and cached generic type metadata supplied the class indexes;
native protobuf readers, writers and codec constructors supplied the wire tags.
These are not copied from a different client version.

| Message / class index | Verified fields | Native evidence |
| --- | --- | --- |
| GetShopBatchReq / 12377 | packed shop types = 11 | codec constructor `0x1486033b0`, tag `0x5a` |
| GetShopBatchRsp / 68803 | shops = 4, retcode = 10 | reader `0x147848850`, tags `0x22`, `0x50` |
| Shop / 17784 | cards = 1, crystals = 9; goods = 4, shop type = 8 | reader `0x152917570`, nested types 51075 / 62721 |
| ShopGoods / 79360 | min level = 11, purchase limit = 14, mora = 9, crystals = 15 | reader `0x14c846ab0`; consumer `0x149a58d60` maps offsets 0x74 / 0x8c / 0x5c to item IDs 201 / 202 / 203; level check `0x149a571f5` reads 0x88; limit check `0x149a57404` reads 0x78 |
| ShopType / 57587 | RECOMMEND = 900, PACKAGE = 902, MCOIN = 903, PAIMON = 1001 | recovered numeric enum defaults; recommendation panel `0x14cfd78d9` tests shop type 0x384 |
| ShopPlayProduct / 42863 | product ID = 1, price tier = 2, play type = 3 | writer tags at `0x152776713`, `0x15277678b`, `0x1527767f2` |
| RechargeReq / 67538 | play = 1, card = 12, crystals = 14 | writer `0x14af78330`, nested types 42863 / 51075 / 62721 |
| RechargeRsp / 17747 | retcode = 5, product ID = 12 | reader `0x14eaef090`, tags `0x28`, `0x62` |
| GetBattlePassProductReq / 82534 | play type = 9 | writer `0x150c63ce0`, tag `0x48` |
| GetBattlePassProductRsp / 78059 | retcode = 2, tier = 4, play type = 6, product ID = 15 | reader `0x14fddac10`, tags `0x10`, `0x22`, `0x30`, `0x7a` |
| TakeBattlePassMissionPointReq / 28005 / opcode 23648 | packed mission IDs = 4 | codec constructor `0x1518454e0`, tag `0x22`; sender `0x14d124b70` shares the normal BP module with TakeBattlePassRewardReq |
| TakeBattlePassMissionPointRsp / 18629 / opcode 26251 | retcode = 8, mission IDs = 13 | reader `0x1497ab090`, tags `0x40`, `0x68` / `0x6a` |
| BattlePassSchedule / 25940 | paid platform flags = 9, weekly points = 15 | reader `0x15304b350`; conversion `0x14d1222b0` maps field 9 to the flags tested with `Miscs.GetPlatFormCategory` at `0x14d125110` |

The old Shop schema swapped its card and crystal lists. `StoreProtocolTest` now
parses fixed wire fixtures using the observed tags. Unknown fields and unsupported
concert/cloud products are left out; no unknown opcode has been invented.

Product IDs, aliases, pack amounts, first/subsequent crystal bonuses and monthly
card limits come from `Product*ConfigData.json`. Native product discovery selects
the desktop `ys_chn_*` aliases rather than the first `cloudys_chn_*` row.
Products retain the resource tier names (for example `Tier_1`, `Tier_5`, `Tier_10`)
and the SDK price endpoint returns a `t_price` array with `price: "0"` for each tier.
An invented `Tier_0` cannot match the native client configuration. Client-provided
prices or rewards are ignored. The SDK response shape follows the public
`sdk-static.mihoyo.com/hk4e_cn/mdk/shopwindow/shopwindow/listPriceTier?game_biz=hk4e_cn`
endpoint, with the prices replaced by zero.
Battle pass product types follow the resource's NORMAL / EXTRA / UPGRADE and
discount variants. Battle pass extras and reward index use the current schedule.
The cached numeric field defaults also verify HCOIN card type = 1 and battle pass
NORMAL / EXTRA / UPGRADE / NORMAL_DISCOUNT / EXTRA_DISCOUNT = 1 / 2 / 3 / 4 / 5.

`ShopmallRecommendConfigData.json` references PACKAGE config 101, so the monthly
card remains in shop 902; crystals are in 903 and premium battle pass products
use the battle pass product query. Moving the card to shop 1001 would contradict
the recommendation resource. The virtual recommendation entrance 900 must also
be advertised in `GetShopmallDataRsp`; it selects the recommendation panel and
looks up the PACKAGE card. Omitting 900 hides the recommendation page even when
902 contains a valid monthly card.

Native goods consumers append scalar mora, primogem and crystal prices to the
cost display. Those currencies must not also be duplicated in `cost_item_list`.
The four previously unmapped fields above now carry their verified tags rather
than placeholder field numbers in the 50000 range.

Paimon's Bargains uses current resource goods, including `rotateId` and original
sale windows, instead of the fork's fixed substitutions (unrelated characters,
five-star weapons and Traveler tokens in rotating slots). The month index is
relative to the first sale month in the row; the supplied rotations start in
October 2020. Queries and purchases resolve the same row at the Shanghai 04:00
month boundary. Expired rows and unresolved item ID 0 are omitted from client
responses. GM operator overrides are still applied after the resource catalogue.

Battle pass triggers are reloaded after resources load. Raw trigger names are
preserved because newer resources use names absent from the legacy watcher enum,
including `TRIGGER_CONSUME_RESIN`. Daily and weekly experience both count toward
the weekly limit; period tasks do not. Refresh dates are persisted, with daily
04:00 and Monday 04:00 boundaries in Asia/Shanghai. Legacy saves retain their
progress at first assignment. Invalid selections and wrong reward-track tags
are rejected; acknowledgements include only granted rewards and claimed missions.

## Verification boundary

The GM free purchase path delivers directly without an external payment SDK.
Native product discovery and recharge handlers are implemented with the verified
fields. The official client payment SDK may still open a platform checkout or
reject a zero-price tier; that UI requires a separate client-side verification.
Do not enter payment information. The GM free purchase path is the supported
local alternative while native checkout behavior remains unverified.
