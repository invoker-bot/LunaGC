# 往期活动复刻

在 [GM 控制台](http://localhost/gm) 的「往期活动」页管理活动。Docker Compose 同时保留
8088 端口；80 端口只绑定本机，用于上述地址。可通过 `LUNAGC_GM_HTTP_PORT` 更改本机入口端口。

## 时间和排期

- 默认按首次开放时间由早到晚排列，先推进 1.0「原素烘炉」，再推进「百货奇货」、
  「未归的熄星」等活动。
- 「历史开放」是国服的原始开放时间；版本更新后开启的活动保留这一描述，不补造具体钟点。
- 「本次复刻」是当前服务器的排期，和历史时间分别显示。开启默认使用原活动时长，
  也可选择 7、14、30 天；无明确时间的资源条目默认 7 天。
- 关闭后在原排期结束前恢复开启，会保留进度和已领取奖励；重新复刻或过期后再次开启，
  使用新的排期 ID，并重置该活动的进度和领取状态。
- 配置保存到运行时数据目录的 `ActivityConfig.json`，重启后仍有效。
  在线玩家收到排期和详情通知；未在线的玩家登录时读取当前配置。

## 当前还原进度

目录覆盖本资源版本的全部 401 条活动记录，包含剧情、试用和宣传条目。
其中 158 条有可核实的历史时间；其余显示「时间待核实」，不纳入默认的历史时间列表。
共用名称的复刻、测试和取消活动需要逐条对应资源 ID；目前历史日期只标注已对应的首次活动。
「开启」表示排期与活动详情启用，具体能否游玩以每条记录的还原说明为准。

### 第一项：原素烘炉（5001，2020-10-12）

已接入 `MpPlayGroupExcelConfigData.json` 的玩法位置与组数据、7.1 活动详情消息、
原始 `Crucible.lua` 所需的玩家凝块累计值及烘炉进度接口。
原脚本测试覆盖小凝块 300 分、大凝块 1000 分、首次加成提交双倍计分、
重复累计值不重复计分，以及不同玩家和场景之间的数据隔离。

主控组 `305001001` 已从固定版本的
[客户端 Lua 备份](https://github.com/Ahanlei123/2.8_live_data/blob/f25e3155e38f9dc8f647493bf23a935147caf59e/lua/activity/5001/activity5001_group305001001.lua)
恢复，随 jar 打包；资源目录中的同路径脚本优先。来源和原始哈希保存在
`src/main/resources/historical-scripts/SOURCES.md`。
服务端已解析它的 `crucible_config`，接入 3 秒倒计时、900 秒挑战时限、
5,000 / 20,000 / 35,000 分的阶段回调、成功/超时/取消事件和重开后的清零。
原 Lua 的阶段组选择与七个关联组的清理流程已通过测试。
主控组已接入 scene 3 的实际地图 block 3003；玩家接近活动区域时加载主控与七个关联组。
每个世界有独立的脚本绑定和场景轮次，不修改共享地图元数据或静态 group grid。
关闭、到期、重新复刻或空场景销毁时卸载实体、触发器、区域和计时器，清空场景组缓存；
旧轮次的异步事件不能进入新场景。远离区域时，正在进行的挑战保留到本轮结束。
场景重载会清空正在进行的挑战；原排期的玩家任务进度和已领取状态仍然保留。
已接入资源中的全部 13 项活动任务：炼金次数、累计效率、指定组击杀数，以及同世界等级的三分钟内通关。
最后一项的参数 `720` 表示 900 秒挑战剩余至少 720 秒，边界包含恰好三分钟。
挑战开始时固定区域内参与者与世界等级；效率只计给提交者，重复累计值、非参与者、
倒计时期间和截止时刻的提交不会增加任务进度。击杀按实际攻击者所属角色计数，每个怪物仅记一次；
脚本清场不会增加击杀任务。任务进度沿用数据库保存、进度通知和奖励领取流程。
每轮挑战有独立序号；旧轮次的异步脚本与计时器不能进入下一轮。挑战结束的 Lua 清理完成后才能重开。
清理怪物时，死亡回调排队到场景锁释放后执行，避免脚本相互等待；结束后不会再运行刷怪死亡回调。
`CreateMonster` 已按原 Lua 的 `delay_time` 延迟 5 秒或 10 秒重生。
阶段切换、刷新或卸载组时取消旧的重生任务；已被调度器取出的任务也不能在清理后生成怪物，
活动的旧场景或旧挑战任务不会进入新轮次。跨组清场按 Lua 指定的 `group_id` 查找目标实体。
这些验证覆盖服务器逻辑，尚未在游戏客户端中运行完整挑战。

**尚未达到完整可玩状态**：还需接入
队友邀请、加成点名、实时客户端通知和结算奖励。
7.1 中相关 `GadgetPlay*` / `MpPlay*` 消息的包号已存在，但本项目缺少对应的已核实消息结构，
需要进一步协议资料或客户端抓包。页面标记为「还原中」，后续活动标记为「待还原」。
已有 `ExecuteGadgetLuaReq/Rsp` 的包号也尚未核实，仍保留负数占位；内部 Lua 调用已接入。

当前使用用户确认已更新的 7.1.0 客户端进行分析。辅助 JSON 与 `config.ini` 可能残留旧值，
不用于否定用户确认的版本。客户端文件仅保留在本地，分析结果写入被忽略的 `local/activity-research/`。
已用固定提交的
[gi-stringliteral](https://github.com/kuma-dayo/gi-stringliteral/tree/1008fd7db28dcbc55729d5bb6b3a585dab86cf8b)
从本次客户端恢复 86,108 条加密字符串，包含烘炉界面和 MP play 资源名称。
字符串恢复尚未得到上述缺失消息的字段定义；序列化代码和协议字段仍需继续核实。
随后根据本次可执行文件的类型名称读取函数，恢复了 88,904 个类型的名称和命名空间，
包括 `MonoActivityCrucible` 与 `MonoCrucibleEndPage`。协议类名仍被混淆，
继续按可执行文件的字段和方法读取函数，恢复了 440,172 个字段名称及类型引用，
以及 733,442 个方法名称、原生地址和参数数量。字段和方法归属范围连续且无重叠，
名称均通过 UTF-8 与控制字符检查；非零方法地址位于 PE 文件支持的区间。
字段类型输出包括基础类型、属性及类/值类型的定义索引；泛型和数组的负载保留原始索引，
尚未恢复完整的泛型参数或方法签名。
部分混淆类的解析代码已观察到 protobuf 标签读取分支；
字段编号与具体消息、包号的对应关系仍待核实，提取结果不能直接当作可用的 `.proto`。

```powershell
python tools/extract_client_type_names.py 'C:\Users\InvokerBot\AppData\Local\hoyo\hk4e\versions\current'

# 同时提取字段与方法，默认输出 client-type-metadata.json
python tools/extract_client_type_names.py 'C:\Users\InvokerBot\AppData\Local\hoyo\hk4e\versions\current' --include-fields --include-methods
```

该工具只读取客户端；内置偏移与解码常量严格绑定本次 exe 和元数据的 SHA-256，
其他构建会被拒绝，避免更新后套用旧偏移。输出默认保存在被忽略的
`local/activity-research/client-type-names.json`；带字段或方法选项时默认保存为同目录的
`client-type-metadata.json`。两个选项也可单独使用；`--output` 可指定分析结果路径，
客户端目录内的输出会被拒绝。客户端文件和提取出的元数据不随仓库或镜像发布。

## 数据来源和更新

历史名称、日期、版本号和来源链接取自
[原神 BWIKI 活动一览](https://wiki.biligame.com/ys/活动一览) 的语义查询。
导入脚本不复制活动攻略或文章正文；玩法使用
[LunaGC-Resources](https://github.com/invoker-bot/LunaGC-Resources) 中的客户端表和 Lua。
早期活动类型编号参考
[NewActivityType.cs](https://github.com/Genshin-PS-Archive/WeedwackerPS/blob/main/src/GameServer/Data/Enums/NewActivityType.cs)
及项目已有的 `ActivityType`。

```powershell
python tools/import_activity_history.py --resources resources
```

输出 `data/ActivityHistory.json` 和 jar 默认目录中的同名文件；无法对应的记录写入
被 Git 忽略的 `local/activity-history-import-report.json`，供逐项核对。
每条历史记录有独立来源链接。修改共享名称的对应关系应先核对具体活动 ID 与原始开放日期。

## HTTP 接口

接口沿用 GM 令牌和地址访问设置：

- `GET /gm/api/activities`：按时间排序的目录、资源可用性、当前排期及还原进度。
- `POST /gm/api/activities`：`key`、`action`（`enable` / `disable` / `rerun`）、
  可选 `durationDays`（0 为原时长，1–365 为指定天数）。

服务端先验证并构建全部活动处理器，再原子保存配置并热重载。
类型编号未核实、资源缺失或无效排期会被拒绝，当前配置保留。
