# MiaoWang-healthy 宠物健康档案

宠物的健康记录中心：**疫苗本 / 接种、体内外驱虫、就诊病史、用药、过敏史、症状日记、体重曲线**。

## 一、模块职责

| 做什么 | 不做什么 |
|---|---|
| 六类健康记录的增删改查（全部按用户 + 宠物隔离） | 不管宠物本身（pet 模块负责） |
| 时间线聚合、体重曲线、档案概览、结构化导出 | 不做提醒推送（reminder 模块负责，本模块只提供 `next_due_at`） |
| 症状选项对接知识库「健康自查」条目 | 不判断会员状态（VIP 模块负责，本模块只消费权益） |
| 记录条数 / 导出 / AI 解读的权益闸门 | 不存打卡历史（归 reminder 模块） |

**为什么单独一个模块**：VIP 在每个请求的关键路径上（宠物数量、扣次数都要问它），不能再塞进
高频写的业务数据；而且健康数据是时间序列、隐私敏感、后期要做归档与冷热分离 —— 放在健康模块里
演进不会牵连会员体系。

## 二、数据模型（`sql/healthy-ddl.sql`）

| 表名 | 说明 |
|---|---|
| `miaowang_pet_preventive_record` | 疫苗 + 驱虫**合并表**，用 `type` 区分 VACCINE / DEWORM_IN / DEWORM_OUT |
| `miaowang_pet_medical_record` | 就诊记录 / 病史（诊断、处置、费用、化验单附件） |
| `miaowang_pet_medication_record` | 用药记录（可关联就诊单，含用药中/已结束/已停用状态） |
| `miaowang_pet_allergy_record` | 过敏史（过敏原 + 严重程度 + 反应表现） |
| `miaowang_pet_symptom_log` | 症状日记（关联知识库条目 + 名称快照 + 照片） |
| `miaowang_pet_weight_record` | 体重记录（曲线由接口按天聚合，不另存聚合表） |

**为什么疫苗和驱虫合并**：两者字段结构完全一致（项目名、时间、下次到期、医院、备注），
合并后 CRUD、到期提醒、时间线都不用写两套；差异只有 `type` 一个字段，查询时用索引
`idx_pid_type_time` 依然走得到。

**为什么每张表都冗余 `uid` + `username`**：`uid` 是所有查询的隔离条件（`pid` 单独不够，
否则拼一个 pid 就能读别人的病历），`username` 便于排查「这条数据是谁写的」。

**删除一律是逻辑删除**：`BaseDO` 的 `delFlag` 没有配 `@TableLogic`，所以每个实现类都显式
`set(delFlag, 1)`，查询也显式带 `del_flag = 0`。健康数据不接受物理删除（后续如需「彻底删除」
应作为单独的隐私合规动作，而不是普通删除）。

## 三、接口清单

统一前缀 `/mapleleaf/miaowang/v1/health`，返回 `Result<T>`；新增/列表带 `pid`，修改/删除带记录 `id`。

### 3.1 记录 CRUD（`HealthRecordController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/{pid}/preventive` | 新增疫苗/驱虫 |
| GET | `/{pid}/preventive?type=VACCINE` | 列表，`type` 可选 |
| PUT / DELETE | `/preventive/{id}` | 修改 / 删除 |
| POST | `/{pid}/medical` | 新增就诊记录 |
| GET | `/{pid}/medical` | 就诊/病史列表 |
| PUT / DELETE | `/medical/{id}` | 修改 / 删除 |
| POST | `/{pid}/medication` | 新增用药 |
| GET | `/{pid}/medication?status=0` | 列表，`status` 可选 |
| PUT / DELETE | `/medication/{id}` | 修改（结束用药也走这里）/ 删除 |

### 3.2 日常记录（`HealthDailyRecordController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST / GET | `/{pid}/allergy` | 新增 / 过敏史列表（重度在前） |
| PUT / DELETE | `/allergy/{id}` | 修改 / 删除 |
| POST / GET | `/{pid}/symptom` | 新增 / 症状日记列表 |
| PUT / DELETE | `/symptom/{id}` | 修改 / 删除 |
| POST / GET | `/{pid}/weight` | 新增 / 体重列表 |
| GET | `/{pid}/weight/curve?months=6` | 体重曲线（按天聚合，每天取最后一次） |
| PUT / DELETE | `/weight/{id}` | 修改 / 删除 |

### 3.3 聚合与选项（`HealthOverviewController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/{pid}/overview` | 首屏概览：各类条数、下次疫苗到期、体重变化、过敏、在用药物、权益状态 |
| GET | `/{pid}/timeline?types=preventive,symptom&limit=50` | 时间线（六类合并按时间倒序） |
| GET | `/symptom/options?species=1` | 症状选项（知识库健康自查条目） |
| GET | `/{pid}/export` | 导出完整档案（结构化 JSON，需档位支持） |

### 3.4 图片上传（`HealthFileController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/file` | 上传化验单 / 影像 / 症状照片，返回 `url` + `objectKey`（表单字段名 `file`，≤10MB） |

上传走 common 的 `FileStorageService`（MinIO），健康模块不关心存储实现；
返回的 `url` 存进就诊记录的 `attachments` 或症状日记的 `images` 字段（多个用逗号分隔）。

## 四、三个关键设计

### 4.1 宠物归属校验：复用 pet 的接口 + 透传登录头

所有表以 `pid` 为键，**必须**确认这只宠物属于当前用户，否则任何人都能读写别人的病历。
实现上没有让 pet 模块新增内部接口，而是复用它的「查我的宠物」接口：

```
浏览器请求(带 username/token)
   → healthy UserTransmitFilter 还原 UserContext
   → PetServiceFeign.listMyPets()  ──透传 username/token──→  pet 服务按登录态返回宠物列表
   → 看目标 pid 是否在列表里，不在就报「宠物不存在或无权访问」
```

- 透传配置在 common：`openfegin/config/FeignHeaderPropagationConfig`，
  用 `@FeignClient(configuration = ...)` 精确绑定，**刻意不加 `@Configuration`**，
  避免变成所有服务所有 Feign 调用的全局拦截器
- 报错刻意不区分「宠物不存在」和「不是你的宠物」，防止拿报错差异探测别人的宠物ID
- pet 服务不可用时直接报错，**不放行** —— 归属校验失败时放行等于开了后门

### 4.2 权益校验：健康模块只消费，不判断

「每类记录能记多少条 / 能不能导出 / 能不能 AI 解读」全部来自
`GET /vip/internal/benefit/{uid}`（common 的 `VipServiceFeign`）：

| 权益字段 | 在健康模块的作用 |
|---|---|
| `healthRecordLimit` | 写入任一记录前，按「宠物 + 记录类型」计数，达上限返回 `B000403` |
| `healthExport` | 导出接口的闸门，`0` 时返回 `B000404` |
| `healthAiAnalysis` | AI 症状解读接口的闸门（`POST /health/{pid}/ai-analysis`），`0` 时返回 `B000411` |
| `petLimitPerType` | 本模块不用（宠物数量由 pet 模块按**总数**把关），但概览会带上额度语义；字段名是历史遗留，语义已是"宠物总数上限" |

改价格、改额度只需要改 VIP 的档位表，健康模块一行代码都不用动。
权益服务不可用时同样**报错不放行**（等做高并发优化时再加本地缓存 + 降级）。

### 4.3 症状选项对接知识库：让症状变成结构化数据

症状不让用户自由填，而是从知识库的「健康自查」条目里点选（`categoryCode=health`，
猫 52 条 / 狗 46 条）：

- 存 `symptom_id`（关联知识库）+ `symptom_name`（名称快照）
- 写入时症状名以知识库条目名为准，前端传的会被覆盖 —— 避免同一症状出现
  「呕吐 / 吐了 / 想吐」三种写法，否则统计和后续 AI 解读都无从谈起
- 只存名称快照不存正文：知识库以后改文案，历史记录不会跟着变样
- 知识库里没有的症状允许自由填写（`symptomId` 为空 + 必填 `symptomName`）

## 五、启动前要做的三件事

1. **建表**（在 MiaoWang 库执行，脚本会 DROP 重建这六张表，首次执行即可）：

   ```
   MiaoWang-healthy/sql/healthy-ddl.sql
   ```

2. **Nacos 加配置**：`MiaoWang-service-healthy-dev.yaml`（DEFAULT_GROUP）

   ```yaml
   server:
     port: 8073            # 定版端口（2026-09 与其它模块一起排过，别再改）
   spring:
     application:
       name: miaowang-healthy
   ```

   数据源、Redis 等共用 `MiaoWang-all-service.yaml`；本模块的 `application.yml`
   已经导入这两个配置，Nacos 账号密码也配好了。

3. **网关不用加路由**：网关开的是 discovery locator，会按服务名自动生成
   `/miaowang-healthy/**` → `miaowang-healthy` 的路由，**不要手写 routes**（手写反而会和服务名对不上）。
   只需要确认白名单：除 `GET /health/symptom/options` 可放行给未登录用户看选项外，
   其余 `/health/**` 都要求登录。**注意**：本模块依赖 pet 的 `GET /v1/pet`，
   而该接口按登录态返回 —— 网关要保证服务间调用不被鉴权拦掉。

## 六、启动后怎么自检

自带一个直连各服务端口（绕过网关，便于定位问题）的联调脚本：

```bash
# 基础档：身份、宠物归属、依赖不可用时的表现（VIP 没起也能跑）
node MiaoWang-backproject/MiaoWang-healthy/scripts/smoke-healthy.mjs

# VIP 启动后再跑：权益放行 + 每类记录额度边界（B000403）+ 导出权益
node MiaoWang-backproject/MiaoWang-healthy/scripts/smoke-healthy.mjs full
```

用 `HEALTH_URL` 可以指向别的端口，例如本地新构建跑在 8074 时，用它对比
「改了代码但没重启的旧实例」和「新构建」：

```bash
HEALTH_URL=http://127.0.0.1:8074 node MiaoWang-backproject/MiaoWang-healthy/scripts/smoke-healthy.mjs
```

脚本用 `MapleLeaf1` 账号、自建自删测试宠物，不污染主账号数据。

**2026-09-17 实测结论（基础档 10/10，VIP 未启动的场景）**：带登录头能拿到自己的宠物（请求头透传打通）；
别人的宠物 `pid=1` 与已删除的 `pid=3` 都返回 `B000400`（跨账号隔离生效）；
VIP 不可用时概览与写操作都是 `B000405`，且**写入没有落库**（失败不放行）；
知识库不可用时 `B000408`（不是笼统的 `B000001`）；未登录返回「用户未登录或登录已过期」。
额外验证：pet 服务真挂掉时，归属校验对所有健康接口统一报 `B000401`，**不会放行**。

**2026-09-17 实测结论（full 档 14/14，VIP 启动后）**：概览与写入放行；免费档 `recordLimit=20`，
**第 21 条精确报 `B000403`**；导出报 `B000404`（免费档不支持导出）；时间线按时间倒序正确；
体重曲线把 20 条记录按天聚成 1 个点并取当天最后一次；
**过网关的真实路径也验过**（不带 token 被网关拦下、带 `username`+`token` 正确路由到本模块）。

> **单独启动本模块的坑**：项目用 `${revision}` 当版本号，脱离 reactor 单跑
> （`mvn -pl MiaoWang-healthy spring-boot:run`）会报找不到
> `MiaoWang-backproject:pom:${revision}`。可行做法：① 加 `-Drevision=1.0.0.0`；
> ② 先 `mvn -pl MiaoWang-healthy -am install -DskipTests`，再
> `java -jar .m2-sandbox-repo/com/MapleLeaf/MiaoWang/MiaoWang-healthy/1.0.0.0/MiaoWang-healthy-1.0.0.0.jar`
> （IDEA 里直接跑当然也可以）。注意 `java -jar` 起的是**打包时的代码**，
> 改完代码要重新 install 才会生效。

## 七、首屏热路径优化：让每请求的两次跨服务调用消失

### 7.1 改前：一次 `/overview` 的钱花在哪（实测）

| 组成部分 | 耗时 | 说明 |
|---|---|---|
| pet 归属校验（Feign） | 18ms | 每请求一次：查「我的宠物列表」判断 pid 归属 |
| vip 权益（Feign） | 43ms | 最贵的一跳；额度校验、导出校验还会各自再调一次 |
| 六类计数 | 6 次数据库往返 | 六张表各一条 `selectCount` |
| 5 笔明细查询 | 串行相加 | 下次疫苗、最新体重、体重变化、过敏、在用药物 |

顺序 100 次：mean 119.74ms / p50 109.91ms / p95 188.48ms；并发 25：QPS 28.3、p95 939.61ms。

### 7.2 改了八件事

**缓存**

1. **两级缓存**：L1 = 进程内 Caffeine（纳秒级、不吃网络），L2 = Redis（多实例共享）。
   归属 L1/L2 = 10s/30s，权益 L1/L2 = 30s/60s。
2. **单飞防击穿**：用 Caffeine 的 `get(key, fn)`，同一个 key 只放一个线程回源、
   其余线程等结果 —— 热点 key 失效那一瞬间不会有一堆请求同时打向下游。
3. **TTL 随机抖动防雪崩**：在 `[base, base×1.2)` 秒内随机取值，
   避免一批 key 在同一秒集体失效、把压力一次性推给下游。
4. **空结果也缓存防穿透**：没有宠物的用户缓存的是 `[]`，
   否则这类用户每次请求都会穿到 pet 服务（Redis 里实测就是 `[]`，不是空 key）。
5. **缓存故障降级**：Redis 读写失败只记 WARN 并当作未命中、继续回源 ——
   缓存不可用不能变成业务不可用。
6. **只加速放行，不参与拒绝**（最关键的一条）：命中缓存直接放行；
   但只要结论是「拒绝」（pid 不在缓存列表里 / 额度超了 / 不允许导出），
   **必须绕过缓存回源复核一次**再下结论。否则「刚建好宠物就录第一条记录」
   （缓存还没刷新）和「刚开通会员马上用」都会被缓存挡在门外。
   代价是被拒时多一次远程调用 —— 而被拒本来就是低频路径。

**查询**

7. **六次计数合成一条 SQL**：六张表的计数条件同构（`pid + del_flag`，且 pid 上都有索引），
   用一条 `UNION ALL` 一次往返拿回六类条数。
8. **明细查询并行 + 有界线程池**：5 笔互不依赖的查询丢进 `healthQueryExecutor`
   （核心 4 / 最大 8 / 队列 256 / `CallerRunsPolicy`）。线程池刻意开得比 Hikari 默认池（10）小：
   并行任务每个都要占一条连接，开大了只会一起堵在等连接上；队列满就退化成调用线程自己跑，
   保证「变慢」而不是「失败」。

### 7.3 效果（同一时段背靠背对比，抵消共享 VM 的噪声）

| 指标 | 改前 | 改后 | 变化 |
|---|---|---|---|
| 顺序 mean | 119.74ms | **40.57ms** | −66% |
| p50 | 109.91ms | **40.36ms** | −63% |
| p95 | 188.48ms | **56.77ms** | −70% |
| p99 | 228.41ms | **64.39ms** | −72% |
| 并发 25 的 QPS | 28.3 | **78.0** | 2.8× |
| 并发 25 的 p95 | 939.61ms | **361.39ms** | −62% |

缓存命中率（进程累计，含回归联调 + 压测；`GET /health/internal/cache-stats` 可查）：

- 归属：**98.4%** —— 310 次校验只真正回源 pet 5 次
- 权益：**99.3%** —— 304 次校验只真正回源 vip 2 次
- 也就是下游调用量从「每请求 2 次」降到约「每 100 请求 2 次」
- L2 命中在短压测里很少（L1 全兜住了），只有跨过 L1 TTL 的间隔才轮到它：
  实测预热后等 12 秒再请求，`l2Hit` 从 0 变 1，Redis 里 key 的 TTL 与内容都正确。
  L2 的价值在多实例部署和较长间隔，不是单实例短跑。

### 7.4 复现方式

```bash
# 压测（默认打 8073；每次自建宠物、预置固定条数、测完删掉，保证前后数据量一致）
node MiaoWang-backproject/MiaoWang-healthy/scripts/bench-overview.mjs
# 对比新构建
HEALTH_URL=http://127.0.0.1:8074 node MiaoWang-backproject/MiaoWang-healthy/scripts/bench-overview.mjs
```

脚本会打印组件耗时拆解（两跳各多少毫秒）和结束时各缓存的命中率。

### 7.5 还能再压的地方

- 剩下的 ~40ms 主要就是 4~5 次数据库往返：要再降得把明细查询也批量成更少的语句，
  或给概览响应本身加 5~10 秒短 TTL（收益更明显，但要接受首屏可能看到略旧的数据）
- 一致性目前只靠 TTL 收敛：VIP 改档、删除宠物之后，其它实例最长会旧 TTL 那么多秒；
  要做实时失效需要 VIP/pet 主动删缓存（跨模块）
- `/health/internal/cache-stats` 现在走普通登录鉴权，正式上线应挪到内网或运维鉴权

## 八、待办与已知边界

- ✅ **AI 症状解读已实现**：`POST /health/{pid}/ai-analysis` —— 取该宠物最近 10 条症状记录拼成问题，
  调 ai-knowledge 的服务间接口 `/ai/rag/internal/analyze`（知识库检索 + 大模型生成），
  返回解读正文 + 知识库依据 + 免责声明。口径：**不消耗 RAG 问答配额**（属于档位功能：
  有解读权限的档位 RAG 本来就不限次数），闸门只看 `healthAiAnalysis`；成本记进
  `miaowang_ai_call_log`（scene=`analyze`，uid 由请求体带过去做归因）
- **AI 解读的滥用防护未做**：34.9 / 40 档位不设次数上限，恶意用户可反复调用烧成本
  （挂账：按用户做频控或日限，或复用 VIP 的额度模型加一个 `ai_analysis` biz）
- **提醒联动未接**：`next_due_at` 已经落库并进概览，reminder 模块可直接据此生成提醒，
  但提醒的「完成打卡历史」归 reminder 模块，本模块不重复存
- **附件上传已接，但隐私策略不够**：`POST /health/file` 复用 common 的 `FileStorageService`
  （在 common 新增了 `FileBizTypeEnum.HEALTH_IMAGE` → 桶 `miaowang-health`，首次上传自动建桶），
  但该实现是「公共读桶 + 直链」，适合头像/帖子图片；化验单属于敏感数据，
  正式上线应改为私有桶 + 签名 URL（代价是签名会过期，入库要存 `objectKey` 而不是 URL）
- **列表接口没有分页**：当前是全量按时间倒序返回（体重记录量大了会撑不住），
  后续要改成游标分页（按时间 + id 翻页，避免深分页）
- **概览与导出里的 DTO 转换在两个类里各写了一份**（`HealthOverviewServiceImpl` 与三个
  CRUD 实现类），可以抽成一个 `HealthRecordConverter` 统一维护
- ✅ **校验缓存的并发优化已落地**：归属与权益都做了 L1(Caffeine)+L2(Redis) 两级缓存 +
  单飞 + TTL 抖动，概览的 6 次计数合成一条 SQL、明细查询并行 —— 详见「七、首屏热路径优化」。
  仍未做的是一致性主动失效（现在只靠 TTL 收敛）
- **删除宠物不会清理健康记录**（联调时实测发现）：pet 的删除是**软删**（`del_flag=1` + `deletion_time`），
  本模块既不感知也不会清理，于是这些记录成了「接口查不到、库里还在」的孤儿数据
  （本次联调后手动清了 40 行）。要做干净，需要 pet 删除时发事件或调本模块内部接口级联 —— 跨模块待办
- **隐私合规**：病历/过敏史属于敏感数据，正式上线要补「彻底删除」接口、
  导出文件用带签名的临时 URL、以及访问日志
- **时间线里过敏史借用了录入时间**：过敏史表没有「发生时间」列（用户往往也说不上是哪天过敏的），
  时间线锚点用的是 `create_time`