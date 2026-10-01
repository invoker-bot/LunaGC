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
| ShopPlayProduct / 42863 | product ID = 1, price tier = 2, play type = 3 | writer tags at `0x152776713`, `0x15277678b`, `0x1527767f2` |
| RechargeReq / 67538 | play = 1, card = 12, crystals = 14 | writer `0x14af78330`, nested types 42863 / 51075 / 62721 |
| RechargeRsp / 17747 | retcode = 5, product ID = 12 | reader `0x14eaef090`, tags `0x28`, `0x62` |
| GetBattlePassProductReq / 82534 | play type = 9 | writer `0x150c63ce0`, tag `0x48` |
| GetBattlePassProductRsp / 78059 | retcode = 2, tier = 4, play type = 6, product ID = 15 | reader `0x14fddac10`, tags `0x10`, `0x22`, `0x30`, `0x7a` |

The old Shop schema swapped its card and crystal lists. `StoreProtocolTest` now
parses fixed wire fixtures using the observed tags. Unknown fields and unsupported
concert/cloud products are left out; no unknown opcode has been invented.

Product IDs, aliases, pack amounts, first/subsequent crystal bonuses and monthly
card limits come from `Product*ConfigData.json`. Supported cash products use the
server's zero-price tier `Tier_0`; client-provided prices or rewards are ignored.
Battle pass product types follow the resource's NORMAL / EXTRA / UPGRADE and
discount variants. Battle pass extras and reward index use the current schedule.
The cached numeric field defaults also verify HCOIN card type = 1 and battle pass
NORMAL / EXTRA / UPGRADE / NORMAL_DISCOUNT / EXTRA_DISCOUNT = 1 / 2 / 3 / 4 / 5.

## Verification boundary

The GM free purchase path delivers directly without an external payment SDK.
Native product discovery and recharge handlers are implemented with the verified
fields. The official client payment SDK may still open a platform checkout or
reject a zero-price tier; that UI requires a separate client-side verification.
Do not enter payment information. The GM free purchase path is the supported
local alternative while native checkout behavior remains unverified.
