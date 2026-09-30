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

**尚未达到完整可玩状态**：当前资源缺少它引用的主控组 `305001001`；
还需恢复阶段、怪物波次、限时挑战、队友邀请、加成点名和结算奖励。
7.1 中相关 `GadgetPlay*` / `MpPlay*` 消息的包号已存在，但本项目缺少对应的已核实消息结构，
需要进一步协议资料或客户端抓包。页面标记为「还原中」，后续活动标记为「待还原」。

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
