# 原素烘炉的客户端协议核对

本次分析使用用户确认已更新的 CN 7.1.0 客户端，只读取本地文件。
类型索引和地址严格绑定以下构建：

- `YuanShen.exe` SHA-256：`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`
- `global-metadata.dat` SHA-256：`469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161`
- `startup-metadata.dat` SHA-256：`90848967d3f9432e7a13caffcec999bf457ac3be1f2ae86336443a63acbaadcf`

没有用辅助 JSON 或 `config.ini` 判定版本。客户端及完整提取结果保存在本地，
不随 Git 仓库、jar 或 Docker 镜像发布。

## 消息与包号

客户端方法 `AEGNNPENLNM` 的以下实现都是 `mov ax, imm16; ret`，
通知立即数与项目已有的 7.1 `PacketOpcodes` 一致，新恢复的请求编号也记录在此表中。消息身份还通过玩法处理函数、
字段引用和原生 protobuf 读取分支交叉核对。

| 消息 | 混淆类型 / 索引 | 包号 | 包号函数地址 | 读取函数地址 |
| --- | --- | ---: | --- | --- |
| `MpPlayPrepareNotify` | `NEGFJDDPEPF` / 20750 | 21654 | `0x14acdf840` | `0x14acdf6e0` |
| `GadgetPlayUidOpNotify` | `PLNAIHFBMDF` / 29665 | 28806 | `0x14ae2e1a0` | `0x14ae2e1f0` |
| `GadgetPlayStopNotify` | `LKACNOCAHKC` / 33805 | 25650 | `0x1508cc650` | `0x1508cc670` |
| `GadgetPlayDataNotify` | `GHDPDAHBOOA` / 81286 | 20094 | `0x14b1d35f0` | `0x14b1d32b0` |
| `GadgetPlayStartNotify` | `NJEHDFEAHJL` / 81315 | 29148 | `0x14ad266d0` | `0x14ad266e0` |

### 邀请与准备

| 消息 | 混淆类型 / 索引 | 包号 | 包号函数地址 | 读 / 写函数地址 |
| --- | --- | ---: | --- | --- |
| `MpPlayOwnerCheckReq` | `IOIAGINPEJF` / 48491 | 6899 | `0x14fafbe00` | 写 `0x14fafb980` |
| `MpPlayOwnerCheckRsp` | `DEHEMPBKEOM` / 79304 | 8829 | `0x153f737e0` | 读 `0x153f737f0` |
| `MpPlayOwnerStartInviteReq` | `IPMDLDDNAAN` / 41678 | 28718 | `0x149a4a990` | 写 `0x149a4ab20` |
| `MpPlayOwnerStartInviteRsp` | `CFEDMCCHIPB` / 37848 | 8056 | `0x149f48210` | 读 `0x149f48230` |
| `MpPlayGuestReplyInviteReq` | `MNJOAPIBONA` / 30820 | 5376 | `0x15215f490` | 写 `0x15215f570` |
| `MpPlayGuestReplyInviteRsp` | `FMKKGHINFGE` / 22123 | 2563 | `0x14a4a1510` | 读 `0x14a4a1710` |
| `MpPlayGuestReplyNotify` | `NPLNKBICHBL` / 62578 | 21009 | `0x1517527d0` | 读 `0x151752950` |
| `MpPlayOwnerInviteNotify` | `GGJKCBOAKBA` / 69240 | 25124 | `0x15360a990` | 读 `0x15360a560` |
| `MpPlayInviteResultNotify` | `LONKAKAHOPC` / 54528 | 22704 | `0x1514a0670` | 读 `0x1514a0490` |
| `MpPlayPrepareInterruptNotify` | `PHHAPOBMANG` / 87015 | 7851 | `0x14ae14940` | 读 `0x14ae14a40` |

邀请管理器类型 62046 的房主检查发送方法 `0x14f74f3a0` 使用类指针 `0x1457f2820`，
发起邀请发送方法 `0x14f751670` 使用 `0x1457f2c30`。类指针与两个请求类型由元数据初始化表关联。
已知 `OwnerCheckRsp` 的处理函数 `0x14f7380d0` 在成功时进入 `0x14f7385b0`，
其中 `0x14f7389b3` 直接调用上述发起邀请方法，传入响应的 `mp_play_id`。
两次发送都将玩法参数存入 `+0x1c`，将管理器的同一个布尔字段存入 `+0x18`，并调用网络发送入口。
请求类型身份由这条调用链与对应响应交叉核对。

语义名称还参考固定版本的历史定义：[房主检查](https://github.com/Hiro420/3.5_protos/blob/d42eec84da01b1b28abb40d8565fbbcb306a8969/deobfuscated/MpPlayOwnerCheckReq.proto)、
[队友答复](https://github.com/Hiro420/3.5_protos/blob/d42eec84da01b1b28abb40d8565fbbcb306a8969/deobfuscated/MpPlayGuestReplyInviteReq.proto)、
[邀请通知](https://github.com/Hiro420/3.5_protos/blob/d42eec84da01b1b28abb40d8565fbbcb306a8969/deobfuscated/MpPlayOwnerInviteNotify.proto)。
字段编号全部来自上表的本地 7.1 原生分支。邀请通知处理函数 `0x14f73f2d0` 将 `cd` 转为浮点传给弹窗，
服务器使用它发送本次邀请的等待秒数；准备结束时间使用独立的 epoch 秒字段。

| 消息 | 本地 7.1 字段编号、类型及对象偏移 |
| --- | --- |
| `MpPlayOwnerCheckReq` | `is_skip_match` 4 / bool / `0x18`；`mp_play_id` 14 / uint32 / `0x1c` |
| `MpPlayOwnerCheckRsp` | `wrong_uid` 1 / uint32 / `0x1c`；`retcode` 2 / int32 / `0x20`；`is_skip_match` 3 / bool / `0x24`；`mp_play_id` 11 / uint32 / `0x18` |
| `MpPlayOwnerStartInviteReq` | `mp_play_id` 1 / uint32 / `0x1c`；`is_skip_match` 3 / bool / `0x18` |
| `MpPlayOwnerStartInviteRsp` | `is_skip_match` 7 / bool / `0x1c`；`mp_play_id` 8 / uint32 / `0x18`；`retcode` 11 / int32 / `0x20` |
| `MpPlayGuestReplyInviteReq` | `is_agree` 3 / bool / `0x18`；`mp_play_id` 14 / uint32 / `0x1c` |
| `MpPlayGuestReplyInviteRsp` | `retcode` 8 / int32 / `0x18`；`mp_play_id` 11 / uint32 / `0x1c` |
| `MpPlayGuestReplyNotify` | `is_agree` 5 / bool / `0x20`；`mp_play_id` 8 / uint32 / `0x18`；`uid` 10 / uint32 / `0x1c` |
| `MpPlayOwnerInviteNotify` | `is_remain_reward` 6 / bool / `0x18`；`mp_play_id` 8 / uint32 / `0x20`；`cd` 15 / uint32 / `0x1c` |
| `MpPlayInviteResultNotify` | `all_agree` 4 / bool / `0x18`；`mp_play_id` 7 / uint32 / `0x1c` |
| `MpPlayPrepareInterruptNotify` | `mp_play_id` 9 / uint32 / `0x18` |

类型 23204 的玩法管理器分别处理开始、进度、玩家操作和结束消息。
它派发的客户端事件枚举值 895–898 与上述包号属于不同编号空间。

`GadgetPlayStopNotify` 的静态字段编码器引用类型 71290 `INJIGKFJGLP`。
初始化表项 290724 将该类型放入 `0x145809fa0`，随后用于创建字段 2 的消息编码器。
其读取函数位于 `0x14b327670`，对应补入的 `GadgetPlayUidInfo`。
头像子消息引用类型 49812 `MFDLDKCDGCF`；其读取分支与现有 `ProfilePicture` 的字段 1–4 相符。

### 玩家提交请求

`ExecuteGadgetLuaReq` 对应类型 18475 `JNLGGNKIEDH`，包号函数
`0x14cd0b440` 直接返回 **26835**，与项目已有请求编号一致。
该请求的读取方法已被客户端裁剪；写入函数 `0x14cd0aad0` 保留了以下标签：

| 字段 | 编号 / 类型 / 对象偏移 | 写入标签 |
| --- | --- | ---: |
| `source_entity_id` | 8 / uint32 / `0x24` | 64 |
| `param1` | 14 / int32 / `0x18` | 112 |
| `param2` | 9 / int32 / `0x20` | 72 |
| `param3` | 6 / int32 / `0x1c` | 48 |

烘炉管理器的发送方法 `0x1488c6320` 使用初始化表关联的类指针 `0x1457e2cd0`
创建该请求，设置这四个字段，随后交给网络发送方法 `0x14724a3b0`。
这与资源 `Crucible.lua` 的玩家凝块提交入口吻合。

对应 `ExecuteGadgetLuaRsp` 的编号和 7.1 字段仍未确认；保留原负数占位。
现有 `GameSession.send` 会拦截未恢复编号的包，提交请求的服务端处理不依赖该响应。
这尚不能证明客户端完整交互已正常，仍需实机核对。

## 字段编号

表中偏移是客户端对象的字段存储偏移。标签值为 `field_number * 8 + wire_type`。

| 消息 | 字段编号、类型及对象偏移 |
| --- | --- |
| `MpPlayPrepareNotify` | `mp_play_id` 1 / uint32 / `0x1c`；`prepare_end_time` 11 / uint32 / `0x18` |
| `GadgetPlayStartNotify` | `entity_id` 3 / uint32 / `0x20`；`play_type` 5 / uint32 / `0x18`；`start_time` 15 / uint32 / `0x1c` |
| `GadgetPlayDataNotify` | `entity_id` 6 / uint32 / `0x20`；`play_type` 9 / uint32 / `0x1c`；`progress` 10 / uint32 / `0x18` |
| `GadgetPlayUidOpNotify` | `op_name` 1 / string / `0x18`；`uid_list` 5 / repeated uint32 / `0x20`；`entity_id` 7 / uint32 / `0x38`；`param_list` 11 / repeated uint32 / `0x28`；`play_type` 13 / uint32 / `0x34`；`op` 15 / uint32 / `0x30` |
| `GadgetPlayStopNotify` | `cost_time` 1 / uint32 / `0x28`；`uid_info_list` 2 / repeated message / `0x18`；`entity_id` 4 / uint32 / `0x24`；`is_success` 10 / bool / `0x30`；`score` 11 / uint32 / `0x2c`；`play_type` 12 / uint32 / `0x20` |
| `GadgetPlayUidInfo` | `icon` 2 / uint32 / `0x30`；`uid` 3 / uint32 / `0x3c`；`profile_picture` 5 / message / `0x20`；`score` 7 / uint32 / `0x34`；`op` 9 / uint32 / `0x38`；`online_id` 10 / string / `0x28`；`nickname` 11 / string / `0x18` |

玩家操作读取函数同时接受字段 5 的标签 40 / 42，以及字段 11 的标签 88 / 90，
分别对应非压缩与压缩的 repeated uint32。项目使用 protoc 的默认压缩编码。
现有 `GadgetPlayInfo` 的字段 1–6 与烘炉 oneof 字段 21，以及
`GadgetCrucibleInfo` 的字段 1–2，也已与这次客户端的读取分支核对。

字段的语义名称依据玩法处理代码、共享字段引用和原 Lua 接口还原。
`GadgetPlayUidInfo.op` 的具体结算称号含义仍需客户端验证，目前保留默认值 0。

## 服务端接入与验证范围

- 倒计时开始时广播 `GadgetPlayStartNotify`。`start_time` 为倒计时开始的 epoch 秒；
  客户端使用 `GadgetPlayInfo.start_cd` 计算实际开战时刻。
- 每次实际进度变化广播 `GadgetPlayDataNotify`，包括阶段内计分和扣分。
- 原 Lua 的 `GadgetPlayUidOp` 已发送目标 UID、操作名、操作编号与参数列表；
  目标必须在场且属于本轮参与者，截止时间和取消后的操作会被拒绝。
- 成功、超时、主动取消及场景卸载发送 `GadgetPlayStopNotify`。
  成绩与耗时来自不可变的本轮快照；耗时从倒计时结束起计算并限制在挑战时长内。
  参与者的昵称与头像在开局时保存，退出场景后仍可出现在本轮结束列表中。
- 房主检查与发起邀请均验证当前排期、安装的主炉、房主身份、玩家加载状态、活动半径与可用角色。
  当前世界的固定队伍全部同意后，广播 `MpPlayPrepareNotify` 并调用原 Lua 的 `EVENT_MP_PLAY_PREPARE`；
  资源指定的 20 秒准备完成后调用 `EVENT_MP_PLAY_BATTLE`，由 Lua 进入原有的 3 秒开战倒计时。
  主炉的场景实体信息同步 `prepare_end_time`。准备事件完成前不会提交开战事件；不在场景锁中等待 Lua。
- 邀请等待 30 秒，拒绝或超时发送失败结果；准备阶段的队员离场、加载状态变化或越出半径会广播中断，
  执行原 Lua 中断事件。旧邀请序号使迟到的准备和开战事件失效，旧中断也不能重新启用下一次准备的主炉。
  房主离开正在进行的挑战会取消本轮。活动卸载同样取消邀请和准备。
- 当前只支持 `is_skip_match = true` 的本世界队伍；跨世界匹配请求返回 `RET_MP_MATCH_PLAY_NOT_OPEN`。
  尚未提供挑战结算奖励，邀请通知的 `is_remain_reward` 使用默认值 false。
- `ExecuteGadgetLuaReq` 的烘炉入口检查安装中的主炉、当前活动排期、场景代次、
  玩家在场、本轮参与资格与挑战时限。`param3` 必须属于发送玩家的队伍实体，
  `param2` 仅接受提交操作 1；`param1 = 5001` 的服务端点名操作不能从此入口调用。
  校验和 Lua 执行共同持有场景锁，避免卸载或重开发生在两者之间。

测试先验证缺失消息会失败，再核对所有字段编号、实际发包的包号和字节，
覆盖 repeated 的两种编码、重开后的旧快照、取消、延迟超时及现有 Lua / 场景回归。
这些结果确认服务端行为和消息编码；邀请弹窗、准备及完整客户端挑战、点名效果、
结算称号和体力奖励仍需实机验证，跨世界匹配与结算奖励仍待接入。

## 重复提取包号

```powershell
python tools/extract_client_type_names.py 'C:\Users\InvokerBot\AppData\Local\hoyo\hk4e\versions\current' --include-fields --include-methods --include-packet-ids
```

本次构建提取出 4,923 个直接返回包号的类型，包含 4,919 个不同编号。
2002、1013、1025、1017 各有两个类型返回同一编号；工具保留全部记录，
不会按编号覆盖类型或自动替换 `PacketOpcodes`。这些数据提供包号到混淆类型的线索，
其他消息的名称和字段仍需逐项核对，不能直接当作完整的 `.proto` 导出。

## 嵌套泛型类型

工具增加 `--include-generic-types`，需要同时启用 `--include-fields`。
此选项严格校验上述 startup 元数据哈希；普通名称、字段和方法导出保持原有格式。

```powershell
python tools/extract_client_type_names.py 'C:\Users\InvokerBot\AppData\Local\hoyo\hk4e\versions\current' --include-fields --include-methods --include-packet-ids --include-generic-types
```

原生初始化代码 `0x1405365c5` 读取 startup 的 406,563 条八字节泛型记录，
再通过注册表 `0x142871b68 + 0x28` 的 `GenericInst` 获取参数列表。
startup 从文件起点读取，global 元数据则跳过 `0x210` 字节；两个文件的基址不同。
字段导出共解析到 18,822 个不同的泛型实例，可递归显示其泛型定义和参数。
数组元素及完整方法签名仍待恢复。

独立核对的列表负载包括 `GadgetPlayUidOpNotify` 的 `0x71eb`（uint32 参数），
以及 `GadgetPlayStopNotify` 的 `0x22a60`（类型 71290 `INJIGKFJGLP` 参数）。
后者与字段 2 编码器及原生读取分支的结论一致。
