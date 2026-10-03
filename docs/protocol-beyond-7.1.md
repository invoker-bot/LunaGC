# 7.1 Beyond mall and handbook protocol evidence

The verified client is the locally supplied `YuanShen.exe`, SHA256
`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`.
Class indexes come from the existing read-only metadata extractor. Wire numbers
come from its native protobuf readers/writers and nested codec arguments.

## Mall and currency

| Message / class index | Verified fields | Native evidence |
| --- | --- | --- |
| GetShopmallDataReq / 65749 / opcode 20461 | param = 10; selected shop type = 14 | sender `0x147256a80` writes instance offsets 0x18 / 0x1c; caller `0x148ff0d90` supplies Beyond types |
| GetShopmallDataRsp / 14262 / opcode 21279 | shop types = 7; selected shop type = 11; param = 15; retcode = 8 | reader and writer of class NLKDKAGCAHC, offsets 0x18 / 0x20 / 0x28 / 0x2c |
| ShopGoods / 79360 | Beyond mcoin price = 1068 | consumer `0x149a58d60` maps instance offset 0x84 to currency item 231 |
| Shop / 17784 | Beyond mcoin products = 13 | reader `0x152917570`, nested product class 66304 |
| RechargeReq / 67538 | Beyond product = 3 | writer `0x14af78330`, nested class 66304 |
| ShopBeyondMcoinProduct / 66304 | product ID = 1; price tier = 2 | native product codec; other numeric amounts remain unverified and unset |

The Beyond mall uses shop types 104000, 103000, 102000, 101000, 100000 and 105000.
Only categories with live goods or enabled products are advertised. Ordinary
Teyvat shop requests retain their own catalogue. Both single and batch queries
use the same shop builder, prices and original sale windows.

Native virtual item dispatch `0x14b504fd0` and its jump table `0x14b505a7c`
confirm the following mappings; item names alone were not used to assign properties.

| Virtual item | Player property |
| --- | --- |
| 231 | 10063, Beyond mcoin |
| 232 | 10065, costume gacha coin |
| 233 | 10069, attendance coin |
| 234 | 10061, costume transfer coin |
| 236 | 10067, costume gacha free coin |
| 241 | 10076, DLC coin |
| 242 | 10078, DLC free coin |
| 244 | 10082, free mcoin |

Level/creator level/experience property IDs 10073/10074/10075 are also recovered
from the client's numeric enum defaults. Purchases use Beyond level, not adventure rank.
GM free recharge reads the six `ProductBydMcoinDetailConfigData` tiers, desktop
product aliases, and base/first/repeat quantities from resources. Receipt tracking
is shared with the existing free store. Native numeric product amount fields
and payment SDK presentation still require further verification.

## Cosmetic ownership

| Message / class index | Verified fields | Native evidence |
| --- | --- | --- |
| BeyondCosmeticDataNotify / 83630 / opcode 23484 | owned costume list = 7 | reader `0x14ca696e0`, instance offset 0x40, nested class 85957 |
| BeyondAddCosmeticNotify / 41982 / opcode 3399 | owned costume list = 4 | reader `0x1503a21d0`, instance offset 0x48, nested class 85957 |
| Owned costume / 85957 | costume ID = 13; expiry time = 5; unknown value = 12 | native reader/writer, offsets 0x20 / 0x1c / 0x18 |

Data consumer `0x1537a4b20` and add consumer `0x1537a8860` insert IDs from this
list into the permanent owned set at offset 0x38. Separate lists populate trial
sets. Getter `0x1537a5ab0` returns the expiry value; consumer `0x14a899a40`
subtracts current seconds. Permanent purchases leave expiry and the unverified
value unset. They do not use the other, expiring cosmetic lists.

`BydMaterialExcelConfigData` use actions resolve direct costumes or suits.
Suit membership comes from `BeyondCostumeExcelConfigData`, not guessed ID ranges.
Ownership is embedded in the Player Mongo document, sent on login and updated
after purchase. Unsupported actions and complete duplicate purchases are rejected
before charging. This does not implement costume equip or saved outfit plans.

## Handbook initialization

| Message / class index | Verified fields | Native evidence |
| --- | --- | --- |
| WorldWatcherAllDataNotify / 72671 / opcode 22528 | group info list = 7 | reader `0x14e5a3530`, consumer `0x14975a370` loads group dictionary |
| WorldWatcherInfo / 66515 | group ID = 1; reward state = 2; watcher progress list = 3; finished watcher IDs = 4 | native nested codec and field readers |
| WorldWatcherProgress / 34405 | watcher ID = 1; progress = 2 | native nested codec and field readers |

The handbook panel `0x151c8533b` looks up resource group IDs through manager
`0x14a2f9c00`. State enum 80165 is unfinished=0, finished=1, reward taken=2.
`BeyondHandbookExcelConfigData` group key `KGEHGPFLIBH`, watcher requirements and
AND/OR logic `ILBBEFKCLGD` determine each group's state. Missing watcher resources
cannot complete a group. Fresh accounts receive actual unfinished targets.
Existing saved progress is capped at each target and claimed groups remain claimed.

This implements catalogue initialization and serialization, not gameplay
settlement. No completed progress, reward claims or season schedule is fabricated.
The supplied Beyond battle pass schedules contain placeholder 1998 dates;
Beyond battle pass, UGC play/hall services and task reward delivery remain absent.

## Verification boundary

Tests cover fixed native wire fixtures, selection-separated catalogues, expired
goods, resource cost aliases, currency debits and overflow, receipt idempotency,
direct/suit ownership, duplicate rejection, handbook state logic and BSON
round trips with the production Morphia mapper without database writes.

GM level validation accepts 99 for Beyond shops and retains 61 for ordinary
shops. The versioned text-map cache includes Byd material name hashes; old caches
are regenerated so missing cached names are not misreported as missing resources.

The game UI must be checked after reconnecting to the updated server. These tests
do not prove all Beyond screens, costume equip, task completion or payment SDK
checkout work in the official client.
