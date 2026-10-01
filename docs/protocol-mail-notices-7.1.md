# 7.1 公告与邮件适配

## 客户端来源

只读取用户提供的客户端；未修改二进制文件。对应 `tools/extract_client_type_names.py` 的固定哈希：

- `YuanShen.exe` SHA-256：`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`
- `global-metadata.dat` SHA-256：`469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161`

名称为服务端语义命名。CmdId 来自客户端 `AEGNNPENLNM` 的字面量返回值，字段标签来自 protobuf 读写器。
请求的语义还通过客户端邮件模块 `GCBCFBCMJOL` 的创建、发送与本地状态更新调用核对。

## 邮件包号

| 消息 | 客户端类型索引 / 名称 | CmdId | 关键字段标签 |
| --- | --- | --- | --- |
| GetAllMailReq | 81376 / EPAGNKFMKHD | 5495 | is_collected=2 |
| GetAllMailNotify | 29065 / MJBNIIKOOPO | 28960 | is_collected=2 |
| GetAllMailRsp | 82125 / OEJGFDGDICJ | 29682 | retcode=1, is_truncated=7, is_collected=14, mail_list=15 |
| GetAllMailResultNotify | 78118 / LBNLNMPDFHE | 8750 | retcode=2, is_collected=3, page_index=5, transaction=9, mail_list=10, total_page_count=12 |
| GetMailItemReq | 37902 / DHOHHMLDLGF | 3404 | mail_id_list=15 |
| GetMailItemRsp | 15565 / NHNKPLAILJG | 22901 | item_list=3, beyond_item_hint=5, mail_id_list=6, retcode=15 |
| ReadMailNotify | 58440 / JJKFGDAOLGK | 8592 | mail_id_list=14 |
| ChangeMailStarNotify | 34823 / PMKOOBIFNNP | 8766 | is_star=4, mail_id_list=10 |
| DelMailReq | 47862 / KOGGBJFCLFL | 3042 | mail_id_list=12 |
| DelMailRsp | 45312 / MKDGJGGNKAI | 28591 | retcode=9, mail_id_list=14 |
| MailChangeNotify | 55744 / BLKNLDKMHJK | 23925 | del_mail_id_list=4, change_mail_list=5, mail_list=6 |

关键定位：

- GetAllMailRsp 读写器：`0x14d14b440`。
- GetAllMailResultNotify 读写器：`0x149c33360`。邮件模块接收函数 `0x14bdd1330` 使用 offset 0x28 的
  一起始页号减一索引，offset 0x30 的总页数分配并等待完整结果。服务端当前发送一页，二者均为 1。
- 邮件模块创建请求：`0x14bdce730`（5495）、`0x14bdd1e50`（28960）、`0x14bdd0290`（3404）、
  `0x14bdd0f80`（3042）、`0x14bdd2c20`（8592）。8592 发送前更新本地邮件状态，3042 不执行该更新。
- MailData=58204、MailTextContent=35929、MailItem=53687、EquipParam=23908。
  MailData 的既有标签与本客户端一致。新邮件放入 `mail_list`，已读、星标与领取更新放入 `change_mail_list`。

旧代码的部分请求包号为负数，处理器因此没有注册；GetAllMailRsp 使用旧描述符标签，
GetAllMailResultNotify 的分页字段是 50000/50001 占位标签。这些均已替换。

## 公告链路

旧实现只有海外服接口且缺少公告网页。客户端补丁将官方公告地址改写到本地同一路径，导致页面 404。
SDK 配置现在返回本地公告 URL；自包含网页随服务端 JAR 打包，不再读取缺失的网页资源目录。
`hk4e_cn` 与 `hk4e_global` 的 getAnnList、getAnnContent、getAlertAnn、getAlertPic 共享持久化目录。
列表与正文使用同一生效时间和公告编号，无空占位公告、外部图片或脚本。页面有 8 秒请求超时和重试按钮。

`PlayerSetPauseReq` CmdId=5963、is_paused=11 也与客户端写入器一致；尚未找到可信的
`PlayerSetPauseRsp` CmdId，本次没有猜测该响应的包号。公告网页和邮件链路的服务端回归、HTTP 和浏览器检查
不能替代游戏内 WebView 的实机检查；最终仍需在游戏中打开、关闭公告并领取邮件附件确认。

## 保存与失败处理

公告先写临时文件再替换，失败不会改变已发布目录。邮件写入使用确认的 MongoDB 保存，
每位收件人拥有独立的 Mail 文档；`deliveryKey` 的唯一稀疏索引防止同一发送批次重复投递。
旧邮件只原子写入 mailId，不覆盖正文或领取状态；全局编号序列在重启时从已用最大值继续。
在玩家登录与离线投递重叠时，取邮箱会补入数据库中的新邮件。

领取附件会检查有效期、物品、数量、等级、背包容量和堆叠限制。保存预约失败不会发物品；
发放中断会保留 claimInProgress，防止再次发放。保留预约不等于完成领取，GM 会标出异常供核对，
单机 MongoDB 没有跨 Mail、Player 与背包文档的事务保证。正常路径在附件及玩家保存完成后标为已领取。
