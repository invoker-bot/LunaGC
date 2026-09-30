# 未归的熄星协议核对

使用用户提供并确认的 7.1.0 客户端进行只读核对，未修改游戏文件。
本地解析和反汇编保存在被忽略的 `local/activity-research/`，客户端文件不进入仓库。

## 已核对的字段

| 消息 | 本地类型 | 证据 | 结果 |
| --- | --- | --- | --- |
| ActivityInfo 中的 aster_info | BGOGJIOLNCD（44487） | 读取函数 `0x14dc96450` 的 `0x14dc96835`–`0x14dc968db` 分支 | tag `0x247a`，字段 1167，嵌套 NEPAKOBIMCG |
| AsterActivityDetailInfo | NEPAKOBIMCG（31374） | 写入函数 `0x1485fc140`、读取函数 `0x1485fb790` | little 2、large 6、mid 12、progress 15、关闭时间 13、关闭标记 11、特殊奖励标记 7 |
| 两个 uint32 余额槽 | 同上 | 先检查对象偏移 `0x40`，写 tag `0x08`；偏移 `0x3c` 写 tag `0x28` | `DKOEMPLNJBP = 1`、`JAHBDIPMKMI = 5`，替换原先无依据的 50000 / 50001 |
| AsterLittleDetailInfo | HODAKEOMFKK（58966） | 读取与写入函数 | 阶段开启时间 3、状态 6、开放标记 7、阶段 ID 8、第一阶段开启时间 11 |
| AsterLittleInfoNotify | MKMFPJAHOOJ（49161） | 包号返回函数、读取函数 `0x14fdd6ea0` 比较 tag `0x22` | 包号 20608；info 为字段 4，修正旧描述符中的字段 14 |
| AsterMidDetailInfo | FGNFICMLJCN（37674） | 读取与写入函数 | open 6、camp_list 9、begin_time 10、collect_count 15 |
| AsterLargeDetailInfo | OBKGNLGCCFM（64675） | 读取与写入函数 | begin_time 6、open 10 |
| AsterProgressDetailInfo | HJKFPOCOCGF（67314） | 读取与写入函数 | last_auto_add_time 6、count 9 |
| AsterMiscInfoNotify | DEFAMMEJKOA（57890） | 包号返回函数、读取函数 `0x153446370` | 包号 24878；DKOEMPLNJBP 13、JAHBDIPMKMI 15；替换旧版 token 9 / credit 2 |
| SelectAsterMidDifficultyReq | NBKLACHMLFI（79827） | 写入函数 `0x14b776950` | 包号 29428；gadget_entity_id 2、schedule_id 7、difficulty_id 12 |
| SelectAsterMidDifficultyRsp | EFIOBDGHMAO（78529） | 读取函数 `0x150e50230` | 包号 2401；gadget_entity_id 1、schedule_id 3、retcode 12、difficulty_id 15 |

上述对象偏移仅用于核对本次客户端，不能作为其他客户端版本的通用布局。
两个余额槽的**熄星能量（109）/ 熄星精粹（110）对应关系尚未核实**，保留原始混淆字段名，当前服务端不向其中填写推测值。
碎屑的货币 109 累计数与采集记录保存在同一活动文档，采集成功发送物品获得提示；活动界面的余额显示与商店扣费仍需接入。

## 时间与服务端状态来源

排期第 1 天使用实际开始时刻；第 2 天及以后按国服凌晨 4 点换日。
参考固定版本的
[BaseActivity::getBeginTimeByOpenDay](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/player/activity/base_activity.cpp)。
[AsterActivity](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/player/activity/aster_activity.cpp)
说明探索阶段完成全部区域任务后接续下一阶段；内容结束时刻是资源 `activityStayTime + 1` 对应的日界线。
历史实现仅用于语义和状态转换参考，其旧版字段号不能用于 7.1。

服务端接入资源的三章日程（第 1 / 3 / 8 天）、探索两轮任务（第 1 / 2 天）、冒险等阶 20 门槛和 14 天内容期限。
任务集从 `missionVec` 对应的任务逐个展开，不能直接把 `watcherIdVec` 的一个条目当成该轮全部任务。
只加载六处有效探索组，跳过停用的 1200107～1200109。

## 仍需继续完成

- 两个余额槽的语义和 AsterMiscInfoNotify 的发包接入（包号与字段已核对）。
- 第 2 章陨星回收：60 处营地、难度选择、封印战斗、20 树脂领奖和累计任务。
- 第 3 章多人挑战：场景战斗、能量提交、匹配、奖励和进度。
- 活动商店、特殊角色奖励以及剧情组和支线任务。
- 7.1 客户端完整探索流程和后续阶段的实机验证。

当前仅宣告探索阶段开放，第二、第三章详情保留开启时间但不发送可游玩的开放标记。
陨星回收难度请求和回包已恢复描述符，尚未接入战斗处理器，不将协议恢复等同于可开始挑战。
活动目录保持「还原中」，不将已恢复字段或已加载脚本等同于完整可玩。
