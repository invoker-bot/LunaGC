# 原素烘炉的客户端协议核对

本次分析使用用户确认已更新的 CN 7.1.0 客户端，只读取本地文件。
类型索引和地址严格绑定以下构建：

- `YuanShen.exe` SHA-256：`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`
- `global-metadata.dat` SHA-256：`469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161`

没有用辅助 JSON 或 `config.ini` 判定版本。客户端及完整提取结果保存在本地，
不随 Git 仓库、jar 或 Docker 镜像发布。

## 消息与包号

客户端方法 `AEGNNPENLNM` 的以下实现都是 `mov ax, imm16; ret`，
立即数与项目已有的 7.1 `PacketOpcodes` 一致。消息身份还通过玩法处理函数、
字段引用和原生 protobuf 读取分支交叉核对。

| 消息 | 混淆类型 / 索引 | 包号 | 包号函数地址 | 读取函数地址 |
| --- | --- | ---: | --- | --- |
| `MpPlayPrepareNotify` | `NEGFJDDPEPF` / 20750 | 21654 | `0x14acdf840` | `0x14acdf6e0` |
| `GadgetPlayUidOpNotify` | `PLNAIHFBMDF` / 29665 | 28806 | `0x14ae2e1a0` | `0x14ae2e1f0` |
| `GadgetPlayStopNotify` | `LKACNOCAHKC` / 33805 | 25650 | `0x1508cc650` | `0x1508cc670` |
| `GadgetPlayDataNotify` | `GHDPDAHBOOA` / 81286 | 20094 | `0x14b1d35f0` | `0x14b1d32b0` |
| `GadgetPlayStartNotify` | `NJEHDFEAHJL` / 81315 | 29148 | `0x14ad266d0` | `0x14ad266e0` |

类型 23204 的玩法管理器分别处理开始、进度、玩家操作和结束消息。
它派发的客户端事件枚举值 895–898 与上述包号属于不同编号空间。

`GadgetPlayStopNotify` 的静态字段编码器引用类型 71290 `INJIGKFJGLP`。
初始化表项 290724 将该类型放入 `0x145809fa0`，随后用于创建字段 2 的消息编码器。
其读取函数位于 `0x14b327670`，对应补入的 `GadgetPlayUidInfo`。
头像子消息引用类型 49812 `MFDLDKCDGCF`；其读取分支与现有 `ProfilePicture` 的字段 1–4 相符。

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
- 已提供 `MpPlayPrepareNotify` 的发包类；邀请与准备状态机尚未接入它。

测试先验证缺失消息会失败，再核对所有字段编号、实际发包的包号和字节，
覆盖 repeated 的两种编码、重开后的旧快照、取消、延迟超时及现有 Lua / 场景回归。
这些结果确认服务端行为和消息编码；完整客户端挑战、邀请流程、点名效果、
结算称号和体力奖励仍需继续适配和实机验证。

## 重复提取包号

```powershell
python tools/extract_client_type_names.py 'C:\Users\InvokerBot\AppData\Local\hoyo\hk4e\versions\current' --include-fields --include-methods --include-packet-ids
```

本次构建提取出 4,923 个直接返回包号的类型，包含 4,919 个不同编号。
2002、1013、1025、1017 各有两个类型返回同一编号；工具保留全部记录，
不会按编号覆盖类型或自动替换 `PacketOpcodes`。这些数据提供包号到混淆类型的线索，
其他消息的名称和字段仍需逐项核对，不能直接当作完整的 `.proto` 导出。
