# 7.1 store protocol evidence

The fields below were checked against the provided local `YuanShen.exe`, SHA256
`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`.
The read-only extractor and cached generic type metadata supplied the class indexes;
native protobuf readers, writers and codec constructors supplied the wire tags.
These are not copied from a different client version.

| Message / class index | Verified fields | Native evidence |
| --- | --- | --- |
| PlayerRechargeDataNotify / 19066 / opcode 9913 | price tier version = 14, product tier list = 15 | reader `0x148293360`, tags `0x70`, `0x7a`; opcode getter `0x1482930b0` |
| ProductPriceTier / 45455 | product ID = 8, price tier = 15 | writer `0x152404430`, tags `0x42`, `0x7a`; these differ from ShopCardProduct's fields 1 / 2 |
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
Both `listPriceTier` and `listPriceTierV2` are served for the CN and global SDKs,
using GET or POST. The supplied SDK's `PayManager.RequestPriceTier` at
`0x159431ae5` selects V2 when its box configuration enables it. Its product-list
consumer at `0x15929b37e` reads `t_price`, and `0x15929b3ae` reads `tier_id`.
Previously V2 fell through to an empty `data: {}` success response; that response
cannot populate the SDK's product lookup and can leave the card unready.
`StorePriceRoutesTest` exercises both versions, regions and methods over HTTP,
including the production empty-response fallback. Safe request logs record only
the API, region and tier count. Native rendering after restarting the client's
cached SDK still requires an in-game check.
The SDK also creates its own Unity requests through `MiHoYo.SDK.NetworkManager`:
`RequestPriceTier` calls `PostRequest` at `0x1593b0170`, while V2 calls `GetRequest`
at `0x1593b0880`. These do not call the game's `WebRequestUtils.MakeInitialURL`.
The HTTP patch now redirects only the two price endpoints at these SDK entries,
preserving query parameters, request bodies and callbacks. Both signatures were
verified to occur exactly once in the supplied client binary; unmatched profiles
log a missing hook instead of using a guessed address. SDK price logs omit query
strings and account data. URL routing tests cover both regions, API versions and
schemes, and unrelated SDK/file requests. An already running game needs to close
before the rebuilt patch can be installed. The in-game result remains unverified
until that patch is loaded and the SDK price requests reach the local server.
The patch's executable-name check is case-insensitive, matching Windows path
semantics. Computer Use launched the supplied client as `yuanshen.exe`; the old
case-sensitive check logged `Executable is not Genshin. Skipping initialization.`
and skipped every HTTP hook. `executable_tests` covers both game names and mixed
case, and still rejects unrelated executables.
The native `AccountPlatNative.dll` also contains `login_get_product_list` and
`listPriceTier`. Its supplied pristine SHA256 is
`8b711b899feb6be347d7af3ffc9e9adfd6bb6e8624a0f7104578b05bf2c7a53b`.
This SDK embeds libcurl: three native request builders set `CURLOPT_URL` (10002)
through `curl_easy_setopt` at RVA `0x41ae90`. The calls occur at RVAs `0x2ee852`,
`0x2f1834` and `0x2f3975`. WinHTTP imports in this DLL supply proxy configuration,
so hooking WinHTTP connections alone does not cover its libcurl HTTP transport.
The patch waits for the SDK module to load and uses a unique checked signature
to redirect only the two price endpoint URLs at libcurl's URL option. Other
options, request bodies and callbacks are preserved. A bounded CString cache
keeps URL pointers valid while libcurl copies them. Tests exercise the native
register arguments and ensure numeric options are never treated as pointers.
Product-list invocation at `0x1470ab970` and its game callback at `0x149648b80`
now log only request/response presence and identifier counts, without payloads.
The installed native hook initialized successfully, but the monthly-card panel
still showed its preparing message, with neither a product-list invocation nor a
price request. Client handler `0x14900a200` receives type 19066 / opcode 9913 and
passes the product ID / tier lists to `0x149647630`, which initializes the SDK
query object at offset `0x58`. Request method `0x14964fd30` returns immediately
when this query is absent. The server previously had no such notification and
`PlayerRechargeDataNotify` still had a negative unsupported opcode placeholder.
The server now sends the resource product catalog at login and before mall,
shop, batch shop and battle pass product responses. Only enabled native products
are included; GM-only direct primogem grants have no SDK product ID and are omitted.
The packet's version and HTTP price table share the same version constant.
`StoreProductInitializationTest` checks the observed wire bytes, SKU and tier
coverage, enabled state and version agreement. Native display must still be
verified after receiving this catalog; URL hook initialization alone is insufficient.
After deploying the catalog notification on 2026-10-02, the existing patched
client reconnected and logged `SDK product list requested`, then requested the
local CN `listPriceTierV2` endpoint and received 18 product identifiers. The server
confirmed the price request. All 53 shop regression tests passed, including the
new catalog wire fixtures. This confirms the missing notification blocked product
discovery; panel rendering and native checkout remain separate verification steps.
Battle pass product types follow the resource's NORMAL / EXTRA / UPGRADE and
discount variants. Battle pass extras and reward index use the current schedule.
The cached numeric field defaults also verify HCOIN card type = 1 and battle pass
NORMAL / EXTRA / UPGRADE / NORMAL_DISCOUNT / EXTRA_DISCOUNT = 1 / 2 / 3 / 4 / 5.

`ShopmallRecommendConfigData.json` references PACKAGE config 101, but this is a
product configuration reference, not the shop that the recommendation resolver
queries. Native resolver `0x149000729` selects shop 900, and `0x14900079d` searches
its product map. Conversion `0x14c623e3e` consumes shop 900's card list. The card
therefore appears in both 900 and 902, with the same product ID, enabled state,
remaining days and persisted purchase records. Advertising entrance 900 alone
leaves the recommendation panel empty. Crystals remain in 903; premium battle
pass products use the separate battle pass product query.

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
Resource level requirements are also retained. At adventure rank 8, the supplied
catalogue exposes 14 currently offered rows; many material rows require rank 10,
25 or 35. This can show fewer goods than the former fork catalogue, which removed
those requirements. GM product editors can explicitly lower an individual good's
minimum rank when a local custom catalogue is wanted.
The editor preserves the resource upper bound of 99 for ordinary goods, as well
as Beyond goods. Older validation capped ordinary rows at 61 and prevented an
otherwise unchanged resource good from being saved. Ordinary goods still reject
Beyond-only crystal prices.
Battle pass entrance open state 1301 requires adventure rank 20 in
`OpenStateConfigData.json`; state 1300 enables the underlying system at rank 1.
The native enum defaults identify these as BATTLE_PASS_ENTRY and BATTLE_PASS.
An enabled GM premium product does not open the rank-gated game entrance.

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
