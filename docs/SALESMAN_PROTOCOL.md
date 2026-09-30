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
| KOPLLPLDGGH / has_talked | 12 | bool | +0x24 |
| JIDDOIEGKNP / day_index | 15 | uint32 | +0x28 |

所有字段号均通过本次原生读写函数核对。map 的静态初始化器在 `0x1523efc89`
写入 tag 34（字段 4）；读取分支在 `0x1523efdc0` 比较 tag 34。
状态枚举的原生定义 30521 明文包含 None/Unstarted/Started/Delivered，
默认值表核对数值为 0/1/2/3。日序、状态、奖励映射与历史同名结构关联。

`has_talked` 是服务端采用的语义推断名：类型 69706 的返回处理器
`0x148eb9b00` 检查 ActivityInfo 的详情分支 15，再在 `0x148eb9b7f` 将详情 `+0x24` 标记为 1。
该字段与立本交互状态的具体客户端显示仍待实机核对，原生明文语义未恢复。
其余混淆字段暂不赋值，尤其不能把某个 uint32 直接猜成剩余开匣次数或特殊奖励。

## 未完成

当前详情处理器填日序、状态、已领映射；首期进度结构保存已提交天数与累计机会。
领奖请求和回包 `.proto` 已添加，但收包处理、材料扣除及奖励发放尚未接入。
立本交互、提交消息、各个计数字段、匣子位置编号和特殊奖励概率的原始使用方式仍需核对。
完整兑换与客户端界面尚未验证，不据此标记活动已还原。
