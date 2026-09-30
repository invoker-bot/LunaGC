# 百货奇货首期协议核对

本页只覆盖活动 5003 的首期。使用用户确认已更新的本地 7.1.0 客户端，
文件哈希及元数据提取方式沿用[烘炉协议核对](CRUCIBLE_PROTOCOL.md)。
客户端文件和提取元数据保留在被忽略的 `local/activity-research/`，不随仓库发布。

## 原生证据与语义边界

| 消息 | 客户端类型 | 包号 | 原生函数 |
| --- | --- | --- | --- |
| 活动详情 | 53563 / BOLEBJMCFOF | 内嵌消息 | 写入 `0x1523ef000`，读取 `0x1523efd10` |
| 领奖请求 | 47715 / KPINGNNGEKP | 21130 | 写入 `0x152c9b090`，包号返回 `0x152c9b2e0` |
| 领奖回包 | 75189 / AHMIMIDGNDM | 29368 | 读取 `0x150fd17d0`，包号返回 `0x150fd17b0` |
| 材料提交请求 | 81069 / AELKIAEKDLK | 988 | 写入 `0x14ab867a0`，包号返回 `0x14ab868d0` |
| 材料提交回包 | 69706 / FGEJKJEJMDM | 28807 | 读取 `0x14bbc7130`，包号返回 `0x14bbc7390` |

领奖消息的语义名称与项目已有 7.1 包号表相符；字段含义通过同一版本常见字段名、
原生请求写入及固定版本的
[SalesmanTakeRewardReq](https://github.com/Hiro420/3.5_protos/blob/d42eec84da01b1b28abb40d8565fbbcb306a8969/deobfuscated/SalesmanTakeRewardReq.proto)
与 [SalesmanTakeRewardRsp](https://github.com/Hiro420/3.5_protos/blob/d42eec84da01b1b28abb40d8565fbbcb306a8969/deobfuscated/SalesmanTakeRewardRsp.proto)
交叉关联。旧版本的字段号不沿用。

7.1 请求写入路径 `0x148ec1130` 使用类型 47715，
在 `0x148ec1169` 将第一参数写入对象 `+0x1c`，在 `0x148ec1181` 将第二参数写入 `+0x18`。
其 protobuf 写入器分别在 `0x152c9b0f6` / `0x152c9b171` 发出 tag 72 / 104，
对应 `schedule_id = 9`、`position = 13`，均为 uint32。

回包读取器将 tag 16 写入 `+0x24`（schedule，字段 2），tag 80 写入 `+0x1c`
（reward，字段 10），tag 112 写入 `+0x20`（retcode，字段 14），
tag 120 写入 `+0x18`（position，字段 15）。客户端返回码字段为 int32。
协议测试使用独立的原生 tag 字节构造请求和回包，包括负返回码。

材料提交的发送路径为 `0x148ec9c00`。从详情确认动作 `0x15140d960` 进入此路径，
将排期写入对象 `+0x18`（`0x148ec9c35`）。对应类型指针 `0x1457d01a0`
通过元数据 usage 的 kind 1 记录核对为类型 81069；同一地址有其他 kind 的记录，
不能混用。写入器 `0x14ab867fe` 发出 tag 120，因此请求 `schedule_id = 15`。
回包读取器在 `0x14bbc7168` / `0x14bbc716d` 比较 tag 96 / 112，
分别写入 `+0x1c`（返回码，字段 12）与 `+0x18`（排期，字段 14）。
字段类型从同一客户端字段表核对为 int32 / uint32。
结构同时参考固定版本的
[SalesmanDeliverItemReq](https://github.com/Hiro420/3.5_protos/blob/d42eec84da01b1b28abb40d8565fbbcb306a8969/deobfuscated/SalesmanDeliverItemReq.proto)
及 [SalesmanDeliverItemRsp](https://github.com/Hiro420/3.5_protos/blob/d42eec84da01b1b28abb40d8565fbbcb306a8969/deobfuscated/SalesmanDeliverItemRsp.proto)；
旧包号和旧 tag 不沿用。

提交回包名称还通过本次客户端的明文事件枚举交叉核对：类型 62730 中
`SalesmanDeliverItemRsp`（字段 306076）的原始默认值是 801。
类型 69706 的处理器发送事件 801，与该枚举直接对应；
领奖处理器发送 800，对应同一枚举的 `SalesManTakeRewardRsp`（字段 306075）。
因此提交回包的名称已由同一客户端的事件表确认，不再仅依靠字段形状推断。
活动枚举 22062 的 `NEW_ACTIVITY_SALESMAN` / `NEW_ACTIVITY_SALESMAN_MP`
默认值分别为 3 / 1205，保持首期与后续版的类型区分。

## 详情

| 原生字段 / 服务端名 | 字段号 | 类型 | 对象偏移 |
| --- | --- | --- | --- |
| AMMODINPKOF | 1 | uint32 | +0x2c |
| FLILLCHPLLH | 3 | uint32 | +0x30 |
| HOPKPPOAEHI / selected_reward_id_map | 4 | map<uint32,uint32> | +0x18 |
| MOPCCGMDDAE | 6 | uint32 | +0x34 |
| IKOGFJJCIJD | 7 | bool | +0x25 |
| FBDGELFLLGI | 8 | uint32 | +0x20 |
| PGMJOECPHIN | 9 | uint32 | +0x38 |
| JFNKKHOLNEN / status | 10 | SalesmanStatusType | +0x3c |
| KOPLLPLDGGH | 12 | bool | +0x24 |
| JIDDOIEGKNP / day_index | 15 | uint32 | +0x28 |

所有字段号均通过本次原生读写函数核对。map 的静态初始化器在 `0x1523efc89`
写入 tag 34（字段 4）；读取分支在 `0x1523efdc0` 比较 tag 34。
状态枚举的原生定义 30521 明文包含 None/Unstarted/Started/Delivered，
默认值表核对数值为 0/1/2/3。日序、状态、奖励映射与历史同名结构关联。

此前将字段 12 推断为 `has_talked` 并据遇见立本状态赋值，此推断已撤回。
类型 69706 的提交返回处理器 `0x148eb9b00` 在返回码为 0 时，读取活动类型 1205，
检查 ActivityInfo 详情分支 15，再在 `0x148eb9b7f` 将 `+0x24` 标记为 1。
服务端现保留原生名，并按照当日已提交材料状态赋值，不用跨日交谈状态冒充提交状态。
该结构同时被后续联机版界面使用，原生明文语义以及首期界面的具体显示仍待实机核对。
其余混淆字段暂不赋值，尤其不能把某个 uint32 直接猜成剩余开匣次数或特殊奖励。

## 服务端提交与未完成部分

提交收包处理器已连接首期活动处理器、每日材料检查及库存扣除。有效提交生成一次持久化开匣机会，
重复包、低等阶、关闭或旧排期均不会扣材料。提交成功后回传活动详情并刷新活动条件。
处理器和排期刷新使用同一个玩家活动管理器锁，进度对象另做同步，避免并发提交或排期重建覆盖。
扣除前先保存 `pendingDeliveryDay`；扣除明确失败会释放预留，成功后才增加机会。
扣除或最终保存异常时，不自动重放结果未知的扣除。恢复后的 pending 记录拒绝再次提交；
管理员需要核对库存与进度后处理该记录，不能仅删除 pending 来重试。

首期的真实活动组是 `305003001`，不是另一个包含 NPC 30001 的常驻组 `133004062`。
出生表 `BinOutput/Scene/SceneNpcBorn/scene3_npcborn.json` 中的条目 936–942，
`_configId = 30001`，分别归属套件 1–7，保存七天位置与实际朝向；
该出生表的 `_configId` 是 NPC 类型编号，不是活动条件引用的 Lua 配置编号。
`scene3_dummy_points.lua` 的 `Activity_5003_01–07` 是追踪提示位置，朝向为零，
不应代替出生表中的朝向。当前资源未提供组 `305003001` 的 Lua 文件。

服务端现在通过既有 `GroupSuiteNotify` 发出原始组号和当天套件，
通过 `GroupUnloadNotify` 清理换日、范围离开、关闭和场景退出的旧套件。
NPC 由客户端创建；活动出生条目由专属控制器处理，避免普通 NPC 流式加载覆盖当天套件。
这一机制没有添加虚构组或服务器 NPC 实体，实际 7.1 客户端能否正确显示七套 NPC 仍待实机确认。

已接入归属活动 5003 的对话 `4100101–4100114`。每日初见只记录当前日，
使用活动进度而不是缺失的主任务存档；提交/领奖选项沿用资源条件 5003001/5003002。
客户端条件 5003101–5003107 按当天初见进度同步，未核实的 5003108 不强行设为已满足。
拒绝旧日或条件不符的对话，失败回包不再默认成功。已有普通 NPC 对话路径保留原有接口。
提交要求当前套件已通知、当天初见完成、场景加载完成并在出生位置十米内。
十米为服务器交互检查，未声称是客户端原始配置值。
领奖请求和回包 `.proto` 已添加，领奖收包处理、奖励分配及发放尚未接入。
各个计数字段、匣子位置编号和特殊奖励概率的原始使用方式仍需核对。
完整兑换与客户端界面尚未验证，不据此标记活动已还原。
