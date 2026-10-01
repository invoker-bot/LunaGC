# LunaGC 7.1.0

## GM 物品与装备

物品名称读取中文主文本表及 `TextMap_MediumCHS.json`，支持分片文本表与已知的 ±512 索引偏移。
升级会重建旧名称缓存；资源确实缺少文本的条目显示「名称待确认（物品 ID）」，可搜索「名称待确认」筛出，不将索引或猜测当成正式名称。

武器与圣遗物使用「配置并给予」窗口，目标玩家须在线，每次最多发放 100 件：

- 武器：等级、突破阶段、精炼等级按资源限制；可在突破临界等级选择突破前或突破后。基础攻击力和副属性由武器、等级与突破自动计算。
- 圣遗物：等级使用游戏中的 +0～上限（五星通常为 +20），可选该部位的主属性，以及最多四种副属性的单次数值与总次数；未指定部分按资源权重随机补齐。
- 星级、套装、部位和初始副属性数量由物品 ID 决定，窗口展示这些信息。主副属性冲突、重复属性、错误属性库及超出当前等级次数的配置会被拒绝。
- 发放前检查背包容量，成功结果须确认装备保存。连接或保存异常时先核对背包，避免重复发放。

## 游戏内武器强化

已补齐 7.1 武器强化与溢出矿石预览的请求包号和材料字段。支持使用强化矿石、消耗未装备且未锁定的武器，
扣除摩拉并提升等级；达到当前突破阶段的等级上限后，溢出经验按资源换回矿石。预览不会扣除材料。
协议依据和测试范围见 [武器强化协议记录](docs/protocol-weapon-upgrade-7.1.md)。

## 公告与游戏邮件

[GM 控制台](http://localhost/gm) 新增「公告」和「邮件」：

- 公告支持草稿、编辑、定时发布、撤下、删除和分页。点击「查看游戏公告页」可预览当前生效内容。
  新公告正文按普通文字显示，国服与海外服使用同一份目录。
- 邮件支持向单个或多个 UID 发送，最多一次 100 个，离线玩家也可收到。可设置发件人、标题、正文、
  1–365 天有效期和最多 10 种附件；附件物品 ID 可在「物品」页查询。
- 发送界面保留本次批次编号。网络失败或部分投递失败后点击重试，会跳过已经投递的收件人。
  点击「新建邮件」才开始下一批。发送前会检查所有 UID 与附件；不存在的 UID 不会创建账号。
- 「查看玩家邮箱」可分页检查正文、到期时间、已读和附件领取状态。读信、星标、领取与删除已接入 7.1；
  未领取附件的有效邮件不能删除。旧邮件在读取时自动获得稳定编号，删除其他邮件不会改变它。

公告保存在运行数据目录的 `data/Notices.json`，Docker 使用持久化状态卷；第一次使用时会导入已有的
`GameAnnouncement.json` 正文（旧 HTML 转为文字），文件不存在时提供欢迎公告。
邮件、发送回执和编号序列保存在 MongoDB，重启后保留。部署和迁移时须同时备份状态卷与 MongoDB。

游戏公告页 `/hk4e/announcement/index.html` 直接随服务端打包，不依赖官方 CDN；
国服与海外服公告接口均已注册，加载失败会提示重试而不无限等待。
若旧客户端补丁打开公告后持续转圈，更新补丁并完全退出游戏后再执行 `task patch`。
新版补丁保留公告窗口的原始公钥，仅替换短登录数据所用的 1024 位 SDK 密钥，避免公告 Cookie 加密超长。
邮件附件领取先保存预约状态再发物品；若进程在发放中途退出，预约会阻止自动重复发放，
GM 邮箱会显示「附件领取中断」，需要核对数据库与日志后恢复，不能直接清除预约再次领取。
7.1 协议依据及实机验证边界见 [公告与邮件协议记录](docs/protocol-mail-notices-7.1.md)。

## 商城与免费充值商品

[GM 控制台](http://localhost/gm) 的「商城」包含两种目录：

- **免费充值商品**：空月祝福（小月卡）、珍珠纪行 / 珍珠之歌（大月卡）、原石与创世结晶。
  现金价格统一为 **0**；填写顶部在线玩家 UID 后点击「免费购买」即可发放。
  月卡按资源配置提供立即结晶和每日原石，剩余天数最多 180 天，按上海时间 04:00 换日。
  纪行使用本期任务和等级奖励；珍珠之歌的等级与额外奖励读取当前资源，同档位不能重复领取。
  结晶礼包保留资源中的首次 / 后续额外数量，直接购买原石则发放标注数量。
- **普通商店商品**：搜索、商店筛选、分页、编辑、新增、上架、下架与恢复默认。
  可调整数量、摩拉 / 原石 / 结晶 / 材料兑换价格、限购、冒险等阶和刷新间隔。
  普通商品的游戏内兑换价格保留；需要免费时可在 GM 设置为 0。

默认商品由 `resources/ExcelBinOutput/ShopGoodsExcelConfigData.json` 与服务端
`Shop.json` 合并，再生成圣遗物商店。资源加载后会完整重载商城，7.1 的批量查询也会返回商品。
尘辉兑换以资源表为准，角色与武器按原始轮换表在每月上海时间 1 日 04:00 更新；
旧配置中的固定替换商品不再覆盖轮换栏，过期或物品 ID 无效的商品不会显示。
推荐商品入口使用客户端的 900 编号，空月祝福由礼包商城提供；商品限购、等级与价格使用
已核对的 7.1 字段，货币费用不会重复显示。
GM 覆盖保存在运行数据目录的 `data/ShopOverrides.json`，Docker 使用持久化状态卷，
重启和更新资源会保留设置。「恢复默认」清除覆盖；自定义商品则可删除。
游戏内重新打开商城刷新商品；月卡和纪行的免费购买立即更新在线玩家。

游戏内空月祝福沿用客户端的「推荐商品 / 礼包」入口，创世结晶提供六档礼包；
大月卡在「纪行」入口解锁珍珠纪行或珍珠之歌。商品使用桌面客户端 ID 和资源中的价格档位，
SDK 价格表将这些档位的现金价格统一返回为 0；更新服务端后请完全重启游戏，以刷新 SDK 缓存。
原石的直接免费购买位于 GM 商城。

纪行任务在资源加载后重建缓存，排除已停用任务和往期任务；当前 7.1 资源提供 29 条本期任务。
已接通登录、每日委托、采矿、树脂消耗、摩拉消耗、烹饪、锻造、秘境、普通首领、
洞天宝钱、摆设制作、祈愿和五星圣遗物升级的任务进度。每日任务按上海时间 04:00 重置，
每周任务与每周经验上限在周一 04:00 重置，离线错过周一也会补做重置。
完成任务后可领取纪行经验；达到等级可领取免费 / 已解锁付费轨道的物品和自选奖励，重复请求不重复发放。
七圣召唤、部分周本 / 深境螺旋任务、周游壶灵和 7.1 活动专项任务仍需对应玩法的进度接入，
不能把任务目录完整视为所有玩法已经还原。

原生充值请求已接入，官方客户端支付 SDK 的实际 0 元购买界面仍待实机核验；
GM 免费购买可直接发放，不需要支付信息。协议依据见
[7.1 商城协议记录](docs/protocol-store-7.1.md)。

## GM 目录与分页

[GM 控制台](http://localhost/gm) 将当前卡池和历史归档合并到「历史卡池」。
支持按版本、角色、武器或卡池 ID 搜索，筛选当前配置、生效中或历史归档；
当前配置保留启用/禁用操作，历史记录可复刻 30 天。
「历史卡池」和「往期活动」均按单条记录分页，默认每页 20 条，可切换为 10 / 50 条，
列表上下均有首页、上一页、下一页和末页。搜索、筛选或修改每页条数后回到第一页，刷新和操作后保留当前页。

历史卡池随运行包附带 1.0～7.0 共 52 个版本、290 条归档，Docker 无需 Git 也能浏览与复刻；
开发目录仍可补充 Git 提交历史。归档覆盖不代表每一期内容和时间都已核实，7.1 历史归档尚未补入。
运行包的归档只用于目录和复刻模板，更新不会覆盖已有卡池配置或改变生效状态。
复刻卡池可设置 1–365 天有效期（默认 30 天），当前配置也可单独修改到期时间。
修改到期时间不会改变 UP、概率、保底配置、开始时间或启用状态；重新复刻则从现在开始计时并启用该卡池。
到期后卡池停止展示和抽卡，常驻池也遵守设置的时间窗口。玩家已打开的祈愿页面需要重新打开以刷新目录。

旧默认表的部分模板（含启用卡池）曾没有 `scheduleId`，导致 GM 无法直接管理或错误显示为未加载。
服务启动时会自动分配并保存唯一的
本地管理编号：禁用模板使用 50000–89999；启用的旧配置从 1000 开始分配并避开已有编号。
已有编号、物品池、启用状态和时间均保留，迁移前的原配置保存为数据目录中的
`Banners.before-id-migration-*.json`。这些编号用于本服务器管理，不代表官服原始卡池编号。
活动目录也不等于完整玩法还原，具体进度见下文。

## 往期活动

[GM 控制台](http://localhost/gm) 新增「往期活动」，按发布时间排列，显示历史开放时间和本次复刻时间，
支持开启、关闭、恢复与重新复刻。目录覆盖 401 条资源活动，158 条已对应历史时间。
每条活动可自定义 1–365 天复刻时长（0 为原活动时长）；已有排期可保存新的到期时间并保留进度和已领取奖励。
恢复开启沿用原排期，重新复刻会重置进度；修改到期时间会同步调整玩法关闭时间并保留原有领奖期长度。
按发布时间从 1.0「原素烘炉」开始还原；已恢复主控脚本，接入活动详情、Lua 计分、倒计时、阶段回调和 13 项任务进度。
已接入活动场景加载与关闭清理，以及 7.1 开始、进度、点名和结束通知。
已接入当前世界的房主邀请、队友答复、20 秒准备及中断流程。
已接入跨世界 2–4 人整队匹配、房主/队员确认、入队许可、场景转移和加载后准备。
已接入每轮个人领奖：按开局时的个人世界等级读取原掉落表，预览不扣费，确认后消耗 40 点原粹树脂，每人限领一次。
已接入资源中的 18 项结算称号，按本轮凝块、个人效率与击杀统计选择 watcher。
多人完整挑战与称号显示仍待实机验证，各活动的玩法完成度单独显示。
第二项 1.0「百货奇货」已加载七天材料和七组奖励，接入国服凌晨 4 点换日、每日提交资格、
复刻进度结构及 7.1 活动详情。材料提交请求、库存扣除和持久化开匣机会已接入；
已接入立本七套场景通知、每日对话、七匣不重复抽取与实际发奖；完整客户端流程待验证，当前标记为「还原中」。
第三项 1.1「未归的熄星」已恢复六区探索的原始地图脚本、118 个碎屑点、两轮区域任务与六组原始奖励。
采集记录、货币 109 累计数与任务进度一起保存，重复采集、重复领奖和旧排期请求会被拒绝。
已核对并修正 7.1 探索通知字段；陨星回收、多人挑战、商店、特殊角色奖励与剧情仍在还原，客户端完整流程待验证。
原 Lua 的 5 / 10 秒怪物重生与跨组清场已接入。提供本次 7.1 客户端的元数据提取工具，
已恢复 88,904 个类型、440,172 个字段名称及类型引用、733,442 个方法名称及地址；
现已核对烘炉所需的六个消息结构，并直接从客户端提取了 4,923 个包号返回函数。
玩家提交请求、回包的包号与字段已适配，Lua 失败码会原样返回，结束脚本可清理玩家增益；
回包语义名称的推断依据见协议说明。提取工具支持嵌套泛型参数。
详见 [往期活动说明](docs/HISTORICAL_ACTIVITIES.md)、[烘炉协议核对](docs/CRUCIBLE_PROTOCOL.md)、
[百货奇货协议核对](docs/SALESMAN_PROTOCOL.md)和 [未归的熄星协议核对](docs/ASTER_PROTOCOL.md)。


A community server being migrated to the **CN Genshin Impact 7.1.0** client. A fork of girluh's
[LunaGC](https://github.com/girluh/LunaGC), itself a fork of Grasscutter, reworked
for the 7.1 protocol while retaining the CN launcher's login chain.

The 7.1 proto sources, generated Java messages, CmdIds, login/heartbeat field parsing,
and versioned jar defaults are in place. Login and scene entry were verified on the
previous local build; they must be exercised again with a 7.1 client after this wire
format change. The feature notes below record that earlier 7.0 validation and are
not a claim that every feature has been retested on 7.1.

Local handlers with no verified 7.1 CmdId use negative placeholders, so they cannot
accidentally consume an unrelated 7.1 packet. Their features need fresh captures
before being enabled. `BlossomBriefInfoNotify` and `DelTeamEntityNotify` use the
7.1 fan-out map in `data/proto-fanout.json`.

The 818 additional `.proto` files marked `Recovered from ...` preserve schemas
embedded in formerly checked-in generated Java classes. They use separate
protobuf namespaces to avoid conflicting with the 7.1 definitions while keeping
the Java API needed by existing handlers. These recovered schemas are historical
compatibility data, not independently verified 7.1 messages. Gradle now generates
Java into `build/generated/source/proto`; generated Java is not tracked in Git.

Very WIP — expect broken things. What is implemented is listed below; everything
else is not.

## Choose how to run it

| Mode | Command | Resources | State |
| --- | --- | --- | --- |
| Windows development | `task dev` | A separate checkout selected by `.env.local`, or the `resources/` submodule | Shared Compose MongoDB volume; config/data in the working directory |
| Docker Compose runtime | `docker compose up --build -d` | Pinned `resources/` submodule copied into the image | Shared Compose MongoDB volume; config/data in the server volume |

For a fresh checkout, install Git LFS, clone with submodules, and fetch the
resource files:

```sh
git lfs install
git clone --recurse-submodules https://github.com/invoker-bot/LunaGC.git
cd LunaGC
git -C resources lfs pull
```

Then follow [Windows development](#first-time-setup) or
[Docker Compose runtime](#docker-compose-runtime). The runtime build needs
Docker Compose and enough disk space for the resource checkout and image.
The client protocol is still being migrated; a successful server start does
not establish compatibility with every 7.1 client feature.
The signing and client-compatibility keys committed in this repository are
public test material; do not reuse them for unrelated accounts or services.

## Note from the maintainer

This is a fork from girluh's [LunaGC](https://github.com/girluh/LunaGC). VERY WIP, so expect many bugs.

Features and functionality of the ps is not guaranteed, try it yourself to see what works and what doesnt (Most are broken).
This is possibly the only public PS with updated mob and gadget spawns! (Up to Version 5.4)

Contribute if you want/can...

## 7.0 validation history

- **The CN 7.0 login chain end to end**: the dispatch server serves the CN 7.0
  SDK's session routes and a `RegionInfo` with the right `game_biz`, so the
  launcher's own login flow reaches the game server instead of stalling on
  "无法连接网络" or "账号或密码错误".
- **The beginner quest chain**. Questing is on by default (see below) and the
  opening chain runs: Paimon's talk, gliding and stamina unlocks, the first
  statue, and the element gain, which is handled by `ExecChangeSkillDepot`
  rather than being skipped. A brand-new account gets the chain; an account that
  already played without it does not replay cutscenes.
- **The "client is damaged" crash is fixed.** The patched image is swapped into
  its slot in-process — the patch crate moves it aside when the DLL is mapped and
  restores it when the game exits — so the on-disk file the client's anti-cheat
  (`Astrolabe`) checks is the pristine one while the check runs. This applies to
  both swapped slots: the anti-cheat proxy (`Astrolabe.dll`) and the passport SDK
  (`AccountPlatNative.dll`). Before this, every session died at ~100 seconds with
  a WER crash in that DLL.
- **Unhandled packets are logged, not silently dropped.** Any request the server
  has no handler for is dumped at INFO with its opcode name, its number, its size
  and its decoded fields — this is the discovery mechanism for the features
  above, and `task dev` collects the list for you (see below).
- **The harvest list closes into actual handlers.** This is the loop the previous
  bullet only half-described: a session plays through a broken feature,
  `harvest-opcodes.txt` names the opcodes, and handlers get written against them.
  Three from the first real login session are now in-tree — `SeeMonsterReq` (full
  `Req`/`Rsp` handshake), `ClientAIStateNotify` (a client-to-server report with no
  reply), and `AnecdoteGetDataReq` (opcode known, no generated proto on 7.0.0, so
  it gets an empty `Rsp` that still answers the client). Two of the three needed
  generated protos the 7.0.0 dump lacks, copied from the reference fork against
  the same protobuf 3.19.6. New handlers take effect on the next `task dev`,
  which rebuilds the jar.
  The second login session added eight more, and those eight are why the harvest
  keys on the CmdId rather than the name: every opcode the dump has no name for
  logs as `UNKNOWN`, and the harvest used to collapse all of them onto one
  `UNKNOWN` entry, so the backlog reported one anonymous opcode no matter how
  many had actually arrived. Entries read `UNKNOWN (2819)` now and the ids
  survive to be pinned. Two of the eight are named by block completion, not by
  lookup — 7.0 reshuffles every CmdId but a feature block's *message set*
  survives, and `ToTheMoonPingNotify` (6117) and `PathfindingPingNotify` (2347)
  are each the single gap in a block whose every other 6.x name already has a
  7.0 home. The other six keep `UnnamedOpcode<id>` placeholders because their
  blocks each still hold several unplaced names, so any assignment would be a
  guess; the id in the name keeps a later real name trivially greppable. All
  eight are no-ops — there is no proto to parse and no `Rsp` to synthesize
  (7.0 scrambles `Req`/`Rsp` adjacency: none of 558 name pairs are adjacent),
  so dropping the packet is the correct behaviour and the point is that the
  opcode stops filling the backlog.
- **Cooking works, and the recipes a new account sees are the real ones.** The
  7.0 dump writes `CookRecipeData`'s ingredient and output lists under swapped
  keys for most rows, so a cook paid the *outputs* and received the *inputs*;
  `onLoad` undoes the swap, and the handler now falls back to the lowest filled
  quality tier for recipes that only define one or two of the three slots rather
  than handing over a `{id:0,count:0}` non-item. The default-unlocked set is
  computed from the excel rather than hardcoded, and it is rebuilt on demand —
  `*Manager.initialize()` runs in the `GameServer` constructor *before*
  `ResourceLoader.loadAll()`, so an eager scan there sees an empty excel map and
  every account used to begin with no recipes at all, with the client showing an
  empty cooking panel. The nine rows it resolves to are pinned by a test.
- **The token exchange stays on a key the client can actually derive.** When the
  RSA handshake in `GetPlayerTokenReq` fails, the server sends a degraded
  response and now *keeps the wire on the dispatch key* instead of switching to
  the session key. The degraded response cannot be used to derive the session
  key, so switching would leave the client transmitting on the dispatch key
  while every reply goes out under a key it cannot reproduce — and the
  receive-side latch that normally corrects an inbound key mismatch cannot reach
  our *send* path. The client retries the exchange every 30–60s, so staying put
  still converges on the next successful request.
- **The session key is visible to every thread that sends a packet.**
  `GameSession.useSecretKey` was a plain `boolean` written once by the token
  exchange on the KCP I/O thread and read by `send()`, which is reachable from
  *any* thread — the game tick, the schedulers, and the world's `eventExecutor`
  for queued teleports. Without a `volatile` there is no happens-before edge, so a
  send on one of those threads kept reading the pre-token `false`, XORed the
  packet with the dispatch key, and shipped it to a client that had already moved
  to the session key. The client decrypted it to noise and reported
  `PACKET_HEAD_MAGIC_ERROR, CmdID:0` (`error_code` 4010) — a *whole-frame* garbage
  read, which is why the CmdId came out as `0` rather than a real opcode. The
  field is `volatile` now. The receive-side latch in `decryptWithEitherKey` was
  already correct: it tries both keys and latches on whichever produces the magic,
  so the *inbound* direction tolerated the disagreement all along. The bug was
  asymmetric — only the send path committed to one key off a possibly-stale read.
  Three client reports in the 2026-09-21 session, each within a second or two of a
  session-key establishment, and *zero* server-side bad-magic warnings in the same
  log — that combination is the fingerprint: the failure was outbound, and the
  server never noticed.
- **A queued teleport after logout is dropped instead of throwing.**
  `queueTransferPlayerToScene` sleeps for its delay on the `eventExecutor`, and a
  player can log out during it — `onLogout` → `World.removePlayer` nulls the world
  reference, and the enter-scene notify then dereferences it unconditionally. The
  NPE was swallowed by the `Future` nobody reads, so the teleport failed
  *silently*; now it logs at trace and returns, because there is nothing to
  teleport: the client is gone and would never have read the packet. The same
  race family as the key-visibility bug above — both are a cross-thread read of
  state another thread tears down.
- **Daily commissions.** The daily-task loop is wired to the real 7.0.0 opcodes
  and the client accepts the three notifies it needs: `DailyTaskDataNotify`,
  `WorldOwnerDailyTaskNotify` and `DailyTaskProgressNotify`. This one is worth a
  note, because it was silently dead and the monitor is what found it: the
  messages were ported from a 6.6 fork with CmdIds guessed against a 6.7 dump,
  and the 7.0.0 client has no message registered for any of them, so it rejected
  every packet with `PACKET_INVALID_CMD_ID` and dropped it whole. Commissions
  appeared to do nothing, with no server-side error at all. The client's own
  telemetry named the bad ids (`CmdId=24670`, `28030`, `24983`), the real values
  are `7522` / `1939` / `23872`, and they now come from `PacketOpcodes` rather
  than literals, so they cannot drift out of sync with the opcode table again.
  The message *field numbers* are still reasoned, not harvested: only
  `TakeDailyTaskScoreRewardReq/Rsp` exist among the generated protos, so a wrong
  field now reads as garbage instead of the packet vanishing — a strictly better
  failure to debug. See the harvest item under "What does not work".
- **The Stormterror quest is finishable.** Every stock Lua for
  `EVENT_SPECIFIC_MONSTER_HP_CHANGE` compares `evt.param3` against a
  whole-number *percentage* — `if evt.param3 > 20 then return false end` under the
  comment `判断指定configid的怪物的血量小于%20时触发` — and the dragon domain
  (scene 20020, group 220020001) gates quest 35722 击退风魔龙 on exactly that check
  reaching `AddQuestProgress`. The server was sending **absolute current HP** as
  `param3`, so the value was always orders of magnitude above any threshold, the
  condition returned false on every hit, the quest-progress key `220020001` stayed
  at `0` forever, and the chain dead-ended the moment the player entered the domain
  — 35721 would sit `FINISHED` with 35722 unreachable behind it.
  `EntityMonster` now sends `Math.round(clamped cur/max * 100)`, clamped to 0–100,
  with 0 for a dead or uninitialised monster (which every threshold counts as
  "below"). The conversion is pinned by `MonsterHpPercentTest`. Upstream Grasscutter
  has the same bug — this is a fix, not a local regression.
  This is not one quest's fix. The same comparison appears **102 times across 74
  stock scripts** — every boss with a phase-change or enrage mechanic reads
  `evt.param3` as a percent, with thresholds 20, 30, 33, 50, 66, 70, 75 and 80.
  Absolute HP cannot satisfy any of them, so *every* boss mechanic of this kind was
  dead on this server, silently, with no error anywhere.
- **The artifact upgrade screen stops popping a null-exception panel.**
  `InventorySystem.upgradeRelic` has three early returns — relic missing,
  no exp gain, payment failed — and only the happy path ever answered the client.
  A request the server *rejected* was indistinguishable from a request the server
  *ignored*: the client holds the response handle and surfaces a dialog when it
  never arrives, and because nothing went wrong server-side no instrumentation
  saw it. All three now send `ReliquaryUpgradeRsp` with a real retcode
  (`RET_ITEM_NOT_EXIST` / `RET_ITEM_INVALID_USE_COUNT` / `RET_ITEM_COUNT_NOT_ENOUGH`),
  and the new `PacketReliquaryUpgradeRsp(int)` ctor is the reason that is one line
  each. This was the visible half of the developer-mode feature below — the other
  half is finding the next one like it.
- **Artifact shop** — every official 5-star piece, rolled fresh per purchase.
  Configurable; see the table at the bottom.
- Updated mob and gadget spawns up to version 5.4, drops, the inbox, widgets, and
  the weekly boss.

## What does not work (yet)

The honest list, because "WIP" in the header is doing real work here. These are
known-broken, not unknown:

- **Daily commissions are field-guesses.** The CmdIds are correct 7.0.0 values
  now (see above), but the message *layouts* — which field number holds the task
  id, the progress, the reward id — are reasoned by hand, not read from the
  client. Only `TakeDailyTaskScoreRewardReq/Rsp` have generated protos in-tree.
  A wrong field shows up as garbage in the commission panel instead of the
  packet vanishing silently, which is debuggable rather than invisible, but it
  is still a guess. Fixing this needs a real proto harvest (below).
- **Proto harvesting is the single biggest unblock.** `debug/harvest-opcodes.txt`
  already collects every opcode the client sends that nothing handles, and eleven
  of its entries are now handled (above). But the opcodes are the easy half; the
  *schemas* are what is missing, and they gate roughly every feature left in the
  harvest list — the daily-task layouts above are just the one that bit hardest.
  `AnecdoteGetDataReq` is the cautionary case: the opcode is in `PacketOpcodes`,
  no `.proto` exists for it anywhere in the 7.0.0 dump or the reference fork, so
  the handler answers with an empty body rather than reading the request.
  That is also why six of the eleven still answer to `UnnamedOpcode<id>`:
  `PacketOpcodes` can place the *number* (the harvest always could) but nothing
  in-tree can name it, and guessing a name from block position would only hide
  which entries are still unverified.
- **Anything not in "What works".** If it is not listed above, assume it does
  nothing. The client reaching for real SDK gateways
  (`ConnectGateFailure`, `SafeConnect failed`) in `debug/telemetry.log` is
  expected on a private server and is not a bug here.
- **Combat ability noise.** `AbilityInstErrorTrue/False` lines and a recurring
  `UnhandledCombatType: COMBAT_SPECIAL_MOTION_INFO typeVal=19` appear in the
  client log at a low rate. Not crash-grade, and not yet investigated.

## Requirements

- **[Java 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)**
  or newer, on `PATH`.
- **Docker Desktop with Compose**. Both modes use the Compose `mongo` service
  and its named `mongo-data` volume by default. `task serve` starts this service
  automatically. For a separate existing database container, override both
  `LUNAGC_MONGO` and `LUNAGC_MONGO_URI` in `.env.local`.
- **[NodeJS](https://nodejs.org/) 20** — only for handbook generation. Skip it
  and pass `-PskipHandbook=1` if you do not want the handbook.
- **[Rust](https://rust-lang.org/learn/get-started/) + Cargo** — only for the
  client patch. The patch crate needs the **nightly** toolchain and the `windows`
  crate, both of which `cargo` fetches.
- **The CN game version 7.1.0** (`YuanShen.exe`) for testing the migrated protocol.
- **[Go Task](https://taskfile.dev/)** — the runner every command below goes
  through. On Windows, `winget install Task.Task` or `scoop install task`.

## First-time setup

Use the clone commands above. An existing checkout can instead run:

```sh
git submodule sync -- patch resources
git submodule update --init patch resources
git -C resources lfs pull
```

The patch submodule is pinned to the announcement RSA fix in
[invoker-bot/animegamepatch](https://github.com/invoker-bot/animegamepatch).
The resources submodule is pinned to a commit
of [LunaGC-Resources](https://github.com/invoker-bot/LunaGC-Resources).

Then:

1. **Resources.** Use the `resources/` Git submodule for a deployed checkout.
   Git LFS is required for its larger files. For local development with a
   separate resource checkout, put
   `LUNAGC_RESOURCES_DIR=D:/Projects/Experiment/LunaGC-Resources` in the ignored
   `.env.local`. `task serve` and `task dev` load this file and read that
   directory directly. Restart the server after changing resources that are
   loaded at startup. For a direct `java -jar` launch, export the same
   environment variable in the shell.
2. **`.env`.** Copy `.env.example` to `.env`. `.env.local` overrides it. Everything
   else in the template is optional —
   `GAME_PATH` can stay empty and the game directory is read from the miHoYo
   launcher registry. Set it only if the client lives outside the launcher.
3. **Build.** `task build` makes both the patch DLL and the server jar. See below.
4. **Patch the client.** `task patch`. See below.
5. **Start the server.** `task serve`.

To advance the deployed resource version, run:

```
git submodule update --remote resources
git -C resources lfs pull
git add resources
git commit -m "Update resources submodule"
```

A deployment then uses that same pinned commit via
`git submodule update --init resources`.

### Docker Compose runtime

The Compose runtime builds the server jar and copies the checked-out resources
submodule into the image. Initialize Git LFS before building; the Dockerfile
rejects resource pointer files. The runtime does not read `.env.local`.

```
git lfs install
git submodule update --init resources
git -C resources lfs pull
docker compose up --build -d
docker compose logs -f server
```

Check container health with `docker compose ps`. The HTTP service should
respond on `http://127.0.0.1:8088/` once startup finishes. Use
`docker compose down` to stop the stack without deleting its data volumes.

Set `LUNAGC_PUBLIC_ADDRESS` in `.env` (or in the shell) to the IP address or DNS
name clients use to reach the machine. The default `127.0.0.1` is for testing
on the same machine. Compose publishes HTTP on TCP 8088 and the game server on
UDP 22101. MongoDB is also published on `127.0.0.1:27017` for local development
and is not exposed on external interfaces. Set `LUNAGC_MONGO_PORT` in `.env` to
change this host port. MongoDB data and
server-generated config/data live in separate named volumes and survive
`docker compose down`; `docker compose down -v` deletes them.

After changing the resource submodule commit or its files, run
`git -C resources lfs pull` and `docker compose up --build -d` again. The image
holds its own copy of the resources, so an existing container does not see later
checkout changes. Both runtime and development use this Compose MongoDB, so
accounts, player progress, inventory and wish history are shared between modes.

### Switching between development and runtime

Stop the current server before starting the other mode; both use the same game
and HTTP ports. To switch from runtime to development:

```powershell
docker compose stop server
task dev
```

To switch back, close the development session and stop the local Java server:

```powershell
task dev:stop
task serve:stop
docker compose up -d server
```

MongoDB stays running through either switch. Server config/data files remain
specific to each mode; only database records are shared. An older `luna-mongo`
container is not used automatically. To migrate its data, stop database writers,
back up both databases with `mongodump`, and restore the old `grasscutter` database
into an empty Compose database using `mongorestore`. Verify document counts,
collection hashes and indexes before retiring the old container. If both
databases already contain player records, reconcile conflicts before restoring.

## Building

```
task build
```

This runs both:

| Task | What it does |
| --- | --- |
| `task build:patch` | `cargo build --release` in `patch/` → `patch/target/release/ext.dll` |
| `task build:jar` | `gradlew jar` → `LunaGC-7.1.0.jar` in the repo root |

`task build:jar` is timestamp-based, not checksum-based: the proto sources are
tens of thousands of generated files and hashing them all takes longer than the
incremental build would. Both tasks are no-ops when nothing changed, so
`task patch` (which depends on `build:patch`) does not rebuild the DLL every
time.

To skip handbook generation, build the jar by hand:

```
.\gradlew.bat jar -PskipHandbook=1 --console=plain
```

The handbook is generated as `GM Handbook.txt` / `handbook.html` in the repo
root; both are gitignored.

## Running the server

```
task serve          # start MongoDB's container check + the server, detached
task serve:status   # is MongoDB up, is the dispatch server listening, is the game server up
task serve:stop     # stop the server (MongoDB keeps running)
task serve:logs     # page through start_stdout.log
```

The server writes `start_stdout.log` and `start_stderr.log` in the repo root and
survives the shell you started it from — it is a detached process, not a child of
the task runner.

Extra server arguments pass through with `-ServerArgs`. The useful one is
`-debug`, which turns on DEBUG logging without packet spam:

```
task serve -ServerArgs "-debug"       # DEBUG logging, no packets
task serve -ServerArgs "-debug all"   # DEBUG logging + every packet
```

Quest progress is only logged at DEBUG. If you want to follow a quest chain in
the log, `-debug` is required; `task dev` sets it for you.

**Create an account before you log in.** `task serve` opens a visible console
window for the server — type into it:

```
account create <name> <uid>
```

There is no web panel by default. If you closed that window, the same command
works from any shell that has the server's stdin; the console is the easy option.

## Patching the client

```
task game-path      # show the resolved client directory without changing it
task patch          # build the DLL if stale, then install it into the client
task patch:status   # what is patched right now, and are the backups intact
task patch:reset    # restore the pristine client files from their backups
```

Run these commands from the repository directory or a subdirectory. The patch
script derives the repository root from its own location, so moving the checkout
does not require changing the script. The client directory is resolved separately:
`GAME_PATH` from `.env.local` / `.env` takes priority; otherwise the script reads
the miHoYo launcher's install path from the Windows registry. It does not search
all disks for a client. Use `task game-path` to confirm the selected directory.
For an explicit override, put this in the ignored `.env.local`, adjusting it to
your installation:

```dotenv
GAME_PATH=C:/Users/InvokerBot/AppData/Local/hoyo/hk4e/versions/current
```

`task patch` does the whole job: it builds `ext.dll` if the sources changed, then
installs the proxy over `Astrolabe.dll` in `YuanShen_Data\Plugins`, rewrites the
40 dispatch URLs inside `AccountPlatNative.dll` and swaps its passport key for
the server's. It keeps `.lunagc-bak` backups next to each patched file so
`task patch:reset` can undo it exactly. Run `task patch:status` whenever anything
about the client looks wrong — it prints the whole state and exits non-zero on a
problem.

Exit the game completely before `task patch` or `task patch:reset`. Both refuse
to modify a running client, whose mapped DLL stays in use until the game exits.

The built DLL is an ignored local build output. Rust nightly is required for the
first build and whenever the patch sources change; `task patch` reuses an
up-to-date DLL.

**While the game is running, `task patch:status` reads the session state, not
the install state.** Both swapped slots hold their pristine image on disk for
the whole session — that is the mechanism that fixes "the client is damaged" —
so the anti-cheat slot reads `stock`, the passport key reads `miHoYo key`, and
each live copy reads `LIVE`. The exit code is 0 and the output names the running
PID. Only after the game exits do those same readings mean the install is
unpatched. `task dev` checks for a running client before it checks the patch, so
it reports the session rather than blocking on the slots.

### Manual install (if you cannot use the task)

1. Build `patch/target/release/ext.dll` with `cargo build --release` in `patch/`.
2. Back up `YuanShen_Data\Plugins\Astrolabe.dll` somewhere safe.
3. Copy `ext.dll` over `Astrolabe.dll` in that same folder.

The URL rewrites and the passport key inside `AccountPlatNative.dll` are not
optional — without them the client reaches the launcher's real passport server
and login fails with "账号或密码错误". `task patch` is the supported way to apply
all three.

### If the game crashed or was force-closed

The patch crate swaps a patched image into its slot while the game runs and moves
it back when the process detaches. **A game that dies mid-session — a crash, a
`taskkill /F`, a power loss — leaves that live copy stranded in the slot**, which
means the file the next launch loads is the pristine one and the client is
unpatched without anyone telling you.

`task patch:status` catches this, and `task dev` fixes it automatically (see
below). From the command line, just run `task patch` again — it detects the
stranded copy and re-deploys.

## Developer debug sessions — `task dev`

```
task dev            # server in -dev mode + patch check + client + the monitor
task dev:stop       # close the game and wait for the session report
task dev:status     # is a session being recorded, and by which process
task dev:report     # print the latest session report
```

`task dev` is the one command for debugging a play session. It starts the server
with `-dev` (reusing one that is already listening, and saying so if it was not
started with `-dev`), checks the patch state and refuses to launch if the client
is not launch-ready, launches `YuanShen.exe`, then exits and leaves a **hidden,
detached monitor process** in charge. The monitor outlives the terminal that ran
the task; it stops when the game does.

Pass `-SkipPatchCheck` to launch with the client as-is (`task dev -SkipPatchCheck`),
useful after a manual `task patch`.

### Developer mode — `-dev`

`-dev` is `-debug` plus instrumentation. Beyond DEBUG logging it arms the
**unimplemented-request check**: every packet the server decides it probably does
not implement fires `UnimplementedRequestEvent`, and a listener files each one into
`debug/dev-report/unimplemented.md`. Run the server on its own with

```
task serve -ServerArgs '-dev'          # '-dev all' also logs every packet
```

Three reasons are reported, and together they describe every way a player's action
can silently do nothing:

- **`NO_HANDLER`** — no `PacketHandler` is registered for that opcode. The client
  asked for a feature the server has no code for.
- **`HANDLER_THREW`** — a handler exists but raised, so the action was abandoned
  halfway. Already an error in the log; this collects it with the others.
- **`NO_RESPONSE`** — the handler ran to completion and never sent a packet back.
  **This is what an in-game error dialog usually means.** The client is waiting on
  the answer to its request and never gets one, and because nothing went wrong
  server-side, no existing instrumentation saw it — a two-hour session can be
  `CLEAN` while the player is staring at an error panel.

Each entry names the opcode, the player and scene it came from, the payload's
fields (`{no:wire=value, ...}`, enough to hand-write the proto), and a next step
telling you where to add the handler or which early return to make answer. One
entry per `(reason, opcode)` per session, so a player hammering a broken button
fills the file once, not forever.

The listener is registered by `Grasscutter#main` only in developer mode, and it is
a server-internal event handler rather than a plugin, which is why it has no
registrar. A plugin that implements the packet itself can cancel the event to stop
the report — `HandlerPriority.LOW` means a plugin that claims the request runs
first.

### What the monitor records

The monitor watches the game process and the server log until the game exits,
then writes a report. Everything lands in `debug/` (gitignored):

| File | Contents |
| --- | --- |
| `report.md` | The session summary — read this first |
| `report-<yyyyMMdd-HHmmss>.md` | A timestamped copy, so old sessions are not overwritten |
| `session.json` | The same data as machine-readable fields |
| `all.log` | Every server line from this session, nothing before it |
| `dev-report/unimplemented.md` | What developer mode filed — see above |
| `error.log` | Server errors and exceptions only |
| `unhandled.log` | Every packet that arrived with no handler |
| `harvest-opcodes.txt` | The deduplicated opcode list — the work list |
| `telemetry.log` | Client-uploaded telemetry the server received |
| `quest.log` | Quest accepts, completions and finishes |
| `index.txt` | Line counts per bucket |
| `autofix.log` | What the auto-fix did, if it ran |

The report's headline verdict is one of:

- **`CRASHED`** — the game exited with a non-zero code, or Windows Error
  Reporting logged a new crash for `YuanShen.exe` during the session, or a new
  dump appeared in `%LOCALAPPDATA%\CrashDumps`.
- **`ERRORS`** — no crash, but the server logged genuine errors.
- **`CLEAN`** — neither.

The report also carries the game's **exit code as an eight-digit hex**
(`0x0000002A`, not `-42`), the **WER event count against a baseline taken when the
session started** — so a machine with a long history of old crashes does not
report a false `CRASHED` — and the number of new crash dumps.

### Crash and error detection, and how it triggers a fix

The monitor is built around one observation: a private server's most common
silent failure is a packet the server does not handle. So the monitor separates
three things that a naive `grep ERROR` conflates:

1. **Real server errors** — `ERROR`, `Exception`, `SEVERE` in the server's own
   log → `error.log` and the report's error count.
2. **Client telemetry** — the client uploads JSON bodies containing `SuperDebug`,
   `"error_code"`, `WarningAlarm`, `PACKET_HEAD_MAGIC_ERROR`. These say "error"
   and trip a naive filter, but they are the client talking about itself, not a
   server fault. They go to `telemetry.log` only, and are explicitly excluded
   from `error.log`.
3. **Unhandled packets** — the `... arrived and nothing handles it - N bytes -
   fields {...}` lines the server emits at INFO for any opcode with no handler →
   `unhandled.log`, deduplicated into `harvest-opcodes.txt`.

`harvest-opcodes.txt` is the actionable part. It is a sorted, deduplicated list of
every opcode the client sent that the server ignored, so a session that walks
through a broken feature hands you the exact list of handlers to write. This is
how the beginner-quest chain and the login chain were found. Entries are keyed on
the **CmdId**, not the name — the 7.0 dump has no name for most unhandled
opcodes, so they all log as `UNKNOWN`, and keying on the name would fold the
whole backlog into one `UNKNOWN` line no matter how many had arrived. Entries
read `UNKNOWN (2819)`, and that number is what you pin in `PacketOpcodes` to
close the loop.

**The harvest reads the server's whole log, not just the slice the monitor
watched.** The monitor's lifetime is the game's — it stops when the client exits —
but the server is not part of the session and keeps listening, so a login that
happened after the game closed sent its packets to an empty room and
`unhandled.log` stayed at zero bytes while the backlog really grew. That is not
hypothetical: the session recorded on 2026-09-21 from 15:41 was clean and the
monitor wrote its report at 18:10, but the same server was still listening at
22:22 when a client logged back in, cooked three recipes, and walked the world
until 22:43 — eleven unhandled opcodes, all of them invisible to the report that
had already been filed. So at harvest time the monitor re-scans the server's own
stdout log as well as its own bucket, because the announcement is the
authoritative record: each opcode is announced exactly once per server process,
so the whole file is precisely the set that server still cannot handle, and any
opcode an earlier process announced is still unimplemented and still belongs on
the list. `all.log` and the other buckets remain session-scoped; only the
harvest reconciles, because only the harvest is a work list rather than a
transcript.

The **auto-fix** is deliberately narrow: if the session ended with a patched image
stranded in a slot (the "client is damaged next launch" condition), the monitor
re-runs the patch deploy itself and logs it to `autofix.log`. Everything else is
reported as evidence for you, not guessed at.

### Reading a session

```
task dev:stop       # closes the game; the monitor writes the report and exits
task dev:report     # prints debug/report.md
```

A typical loop: `task dev`, play until something breaks, `task dev:stop`, read the
verdict, then:

- `CRASHED` → check the exit code and `debug/error.log`; if the crash is in the
  client, `debug/telemetry.log` often holds the client's own account of it.
- `ERRORS` → `debug/error.log` for the stack, `debug/all.log` for the surrounding
  lines.
- a feature that silently did nothing → `debug/harvest-opcodes.txt`; if the
  feature's request is there, the handler is missing or wrong, and the logged
  field values are the request's actual contents.

`debug/all.log` starts exactly at the session boundary — the offset is recorded
once the server is up but before the game is launched, and the monitor seeks
there rather than asking `Get-Content` for "the end of the file", which is
wherever that cmdlet happens to run. The server's startup banner lands above the
boundary on purpose; everything the session itself produced is below it, and
nothing written while the monitor process was still coming up is lost.

**The small buckets look empty until the session ends.** The writers buffer 4 KB
before flushing to disk, and a session's worth of unhandled packets or errors is
often well under that — they are held in memory and land when the monitor tears
its writers down in its `finally`, which is after the game exits. `all.log` and
`telemetry.log` are big enough to flush repeatedly and look live mid-session;
`unhandled.log`, `error.log` and `quest.log` do not, and reading them while the
game is still up reads as zero bytes. That is buffering, not a capture failure —
`all.log` has the same lines, since every bucket is classified out of the same
ingested stream. Wait for the report.

### Bucket reset warnings

Each session starts by erasing the buckets from the previous one, so `error.log`
is *this* session's errors and not a rerun of the last session's stack traces.
The erase is verified, not assumed: it opens each bucket exclusively and
truncates it, then checks the file's length. A length that is still non-zero
means a handle is still holding the bucket open — the usual cause is a monitor
from an earlier session that never closed its writers, which is what happens
when a session is killed at the terminal instead of ending through the game
exiting.

When the erase is refused, the monitor **moves the bucket aside** to
`<bucket>.prev-session-<HHmmss>` and starts a fresh one, rather than leaving the
session to append onto a previous session's tail. The timestamp matters: two
sessions in a row that both wedge the same bucket would otherwise overwrite each
other's moved-aside file, and the second session's note would point at contents
that were no longer what it described.

If the bucket can be neither erased nor moved — a handle held with an exclusive
share, which is how the pre-fix writers opened their files — it is reported and
**skipped for the session**: the monitor marks that bucket dead, writes nothing
to it, and its lines never reach this session's counts. That is why a session
whose `all.log` was wedged still has a complete `error.log` and `unhandled.log`,
and why the verdict does not come back `ERRORS` on the strength of an old
`error.log` full of someone else's stack traces.

Both outcomes are surfaced in three places:

- a **`## bucket reset warnings`** section in `report.md`, placed above the file
  list it qualifies, which is where to look when a session's buckets look wrong;
- the **`resetNotes`** array in `session.json`, one entry per affected bucket,
  naming the file it was moved to or why it could not be moved;
- a **`[STALE]`** tag next to that bucket's line count in `index.txt`, marking a
  count that is accurate about the file but not about the session.

The moved-aside files are gitignored alongside the rest of `debug/`. Delete them
once you have read them; keeping them is only useful when two sessions need
comparing.

### Questing must stay enabled

`server.game.gameOptions.questing.enabled` defaults to **`true`** and should stay
that way. With it off, `PacketQuestListNotify` and `PacketFinishedParentQuestNotify`
drop every quest that is not already `FINISHED`, and a brand-new account has no
saved quest state at all — the quest log opens empty and the beginner chain never
appears. The old README told you to set it to false; that is wrong for this fork.

Turning it back on for an account that only ever played with it off is safe:
there is nothing unfinished stored, so nothing replays. The dangerous direction
is off **after** on, on an account that has saved progress — that is what replays
cutscenes.

`useEncryption` and `useInRouting` should both be `false`; they already are by
default.

## Artifact shop

Every official 5-star artifact piece - 290 of them, the five slots of all 62 released sets - is on
sale in the general goods store (Blanche's *Second Life*, next to the fountain in Mondstadt).

Each purchase rolls the piece fresh rather than handing over a fixed copy, the way an artifact
domain does: the main stat is drawn from the slot's real pool and the substats from the game's own
affix table, so every number printed on the piece is one the game would print. It arrives at +20
with nine substat rolls on it, and the odds are weighted towards CRIT Rate, CRIT DMG, ATK%,
Elemental Mastery and the DMG bonuses, and towards the top end of each roll. Buying several at once
gives you that many separately rolled pieces.

Tune it under `server.game.gameOptions.artifactShop` in `config.json`:

| Option | Default | What it does |
| --- | --- | --- |
| `enabled` | `true` | Turns the listing off entirely. |
| `shopId` | `1004` | Which shop carries it. `1001` is Paimon's Bargains, straight off the shop menu. |
| `costMora` / `costPrimogems` | `20000` / `0` | Price per piece. |
| `costItemId` / `costItemCount` | `0` / `0` | An item to charge on top of the currencies. |
| `buyLimit` | `0` | Purchases per piece per player. `0` is unlimited. |
| `artifactLevel` | `20` | The upgrade level pieces arrive at, `0`-`20`. |
| `critWeight` | `8` | Weight multiplier for CRIT Rate and CRIT DMG. `1` rolls them as the game does. |
| `damageWeight` | `3` | Weight multiplier for ATK%, Elemental Mastery and the DMG bonuses. |
| `highRollBias` | `3` | How hard each stat leans towards the best of its four values. `0` rolls evenly. |

Setting the last three to `1`, `1` and `0` gives you plain, unweighted domain rolls.

## Troubleshooting

- **"the client is damaged"** — the client's integrity check saw a patched file it
  does not expect. Run `task patch:status`. If the passport slot or the
  anti-cheat slot reads `stock`, `PARTIAL`, `STALE` or shows a stranded live copy,
  run `task patch` and relaunch. This was a recurring crash at ~100 seconds into a
  session; the in-process swap in the patch crate is what fixed it, and a
  stranded copy from a force-closed game is the one way it comes back.
- **"账号或密码错误" (account or password error)** — the passport key inside
  `AccountPlatNative.dll` was not swapped for the server's. Run `task patch`.
- **"无法连接网络" (cannot connect to network)** — the dispatch server is down, or
  the URLs in the client still point at the real ones. Check `task serve:status`
  and `task patch:status`.
- **Quest log is empty / no beginner quest** — `questing.enabled` is off. It
  defaults to on; see the section above. Also confirm the server is running with
  `-debug`, since quest progress is only logged at DEBUG.
- **Temple trial characters remain after the quest** — quest trials use a temporary
  party and are removed when their parent quest finishes. Logging in repairs older
  saves with leftover trial records or oversized parties, preserves owned characters
  and equipment, and restores a character reward missed because of a trial copy.
  Permanently obtained Amber and Kaeya remain available in the character roster.
- **Crates drop piles of ingredients** — wooden crate drops use the resource table's
  individual probabilities (typically 3–6% per ingredient). A crate's death is settled
  once, including overlapping lethal hits and self-destruction notifications.
- **No account / login refused** — create one first. The server has no web panel
  by default; use the console command `account create <name> <uid>`.
- **MongoDB connection timeout** — check the service. On Windows, `Win+R` →
  `services.msc` → look for the MongoDB Server entry and start it. On Linux,
  `systemctl status mongod` and `systemctl start mongod`; if that gives error 14,
  fix the ownership of the data directory and the socket
  (`sudo chown -R mongodb:mongodb /var/lib/mongodb` and
  `sudo chown mongodb:mongodb /tmp/mongodb-27017.sock`) and try again.
- **Windy** — put your `.luac` files in `C:\Windy` (create the folder if it does
  not exist).
- **The cooking panel shows no recipes** — the default-unlocked set is computed
  from `CookRecipeExcelConfigData` at login, so a server started before the
  resource checkout was ready, or a resource tree missing that excel, yields an
  empty panel. Confirm `ExcelBinOutput/CookRecipeExcelConfigData.json` exists
  under the configured resource directory. The set rebuilds on demand while it
  is still empty, so a re-login after the resources are in place is enough.
- **A feature silently does nothing** — run `task dev`, reproduce it,
  `task dev:stop`, and look at `debug/harvest-opcodes.txt`. If the feature's
  request is there, the server received it and had no handler for it. Most entries
  read `UNKNOWN (<number>)` rather than a name — the 7.0 dump has no name for
  them, and the number is what you search `PacketOpcodes` for.

## Repository layout

```
patch/          Rust cdylib -> ext.dll, the client patch (git submodule)
src/            the server
  main/proto/    tracked protobuf definitions, including recovered legacy schemas
build/generated/source/proto/  generated Java; ignored by Git
tools/          every task command lives here as its own .ps1
debug/          `task dev` session output (gitignored)
resources/      LunaGC-Resources (git submodule)
Taskfile.yml    the task definitions
.env            machine-local settings, copied from .env.example (gitignored)
.env.local      machine-local overrides, including external resources (gitignored)
```

Everything in `tools/` that the Taskfile invokes is named without a leading
underscore; the `_`-prefixed files are ad-hoc analysis scratch and are gitignored.

## Credit

girluh's [LunaGC](https://github.com/girluh/LunaGC)

kitkat's [patch](https://github.com/capyb2222/animegamepatch)

Terax for nt
