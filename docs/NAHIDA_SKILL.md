# 纳西妲元素战技状态与初始化

## 预期行为

官方 3.2 更新说明中的「所闻遍计」为：点按对附近敌人造成草元素伤害并施加蕴种印；
长按进入瞄准，瞄准结束后对选定敌人造成草元素伤害并施加蕴种印。
因此长按松开后始终没有攻击或标记，不符合技能描述。

来源：[《原神》3.2 版本更新说明](https://ys.mihoyo.com/main/m/news/detail/24520)。

## 状态编号与实例

已修正服务端两处技能状态处理错误：

- 技能动作编号按状态名称排序生成，但收到 `ModifierChange` 时曾按 JSON 原始顺序取状态。
  纳西妲的长按、瞄准、释放、短按命中状态因此可能被解析成其他状态。现在两条路径使用同一排序。
- 状态的 `onAdded` 包含 `ApplyModifier` / `AttachModifier` 时，曾跳过记录客户端状态实例 ID。
  现在保留这个实例，使后续动作及移除消息能继续定位到原状态。

`NahidaSkillStateTest` 读取资源子模块中的真实配置，经 `AbilityManager.onAbilityInvoke` 验证：

| 技能配置 | 状态编号 | 预期状态 |
| --- | ---: | --- |
| `Avatar_Nahida_ElementalArt` | 5 | `Avatar_Nahida_ElementalArt_HoldButton` |
| `Avatar_Nahida_ElementalArt_RayCast` | 5 | `Avatar_Nahida_RayCast_Handler` |
| `Avatar_Nahida_ElementalArt_RayCast` | 10 | `Focus` |
| `Avatar_Nahida_ElementalArt_RayCast` | 7 | `Avatar_Nahida_RayCast_Trigger` |
| `Avatar_Nahida_ElementalArt_Click` | 1 | `Avatar_Nahida_ElementalArt_Click_Strike` |

测试同时检查移除消息清理对应实例、退出瞄准动作的编号，以及短按命中的技能 ID。
这证明服务端状态解析与清理路径通过回归测试；游戏内短按 E、长按 E 松开后的实际表现仍需复测。
本次没有修改角色存档或替换客户端技能资源。

## 2026-10-03：继续核对松开 E 无效

根据本地已校验的 7.1 客户端序列化/解析函数，核对了 `AbilityInvocationsNotify`、
`ClientAbilityChangeNotify`、`AbilityInvokeEntry`、`AbilityInvokeEntryHead`、
`AbilityMetaModifierChange` 的关键字段及 `AbilityString` 字符串/哈希分支。
本次检查没有发现这些关键字段的编号不一致。

`NahidaAbilityInitializationTest` 使用实际纳西妲资源复现并修正了四项初始化问题：

- 重复初始化同一技能会追加多余实例；现在复用同一编号下的技能和参数。
- 客户端重新分配已占用的技能编号时，服务端把新技能追加到末尾；现在更新指定位置。
- 初始化消息携带的技能参数覆盖表被丢弃；现在保留 `Hold_Damage` 等覆盖参数。
- 后续参数更新使用哈希时被直接忽略；现在在该技能已知参数中解析哈希。

这四项测试在修正前均失败，修正后通过；合并原有技能、状态及生命值结算回归共 112 项通过。
它们证明编号与参数初始化的服务端错误已修正，尚不能单独证明游戏内 E 键释放问题已解决。
当时仍缺少实际操作的调用记录，尚未确认命中、标记和退出瞄准流程。

## 2026-10-03：实际调用确认活动技能污染

14:06–14:08 的游戏调用记录显示，普通场景中的纳西妲同时初始化了
`Avatar_Nahida_BossRush_SpecialArt` 和 `Avatar_Nahida_BossRush_SpecialBurst`。
其中前者在进入时将 `_ABILITY_Nahida_IsInBossRush` 设为 `1`；日志也记录到该值。

真实资源中的普通元素战技根据这个值选择 `HoldButton` 或 `Blank` 状态：
值为 `1` 时，正常的按住/松开按键处理被切换为 `Blank`。
随后按 E 的调用中又出现了 BossRush 专用瞄准状态；该状态等待特殊机关，
并有 30 秒的持续时间。这与普通场景按 E 后不能移动、松开也不释放的现象相符。

原因是 `ResourceLoader` 曾按角色名前缀，把全部动态技能加入基础技能目录。
现已删除这条规则：基础技能严格使用 `ConfigAvatar` 或显式的 `AbilityEmbryos` 配置；
动态技能定义仍保留，天赋、命座和装备继续通过 `OpenConfig` 按解锁情况添加。
活动专用技能由对应玩法显式启用，不再自动绑定到普通角色。

`NahidaAbilityEmbryoTest` 使用实际角色、天赋及任务技能资源，验证：

- 普通纳西妲保留 12 个基础技能及其原始顺序，不混入 BossRush 技能。
- 基础技能目录不会自动开启被动天赋或命座。
- 开启一个命座时只添加对应技能，重复添加不会产生第二份。
- BossRush 的技能定义仍可查询，但不会自动生效。

修正前四项测试均失败（基础技能数为 19），修正后通过；合并原有回归共 116 项通过。
新服务上线后需要重新登录，让客户端重新取得技能目录。
测试和实际故障日志已确认此处原因及服务端修正；修正后的游戏内点按、长按释放和移动仍需实测确认。
