# MiaoWang-Vip 会员模块

宠物的会员（VIP）体系：**权益判定 + 每日额度计数 + 3D 模型定制**。

## 一、职责边界（重要）

VIP 模块只做三件事：

1. **权益判定**：某个用户现在是什么档位、宠物能养几只、每天能用几次 AI 能力
2. **额度计数**：RAG 问答 / 宠物聊天的每日次数用 Redis 计数、扣减
3. **3D 定制**：定制订单流程与模型归属校验

它**不**做的事情（在各自模块里实现，通过接口调用本模块）：

| 业务动作 | 实现方 | 怎么用 VIP |
|---|---|---|
| 新增宠物 / 校验数量上限 | MiaoWang-pet | 调 `/internal/benefit/{uid}` 拿 `petLimitPerType`，自己数当前数量 |
| RAG 问答 | MiaoWang-ai-knowledge | 执行前调 `/internal/quota/consume`（biz=rag），`allowed=false` 就不调模型 |
| 与宠物聊天 | MiaoWang-ai-pet | 同上（biz=chat） |
| 支付 | 后续接入微信/支付宝 | 当前是模拟支付：下单即生效，见 `UserVipServiceImpl#subscribe` 的 TODO |

这样切分的原因：会员模块是「规则中心」，业务模块是「执行者」。以后调价、加档位只改 VIP 的数据，业务模块不用动。

## 二、权益矩阵

价格与次数**全部存在 `miaowang_vip_plan` 表里**（改权益 = 改数据，不改代码）。`-1` 表示不限次数。

| 档位编码 | 名称 | 月费 | 猫/狗各自上限 | RAG 每日 | 聊天每日 |
|---|---|---|---|---|---|
| `FREE` | 免费用户 | 0 | 1 | 3 | 5 |
| `PET_49` | 宠物位扩容月卡 | 4.9 | 5 | 3 | 5 |
| `AI_149` | AI 基础月卡 | 14.9 | 5 | 10 | 20 |
| `AI_249` | AI 进阶月卡 | 24.9 | 5 | 50 | 50 |
| `AI_349` | AI 畅享月卡 | 34.9 | 5 | -1（不限） | 100 |
| `ALL_400` | 全能月卡 | 40 | 5 | -1（不限） | -1（不限） |

口径说明：

- **高档位包含低档位权益**：14.9 及以上同样享受猫狗各 5 只（已按此口径写入种子数据）
- **一个用户可以同时持有多个档位**，权益按维度取最大值合并；
  同一档位续费不会产生新行，只延长到期时间
- 免费用户也能用 RAG（每天 3 次），符合「先试用再付费」的转化思路

## 三、数据表（`sql/vip-ddl.sql`）

| 表名 | 说明 |
|---|---|
| `miaowang_vip_plan` | 档位配置：价格、有效天数、三项权益上限 |
| `miaowang_user_vip` | 用户会员：`uk_uid_plan` 保证同档位一行，续费延长 `expire_time` |
| `miaowang_vip_order` | 订阅订单：金额/档位名做快照，历史订单不受改价影响 |
| `miaowang_vip_3d_order` | 3D 定制单：状态 0待处理 → 1制作中 → 2已完成 / 3已取消 |

额度计数**不落库**：Redis key 为 `MiaoWang:vip:quota:{biz}:{uid}:{yyyyMMdd}`，
当天第一次使用时设置过期时间（到次日零点 + 5 分钟缓冲），不做定时任务清理。

## 四、对外接口

统一前缀 `/mapleleaf/miaowang/v1/vip`，返回体为 `Result<T>`。

### 4.1 档位与我的会员

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/plan` | 可购买档位列表（免费档不返回） |
| GET | `/benefit` | 我的权益总览：档位名、到期时间、宠物上限、今日剩余次数 |
| POST | `/subscribe` | 开通/续费，body `{"planCode":"AI_249"}`（模拟支付，下单即生效） |
| GET | `/order` | 我的订阅订单列表 |

`/benefit` 对非会员也正常返回（`vip=false` + 免费档权益），前端不用写两套渲染逻辑：

```json
{
  "code": "0",
  "data": {
    "vip": true,
    "planNames": ["AI 进阶月卡"],
    "expireTime": "2026-10-11 20:30:00",
    "petLimitPerType": 5,
    "ragDailyLimit": 50,
    "chatDailyLimit": 50,
    "ragUsedToday": 3,
    "ragRemainingToday": 47,
    "chatUsedToday": 0,
    "chatRemainingToday": 50
  }
}
```

### 4.2 每日额度

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/quota/query?biz=rag` | 查今日次数（biz：`rag` / `chat`） |
| POST | `/quota/consume` | 扣一次，body `{"biz":"rag"}` |

扣减用 **Redis INCR 先自增、超限再回滚**：一次网络往返、自增天然原子，并发下计数不会被顶超。
不限次数的档位直接跳过计数，不做无意义的写。

**次数用完不是系统错误**：接口照常返回 `code=0`，`data.allowed=false`，前端据此弹开通引导。
这样避免把业务状态混进异常通道。

### 4.3 3D 模型定制

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/3d/order` | 提交申请，body `{pid, petName, requirement, referenceImages}` |
| GET | `/3d/order` | 我的定制单列表 |
| GET | `/3d/order/{orderNo}` | 定制单详情 |
| GET | `/3d/order/{orderNo}/model` | 取模型地址（首页展示用） |
| POST | `/3d/order/{orderNo}/cancel` | 取消（仅待处理/制作中） |

**模型归属**：查询一律带上 `uid` 条件，查不到就按「订单不存在」处理，
不区分「不存在」和「不是你的」，避免通过报错探测他人订单。
所以「只有这个用户可以使用」是在接口层强制的，不依赖前端。

### 4.4 服务间接口（`/internal`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/benefit/{uid}` | 查权益 |
| POST | `/internal/quota/consume` | body `{uid, biz}`，执行前扣额度 |
| POST | `/internal/3d/complete` | 制作方回填 `{orderNo, modelUrl, adminRemark}` |

⚠️ 这些接口**没有登录态**，上线前必须在网关限制为内网/服务间调用。

## 五、启动前要做的三件事

1. **建表**：在 `MiaoWang` 库执行 `sql/vip-ddl.sql`
   （注意：脚本会 DROP 重建这 4 张表，属初始化脚本，之后请改用增量 SQL）
2. **Nacos 加配置**：新建 `MiaoWang-service-vip-dev.yaml`（DEFAULT_GROUP）

   ```yaml
   server:
     port: 8052            # 选一个没被占用的端口
   spring:
     application:
       name: miaowang-vip
   ```

   本模块 `application.yml` 已导入 `MiaoWang-all-service.yaml`（数据源）与本文件，
   Nacos 账号密码也已配好。
3. **网关加路由与白名单**：放行 `GET /MiaoWang-vip/mapleleaf/miaowang/v1/vip/plan`（档位列表），
   其余 `/vip/**` 需登录；`/vip/internal/**` 禁止外网访问

## 六、待办与已知边界

- **宠物归属校验缺失**：`/3d/order` 的 `pid` 目前不校验是否属于当前用户
  （宠物表在 pet 模块，跨服务校验需要 Feign 调用 + 用户头透传，后续补）
- **猫/狗分开计数无法落地**：宠物表 `miaowang_pet` 现在没有物种字段，
  「猫狗各 5 只」暂时只能整体按上限校验，需要在 pet 模块补 `species` 字段
- **支付是模拟的**：真实支付接入时改为「先落待支付单 → 回调置已支付 → 开通会员」，
  并补重复下单/重复回调的幂等（分布式锁或唯一索引 + 状态机）
- **额度扣减可再收紧**：当前 INCR + 回滚在极端并发下可能多扣 1 次（已回滚，不影响上限），
  若要绝对精确可换 Lua 脚本一次完成「判断 + 扣减」
- **`/internal/**` 无鉴权**，仅靠网关兜底
- **管理端未做**：`/internal/3d/complete` 目前靠手工调用，后续挂到后台管理