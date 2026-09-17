# MiaoWang-Vip 会员模块

宠物的会员（VIP）体系：**权益判定 + 额度计数 + 3D 模型定制**。

## 一、职责边界（重要）

VIP 模块只做三件事：

1. **权益判定**：某个用户现在是什么档位、能养几只宠物、能用多少 AI 能力、健康档案有什么权限
2. **额度计数**：RAG 问答 / 宠物聊天的额度用 Redis 计数、扣减
3. **3D 定制**：定制订单流程与模型归属校验

它**不**做的事情（在各自模块里实现，通过接口调用本模块）：

| 业务动作 | 实现方 | 怎么用 VIP |
|---|---|---|
| 新增宠物 / 校验数量上限 | MiaoWang-pet | 调 `/internal/benefit/{uid}` 拿 `petLimitPerType`，按**宠物总数**校验（不区分物种） |
| RAG 问答 | MiaoWang-ai-knowledge | 执行前调 `/internal/quota/consume`（biz=rag），`allowed=false` 就不调模型 |
| 与宠物聊天 | MiaoWang-ai-pet | 同上（biz=chat） |
| 健康档案（疫苗/驱虫/就诊…） | **MiaoWang-healthy（已实现）** | 通过 common 的 `VipServiceFeign` 取 `healthRecordLimit`/`healthExport`/`healthAiAnalysis` |
| 提醒与打卡 | MiaoWang-reminder（待建） | 提醒条数看 `reminderLimit` |
| 支付 | 后续接微信/支付宝 | 渠道已抽象为 `PayChannel`，当前实现 `MockPayChannel`（走真实回调链路）；接真渠道只需新增实现类 |

**为什么这么切**：VIP 被摆在每个请求的关键路径上（宠物数量、扣次数都要问它），必须保持轻量、高可用；
把高频写的业务数据塞进来会放大它的故障半径。所以 VIP 只当「规则中心」，业务数据留在业务模块。

## 二、权益矩阵

价格与所有权益**全部存在 `miaowang_vip_plan` 表里**（改权益 = 改数据，不改代码）。`-1` 表示不限。

| 档位编码 | 名称 | 月费 | 猫/狗各自 | RAG | 聊天 | 每类健康记录 | 报告导出 | AI 症状解读 | 提醒条数 |
|---|---|---|---|---|---|---|---|---|---|
| `FREE` | 免费用户 | 0 | 1 | **5 次/月** | 5 轮/天 | 20 | ✗ | ✗ | 3 |
| `PET_49` | 宠物位扩容月卡 | 4.9 | 5 | 5 次/月 | 5 轮/天 | 20 |  | ✗ | 3 |
| `AI_149` | AI 基础月卡 | 14.9 | 5 | 10 次/天 | 20 轮/天 | 100 | ✗ | ✗ | 不限 |
| `AI_249` | AI 进阶月卡 | 24.9 | 5 | 50 次/天 | 50 轮/天 | 不限 | ✓ |  | 不限 |
| `AI_349` | AI 畅享月卡 | 34.9 | 5 | 不限 | 100 轮/天 | 不限 | ✓ | ✓ | 不限 |
| `ALL_400` | 全能月卡 | 40 | 5 | 不限 | 不限 | 不限 | ✓ | ✓ | 不限 |

口径说明（改权益前先读这几条）：

1. **高档位叠加包含低档位**，各维度随价格**单调不降**（14.9 及以上同样享受猫狗各 5 只）
2. **免费额度按月给试用，付费额度按天给**：免费 RAG 只有 5 次/月（防止白嫖、制造升级动机），
   付费档按天给（体验爽）
3. **聊天按天、RAG 免费按月**：聊天是留存钩子（便宜），RAG 要走向量检索 + 大模型（贵）
4. **健康档案能建几只宠物 = 宠物数量上限**，不另设概念：能养就能记
5. **健康档案的付费点**是「每类记录条数 / 导出报告 / AI 解读」，而不是「能不能建档」
6. **一个用户可以同时持有多个档位**，权益按维度取最大值合并；同档位续费只延长到期时间
7. **免费档是兜底**：无会员/全部过期 → 回落 FREE 档权益（读库，代码只做缺配置兜底）

## 三、额度模型：上限 + 周期

额度不再是「每日次数」一个数，而是 **(上限, 周期)** 组合，周期支持：

| 周期 | Redis key 归档 | 过期时间 |
|---|---|---|
| `DAY` | `...:{biz}:DAY:{uid}:{yyyyMMdd}` | 次日零点 + 5 分钟 |
| `MONTH` | `...:{biz}:MONTH:{uid}:{yyyyMM}` | 次月一号零点 + 5 分钟 |

完整 key 前缀：`MiaoWang:vip:quota:`。key 自带归档刻度，**永远不需要定时任务清理**。

**多档位合并规则**：折合「月总量」比大小（DAY 的月总量 = 上限 × 30，MONTH 的月总量 = 上限），
谁更宽松用谁，`-1`（不限）最大，持平优先 `DAY`（重置更快）。
例：5 次/月 与 10 次/天 并存 → 取 10 次/天（月总量 300 > 5）。

**扣减方式**：Redis `INCR` 先自增、超限再回滚。一次网络往返，自增天然原子，并发下计数不会被顶超。
不限次数的档位直接跳过计数，不做无意义写入。

**次数用完不是系统错误**：接口照常返回 `code=0`，`data.allowed=false`，前端据此弹开通引导。

## 四、错误码段位

全项目约定（勿重复占用）：

| 段位 | 归属 |
|---|---|
| `B0000xx` / `B0001xx` | common 基础错误 |
| `B0002xx` | user |
| `B0003xx` | community |
| `B0004xx` | healthy |
| **`B0005xx`**（`B000500`~`B000532`） | **vip（本模块）** |
| `B0006xx` | common 文件/对象存储 |

> 曾误用 `B000300~B000308`（与 community 撞车），已改为 `B000500~B000509`。

## 五、数据表（`vip-ddl.sql` = v2 全量；`vip-plan-v2.sql` = 权益增量；`vip-pay-v3.sql` = 支付增量；`vip-promo-v4.sql` = 促销增量；`vip-admin-v5.sql` = 后台增量）

| 表名 | 说明 |
|---|---|
| `miaowang_vip_plan` | 档位配置：价格、有效天数、9 项权益（含额度周期） |
| `miaowang_user_vip` | 用户会员：`uk_uid_plan` 保证同档位一行，续费延长 `expire_time` |
| `miaowang_vip_order` | 订阅订单：金额/档位名/时长做快照；含支付状态机、幂等键、渠道交易号、退款与关单字段（v3 见 `sql/vip-pay-v3.sql`） |
| `miaowang_vip_3d_order` | 3D 定制单：状态 0待处理 → 1制作中 → 2已完成 / 3已取消 |
| `miaowang_vip_promo_code` | 促销码：一张表覆盖「送会员时长」与「下单立减」两种玩法（见 §6.7） |
| `miaowang_vip_promo_redeem` | 核销记录：唯一索引 `(promo_code_id, uid)` 保证每人每码限用一次 |
| `miaowang_vip_admin` | 后台管理员：独立于 `miaowang_user`，密码存 BCrypt 哈希（见 §6.8） |
| `miaowang_vip_admin_op_log` | 后台操作日志：谁、什么时候、对哪一单做了什么 |

## 六、对外接口

统一前缀 `/mapleleaf/miaowang/v1/vip`（**全小写**），返回体为 `Result<T>`。

### 6.1 档位与我的会员

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/plan` | 可购买档位列表（含各档位九项权益，前端直接渲染对比表） |
| GET | `/benefit` | 我的权益总览：档位名、到期时间、各项权益、本周期剩余次数 |
| POST | `/subscribe` | 一键开通/续费，body `{"planCode":"AI_249"}`（内部 = 下单 + 模拟支付，联调/演示用） |
| GET | `/order` | 我的订阅订单列表（订单写操作见 §6.5） |

`/benefit` 对非会员也正常返回（`vip=false` + 免费档权益），前端不用写两套渲染逻辑：

```json
{
  "code": "0",
  "data": {
    "vip": true,
    "planNames": ["AI 进阶月卡"],
    "expireTime": "2026-10-16 20:30:00",
    "petLimitPerType": 5,
    "ragLimit": 50, "ragPeriod": "DAY", "ragPeriodDesc": "每日",
    "ragUsed": 3, "ragRemaining": 47,
    "chatLimit": 50, "chatPeriod": "DAY", "chatPeriodDesc": "每日",
    "chatUsed": 0, "chatRemaining": 50,
    "healthRecordLimit": -1, "healthExport": 1, "healthAiAnalysis": 0,
    "reminderLimit": -1
  }
}
```

免费用户看到的会是 `ragPeriod: "MONTH"`、`ragPeriodDesc: "每月"`、`ragLimit: 5`。

### 6.2 额度

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/quota/query?biz=rag` | 查本周期余量（biz：`rag` / `chat`） |
| POST | `/quota/consume` | 扣一次，body `{"biz":"rag"}` |

### 6.3 3D 模型定制

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/3d/order` | 提交申请，body `{pid, petName, requirement, referenceImages}` |
| GET | `/3d/order` | 我的定制单列表 |
| GET | `/3d/order/{orderNo}` | 定制单详情 |
| GET | `/3d/order/{orderNo}/model` | 取模型地址（首页展示用） |
| POST | `/3d/order/{orderNo}/cancel` | 取消（仅待处理/制作中） |

**模型归属**：查询一律带上 `uid` 条件，查不到就按「订单不存在」处理，不区分「不存在」和「不是你的」，
避免通过报错探测他人订单。所以「只有这个用户可以使用」是在接口层强制的，不依赖前端。

### 6.4 服务间接口（`/internal`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/benefit/{uid}` | 查权益（**已被 healthy 的 `VipServiceFeign` 消费**） |
| POST | `/internal/quota/consume` | body `{uid, biz}`，执行前扣额度 |
| POST | `/internal/quota/refund` | body `{uid, biz}`，业务失败时回退一次（生成失败不扣用户次数） |
| POST | `/internal/3d/complete` | 制作方回填 `{orderNo, modelUrl, adminRemark}` |

### 6.5 订单与支付（v3：标准支付状态机）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/order` | 下单，body `{planCode, payType?, requestId?}`：只落**待支付**订单、不发会员；返回 `payParams` |
| GET | `/order` | 我的订单列表（顺手关掉超时未支付的单） |
| POST | `/order/{orderNo}/pay` | 模拟支付（联调）：造一条带签名的回调，走真实回调链路 |
| POST | `/order/{orderNo}/refund` | 退款，body `{reason?}`：仅本人 / 已支付 / 7 天内；成功后回收会员时长 |
| POST | `/pay/notify/{channel}` | **渠道回调，免登录**（网关白名单）；只接受允许公网回调的渠道 |

**订单状态机**：`0 待支付` ──支付回调──▶ `1 已支付` ──退款──▶ `3 已退款`；
`0 待支付` ─超时 30 分钟──▶ `2 已关闭`（不可逆）。只有 `0` 能被回调改成 `1`，只有 `1` 能退款。

**三层幂等（本模块最值得看的部分）**

| 场景 | 手段 |
|---|---|
| 重复下单（连点 / 弱网重试） | ① `requestId` + 唯一索引 `uk_vip_order_uid_request`；② 同档位已有未超时的待支付单则直接复用 |
| 重复回调（渠道会一直重试） | 状态已是「已支付」→ 直接应答成功，**这一步刻意放在 nonce 校验之前**（否则渠道的正常重试会被判成重放、无限重试下去）；并发时靠 `update ... where pay_status=0` 的 **CAS**，只有一条能改成功，也就只有一次发会员 |
| 重复退款 | 同样 CAS（`where pay_status=1`），不会重复扣减会员时长 |

**回调校验顺序**（顺序错了会出事）：验签 → 时间戳新鲜度（±5 分钟）→ 找订单 → 已支付则幂等应答 →
金额与订单比对 → nonce 一次性（Redis SETNX）→ CAS 流转 → **同一事务内发会员**。

**为什么不用分布式锁**：一条带条件的 `UPDATE` 天然跨实例互斥，比锁更轻，也不会出现锁超时/误删的问题。

**渠道抽象**：`PayChannel`（下单 / 验签解析 / 退款 / 应答）+ `PayChannelRegistry`。
接微信或支付宝只需新增一个实现类并在 Nacos 配密钥，状态机与幂等逻辑一行都不用改 ——
所以 `MockPayChannel` 不是空实现：它会造带签名的回调走完整链路，**联调测过的路径就是真渠道要走的路径**。

### 6.6 支付安全约定

- 回调免登录，安全性完全靠 **HMAC-SHA256 验签 + 时间戳 + nonce**；签名比较用常量时间比较（防时序攻击）
- **mock 渠道不允许公网回调**（`VipPayChannelEnum.externalNotify=false`）：否则知道 mock 密钥的人
  就能伪造回调给自己发会员。联调走已登录的 `/order/{orderNo}/pay`（会校验订单归属）
- 渠道解析失败**不降级到 mock**：宁可下单失败，也不能不花钱拿到会员
- 回调金额必须与订单金额一致，否则拒绝（防"改小金额骗会员"）
- 真实渠道密钥放 Nacos；mock 密钥写在配置默认值里是安全的（它不面向公网）

### 6.7 促销：兑换码 / 优惠码

**一张表两种玩法**（`promo_type`），因为校验逻辑（有效期、名额、每人限一次、状态）完全一样，
拆两张表只会把同样的校验写两遍：

| 类型 | 入口 | 什么时候占名额 |
|---|---|---|
| `PLAN` 送会员时长 | `POST /promo/redeem` body `{code}` | 核销即占（内部生成 0 元「已支付」促销单来发会员） |
| `DISCOUNT` 下单立减 | `POST /order` body 里带 `promoCode` | **支付成功才占**（用码下单却不付款不浪费名额） |

**几个刻意的口径**：

- 立减**不会把订单减到 0**：渠道不支持 0 元支付，最低保留 0.01 元（超出的部分自动截断并记日志）
- **先发权益、后占名额**：名额是概率性约束（并发 CAS 可能没抢到），"已核销"是既成事实；
  顺序反了会出现"名额扣了、会员没发"，用户无法自证。所以 CAS 没抢到只记 error 日志（可对账追责）
- 每人每码限一次由**唯一索引** `uk_promo_uid` 保证，业务层"查一下"只是友好提示
- `0` 元促销单禁止退款（没有钱可退，允许退款只会把刚发的会员收回去）
- **不做"券包/领取"模型**：本期的码就是券（下单时输入码抵扣）。若要做"领券后下单选用"，
  需要再加 `user_coupon` 表与领券接口，属于另一套玩法
- 退款**不退码**（码已经核销，属口径取舍，待产品确认）

### 6.8 后台管理（`/vip/admin/**`）

**身份体系与 C 端完全分开**：管理员独立一张表、独立 token，不复用 `miaowang_user` + role 字段。
理由是后台账号与 C 端账号的生命周期、权限、审计要求都不同，混在一起等于"用户表的任何漏洞都可能变成后台入口"。

| 分组 | 接口 | 权限 |
|---|---|---|
| 登录 | `POST /login`、`POST /logout`、`GET /me`、`PUT /password` | 登录即可 |
| 3D 定制 | `GET /3d/order`、`POST /3d/order/status`、`POST /3d/order/complete` | 运营 |
| 会员订单 | `GET /order`、`POST /order/refund`（代退，不受 7 天窗口限制） | 运营 |
| 促销码 | `GET /promo`、`POST /promo`、`POST /promo/{id}/status` | 运营 |
| 管理员 | `GET /admin`、`POST /admin`、`POST /admin/{id}/status` | **超管** |
| 审计 | `GET /op-log` | **超管** |

**几个刻意的设计**：

- **请求头是 `X-Admin-Token`，不是 `Authorization`**：两套身份物理隔开，杜绝"普通用户 token 被当管理员用"
- **网关放行、服务内鉴权**：`/vip/admin/**` 在网关白名单里（C 端登录态对它没意义），
  `VipAdminAuthInterceptor` 负责校验（除 `/login` 外全拦）
- **token 放 Redis 而不是签 JWT**：后台要的是"可即时吊销"（禁用/改密码/踢下线立即生效），不是无状态
- **每请求查一次库**：后台 QPS 极低，换来"禁用管理员秒级生效"
- **ThreadLocal 用完即清**：线程池复用线程，不清理会让下一个请求读到上一个管理员的身份
- **改密码强制重新登录**（旧 token 立即失效）；**不能禁用自己的账号**（防呆，避免没人能进后台）
- **所有写操作落审计日志**：`3D_COMPLETE` / `ORDER_REFUND` / `PROMO_SAVE` / `ADMIN_CREATE` …
  写日志失败只告警不回滚业务（但会打 error）
- 密码存 BCrypt 哈希（C 端目前是明文，属已知技术债）；**登录失败次数锁定**未做（挂账）
- 初始超管 `admin / admin123456`（`sql/vip-admin-v5.sql`），**这是公开的种子密码，登录后必须改**

⚠️ 这些接口**没有登录态**，上线前必须在网关限制为内网/服务间调用。

## 七、与 MiaoWang-healthy 的契约（已实现，改动需双方同步）

健康档案模块（`MiaoWang-healthy`）已经建成，六类记录 + 时间线 + 体重曲线 + 概览 + 导出 + 症状选项
都已实现；它对 VIP 的依赖只有一条链路：

```
healthy 业务代码
  → HealthBenefitResolver（common 的 VipServiceFeign）
  → GET /mapleleaf/miaowang/v1/vip/internal/benefit/{uid}
  → 反序列化进 common 的 VipBenefitFeignRespDTO
```

**契约锁（改 VIP 返回体前必读）**：`VipBenefitFeignRespDTO` 按**字段名**反序列化，
下面这些名字一旦改动，healthy 侧会**静默拿到 null**（不报错、直接按「不限/无权限」判错）：

| 字段 | healthy 侧用途 |
|---|---|
| `vip` | 是否会员（概览展示） |
| `petLimitPerType` | 概览带额度语义（宠物数量由 pet 模块把关） |
| `healthRecordLimit` | 写入前按「宠物 + 记录类型」计数，达上限报 `B000403` |
| `healthExport` | `0` 时导出接口报 `B000404` |
| `healthAiAnalysis` | AI 症状解读的闸门（healthy 的 `POST /health/{pid}/ai-analysis`）：`0` 时返回 `B000411` |
| `reminderLimit` | 提醒条数（reminder 模块待建） |

要改这些字段名，必须同时改 **common 的 `VipBenefitFeignRespDTO`**（该文件不在本模块内）。

healthy 侧的其他相关约定：

- **宠物归属**：它复用 pet 的「查我的宠物」接口 + `FeignHeaderPropagationConfig` 透传登录头，
  不是新增内部接口；归属校验失败时**报错不放行**
- **权益服务不可用时也报错不放行**（后续加缓存 + 降级）
- **打卡历史归 reminder 模块**，healthy 只提供 `next_due_at` 供提醒生成，不重复存

**pet 模块（已接入）**：`POST /pet` 建宠物前消费 `petLimitPerType` —— 统计该用户**宠物总数**
（`del_flag=0`，**不区分物种**），到达上限返回 pet 侧的 `B000800`；权益服务不可用则 `B000801` 且不放行。
也就是说「免费 2 只 / 会员 5 只」这类权益真正生效的位置在 pet 模块，VIP 只负责给数字。
✅ 口径已定案：**总数口径、与物种无关**（免费 2 只、会员 5 只）。早期"每类各 1 只"的实现已修正
（档位值 1→2 见 `sql/vip-pet-limit-v6.sql`）。字段名 `petLimitPerType` 是历史遗留命名，语义已是总数。

## 八、启动前要做的三件事

1. **升级表结构**（表已按 v1 建过的话）：

   ```
   MiaoWang-Vip/sql/vip-plan-v2.sql      -- 可重复执行，不动会员/订单数据
   ```

   全新环境直接跑 v2 全量脚本 `sql/vip-ddl.sql`（注意它会 DROP 重建 VIP 四张表）。
2. **Nacos 加配置**：`MiaoWang-service-vip-dev.yaml`（DEFAULT_GROUP）

   ```yaml
   server:
     port: 8052            # 选一个没被占用的端口
   spring:
     application:
       name: miaowang-vip
   ```

   本模块 `application.yml` 已导入 `MiaoWang-all-service.yaml`（数据源）与本文件，Nacos 账号密码也已配好。
3. **网关加路由与白名单**：网关用 discovery locator 自动路由，URL 形状是

   ```
   http://<gateway>:7000/miaowang-vip/mapleleaf/miaowang/v1/vip/...
   ```

   放行 `GET /miaowang-vip/mapleleaf/miaowang/v1/vip/plan`（档位列表可公开），
   其余 `/vip/**` 需登录；`/vip/internal/**` 禁止外网访问。

## 九、待办与已知边界

- **3D 定制方向已定案：走人工定制订单流**（提交需求 → 制作中 → 回填模型地址），
  归属校验在接口层强制、只有本人能取模型。决策记录里「3D 定制先做参数化（下发 JSON）」
  那条**已过时**：本期不做参数化，服务端生成专属模型留给 AI 阶段。
  以后若要加参数化，再给 `miaowang_vip_3d_order` 加 `custom_params` JSON 列，不影响现有表结构。
- ✅ **3D 定制的 `pid` 归属已校验**：`/3d/order` 现在会调用 pet 的「查我的宠物」
  （`VipPetOwnershipChecker` + 登录头透传）确认 `pid` 属于当前用户，不属于则 `B000510`；
  pet 服务不可用则 `B000511` 且**不放行**。绑定了宠物时，宠物名以档案为准（覆盖前端传值，防伪造）。
- ✅ **支付状态机已落地**：下单只落「待支付」，回调 CAS 改成「已支付」并在**同一事务**里发会员；
  重复下单（`requestId` + 唯一索引）、重复回调（已支付幂等应答 + CAS）、重复退款都做了幂等；
  验签（HMAC + 时间戳 + nonce）、金额比对、超时关单都已完成。渠道为 mock，但走的是真实回调链路
- **支付还差的部分**：真实渠道 SDK、**对账/补单定时任务**（当前关单是懒触发：读列表或下单时顺手做）、
  渠道异步退款到账回调
- **退款只回收时长、不回收已用额度**：退款前已消耗的 RAG/聊天次数不回滚（口径取舍，待产品确认）
- **跨档位升级未建模**：现在是「一个档位一行、各自计时」，从 14.9 升 34.9 会并存两条会员记录
  （权益按「月等效取高」合并生效，但两条记录的到期时间各自算）
- **额度扣减可再收紧**：当前 INCR + 回滚在极端并发下可能多扣 1 次（已回滚，不影响上限），
  若要绝对精确可换 Lua 脚本一次完成「判断 + 扣减」
- ✅ **`/internal/**` 已由网关统一拒绝外部访问**（`gateway.auth.deny-list`：路径含 `internal` 直接 403，
  服务间 Feign 走 Nacos 直连、不受影响）；纵深防御（服务间共享密钥）仍未做
- **促销码管理只能靠 SQL**：`sql/vip-promo-v4.sql` 里有演示码（`WELCOME30` 送 30 天 / `SAVE5` 立减 5 元），
  但发码、改名额、上下架目前要手工改库 —— 管理端（见下一条）还没做
- ✅ **管理端已做（§6.8）**：管理员独立体系（BCrypt 密码 + Redis token + 两级角色 + 拦截器 + 审计日志）、
  3D 定制单管理（列表/改状态/回填模型）、会员订单查询与运营代退、促销码增改与上下架
- **后台仍缺**：登录失败锁定与图形验证码、管理员分页与搜索（现在最多返回 200 条）、
  促销码删除（只有上下架）、订单列表导出、后台前端页面（接口已就绪）
- **`/internal/3d/complete` 保留**但已被网关拦住外部访问：它属于服务间接口，
  运营侧请走 `POST /vip/admin/3d/order/complete`（有鉴权 + 有审计）
- **RAG 额度已闭环**：`MiaoWang-ai-knowledge` 的 `/ai/rag/ask` 与 `/ask/stream` 现在
  **要求登录**，并在调大模型**之前**先扣一次 RAG 额度；生成失败会自动回退（`/internal/quota/refund`）——
  **失败不扣次数**。`/ai/rag/index/build` 是运维动作，不扣额度。
- **聊天额度仍未闭环**：`MiaoWang-ai-pet` 目前是空骨架（没有聊天接口），无处可接；
  接法与 RAG 完全一样（`biz=chat`），照抄 `RagQuotaGuard` 即可
- **健康记录表尚未在库里创建**：`miaowang_pet_*_record` 六张表还没有，
  需要执行 `MiaoWang-healthy/sql/healthy-ddl.sql`（healthy 的 README 有完整说明）