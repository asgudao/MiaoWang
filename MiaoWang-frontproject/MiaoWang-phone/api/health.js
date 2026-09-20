/**
 * 健康记录接口（手机端 / JS）
 *
 * 契约唯一来源：
 *  - `.dsh-tmp/healthy-contract.md` 末节「实测更新（2026-09-20，有真实数据后）」（冲突时以它为准）
 *  - skill `miaowang-api-contract` 的 healthy 一节
 *
 * 这份文件把**实测踩过的坑**写在调用点上，避免下次再按 REST 惯例猜：
 *
 * 1) **列表全是裸数组** `data:[...]`：preventive / medical / medication / allergy / symptom /
 *    weight / weight/curve / timeline —— **没有 `pageNum/pageSize/total/pages`，也没有
 *    `records/hasMore/nextCursor`**。别照社区那套信封写。
 *    条数控制只有两个参数：`timeline?limit`（默认 50 / 上限 200）、`weight/curve?months`（默认 6 / 上限 24）。
 * 2) **写接口返回裸数字 id**（POST → `1` / `111`，JSON number），PUT / DELETE → `data: null`。
 *    业务失败也是 **HTTP 200**（只有"服务名写错"才是 404 + Spring 默认 JSON）。
 * 3) ⚠️ **PUT 是全量覆盖**：不传的字段会被置成 `null`（实测 preventive 省略 `batchNo` → 读回 `null`）。
 *    → **禁止用 PUT 做局部更新**：必须先 `GET` 拿到全量 DTO，改完**整体 PUT**。
 *    本文件只负责发请求；"DTO → 表单 → 完整 body"的纯函数在 `utils/health.js`
 *    （`*FormFromDto` / `*SaveReqFromForm`），那一对就是"读全量 → 合并 → 整体 PUT"的实现。
 * 4) ⚠️ **附件 `attachments` / `images`：入参是逗号串（String）、出参是数组（List<String>）**；
 *    没有附件时**出参是 `[]` 不是 `null`**。转换也用 `utils/health.js` 的 `joinList` / `splitList`。
 * 5) ⚠️ **时间一律 `yyyy-MM-dd HH:mm:ss`（GMT+8，非 ISO）**，入参也吃这个格式（不传/格式错 → `B000407`）。
 *    例外：`weight/curve.date` 与宠物 `birth` 是 `yyyy-MM-dd`。
 * 6) ⚠️ **传 `symptomId` 会覆盖 `symptomName`**（POST 与 PUT 都成立）：要么选知识库选项，
 *    要么自由填写（此时**不要传 `symptomId`**）。非法 `symptomId` → `B000409`。
 * 7) ⚠️ **三个筛选参数三种行为**（别统一处理）：
 *    - `preventive?type` 非法 → **静默忽略**（返回全部，HTTP 200 `code:"0"`），且**大小写不敏感**
 *    - `medication?status` 越界 → **按值过滤**（`status=99` → `data:[]`），不报错也不是忽略
 *    - `timeline?types` 非法 → **`A000001` 报错**（消息「不支持的记录类型：BOGUS」）
 * 8) ⚠️ **`/health/**` 整个前缀经网关都要登录**（2026-09-20 实测匿名 → `{state:-1}`），
 *    连技能表里标着"可免登录"的 `symptom/options` 也一样被拦 → 本文件**不传 `anonymous`**。
 * 9) ⚠️ **绝对不要调 `/health/internal/**`**：网关 `/internal/**` 黑名单一律 403，
 *    而请求层见 403 会**清登录态**（会把用户踢下线）。本文件根本不封装它。
 * 10) 两个付费墙接口（`POST /{pid}/ai-analysis`、`GET /{pid}/export`）本账号档位不够，
 *    **成功形状未观测**；调用方必须把 `B000404` / `B000411` 当**正常状态**（给升级提示，不要当报错、不要重试）。
 */
import { api, authHeaders, buildUrl } from '../utils/request.js'
import { setHealthDisplayHostResolver } from '../utils/health.js'

const MODULE = 'healthy'

/* ------------------------------------------------------------------
 * 存量附件地址的展示兜底（挂账 #67/#68 的窄口子）
 *
 * 库里存的是上传当时的**绝对 URL**；种子阶段用 `127.0.0.1:7000` 上传的附件就永久是
 * `http://127.0.0.1:9000/...`，手机解析它只会打到自己 → 历史附件必裂图。
 * 展示层（`utils/health.js` 的 `displayAssetList`）会把**只有** `127.0.0.1` / `localhost`
 * 这两个主机名换成"当前请求真正在用的 API 主机"，换法见那边的 2b 节注释。
 *
 * 主机从哪来：**问 buildUrl**（和请求用的是同一个地址规则，不会出现"上传一个网段、展示另一个网段"）——
 *   - App / 真机：`http://192.168.225.183:7000/miaowang-healthy/…` → `192.168.225.183`
 *   - H5：buildUrl 是相对路径（走 manifest 的 devServer 代理）→ 退回**页面自身主机**，
 *     也就是代理与 9000 转发所在的机器
 * 注册的是**函数**不是快照：真机地址自愈换网段后，下一次渲染拿到的是新主机。
 * ------------------------------------------------------------------ */

function currentApiHostForDisplay () {
  const url = buildUrl(MODULE, '/health/file')
  const m = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/([^/?#]+)/.exec(url)
  if (m) return m[1]
  // H5：BASE 是空的（相对路径）。window 在 App 运行时不存在，`typeof` 判断是安全的。
  if (typeof window !== 'undefined' && window.location) return window.location.hostname || ''
  return ''
}

setHealthDisplayHostResolver(currentApiHostForDisplay)

/** `timeline?limit` 默认值（后端默认 50） */
export const TIMELINE_LIMIT_DEFAULT = 50
/** `timeline?limit` 上限（后端钳到 [1,200]，上限未能实测——记录只有 11 条） */
export const TIMELINE_LIMIT_MAX = 200
/** `weight/curve?months` 默认值（后端默认 6） */
export const CURVE_MONTHS_DEFAULT = 6
/** `weight/curve?months` 上限（实测钳到 [1,24]：`999`≡`24`、`0`/`-5`≡`1`） */
export const CURVE_MONTHS_MAX = 24

/* ------------------------------------------------------------------
 * 六类记录：读
 * 全部是**裸数组**，调用方直接拿到 `[...]`
 * ------------------------------------------------------------------ */

/**
 * 疫苗 / 驱虫（合并表，按 `happenedAt` 倒序）
 * @param {number|string} pid
 * @param {string} [type] VACCINE | DEWORM_IN | DEWORM_OUT —— ⚠️ 非法值会被**静默忽略**（返回全部）
 */
export function listPreventive (pid, type) {
  return api.get(MODULE, '/health/' + pid + '/preventive', { query: { type: type } })
}

/** 就诊 / 病史（按 `visitAt` 倒序；`attachments` 出参是数组） */
export function listMedical (pid) {
  return api.get(MODULE, '/health/' + pid + '/medical')
}

/**
 * 用药（按 `startAt` 倒序）
 * @param {number|string} pid
 * @param {number} [status] 0 用药中 / 1 已结束 / 2 已停用 —— ⚠️ 越界值按值过滤（返回 `[]`）
 */
export function listMedication (pid, status) {
  return api.get(MODULE, '/health/' + pid + '/medication', { query: { status: status } })
}

/** 过敏史（`severity` 倒序，重度在前；**没有发生时间列**，用录入时间排序） */
export function listAllergy (pid) {
  return api.get(MODULE, '/health/' + pid + '/allergy')
}

/** 症状日记（按 `occurredAt` 倒序；`images` 出参是数组，无图是 `[]`） */
export function listSymptom (pid) {
  return api.get(MODULE, '/health/' + pid + '/symptom')
}

/** 体重（按 `measuredAt` 倒序；⚠️ 实测 id 是 111/112，别假设从 1 开始） */
export function listWeight (pid) {
  return api.get(MODULE, '/health/' + pid + '/weight')
}

/**
 * 体重曲线点：`[{date:"yyyy-MM-dd", weightKg, count}]`（按天聚合取当天最后一次，`date` 升序）
 * @param {number|string} pid
 * @param {number} [months] 默认 6；后端钳到 [1,24]，非法值不报错
 */
export function weightCurve (pid, months) {
  return api.get(MODULE, '/health/' + pid + '/weight/curve', {
    query: { months: months === undefined || months === null ? CURVE_MONTHS_DEFAULT : months }
  })
}

/* ------------------------------------------------------------------
 * 聚合
 * ------------------------------------------------------------------ */

/**
 * 首屏概览（对象，不是数组）
 *
 * `{pid, petName, species, totalRecords, recordCounts{6 个 key 恒在}, recordLimit,
 *   limitReachedTypes[], nextPreventive|null, latestWeight|null, weightChangeKg|null,
 *   allergies[≤10], activeMedications[≤10 仅 status=0], exportAllowed, aiAnalysisAllowed}`
 *
 * ⚠️ `nextPreventive` **只给"未来最近一条"**（源码 `.ge(nextDueAt, now)`）→ **逾期项拿不到**，
 * 要自己拉 `listPreventive(pid)` 按 `nextDueDays` 分档（见 `utils/health.js` 的 `overdueItems`）。
 * ⚠️ `weightChangeKg` 在没有"≤30 天前基线记录"时是 `null`。
 */
export function overview (pid) {
  return api.get(MODULE, '/health/' + pid + '/overview')
}

/**
 * 时间线（六类合并，时间倒序；**裸数组**）
 * @param {number|string} pid
 * @param {object} [p] `{ types, limit }`
 *   `types` 逗号分隔（如 `'preventive,symptom'`），**大小写不敏感**，
 *   ⚠️ **非法值直接报 `A000001`**（与 `preventive?type` 的静默忽略相反）
 *   `limit` 默认 50 / 上限 200
 */
export function timeline (pid, p) {
  const o = p || {}
  return api.get(MODULE, '/health/' + pid + '/timeline', {
    query: {
      types: o.types,
      limit: o.limit === undefined || o.limit === null ? TIMELINE_LIMIT_DEFAULT : o.limit
    }
  })
}

/**
 * 症状选项（知识库「健康自查」症状，猫 52 / 狗 46 条）
 *
 * ⚠️ 技能表标着"可免登录"，但 **2026-09-20 实测经网关匿名请求回 `{state:-1}`** ——
 * `/health/**` 整个前缀都不在网关白名单里，所以这里**必须带登录头**。
 * @param {number} species 1 猫 / 2 狗（**不是**中文大类）
 */
export function symptomOptions (species) {
  return api.get(MODULE, '/health/symptom/options', { query: { species: species } })
}

/* ------------------------------------------------------------------
 * 六类记录：写
 *
 * ⚠️ `update*` 一律要求传**完整 body**（见文件头第 3 条：PUT 是全量覆盖）。
 *    页面必须先 `list*` / 用详情 DTO 填满表单，再整体 PUT。
 *    "DTO → 表单"与"表单 → body"的纯函数在 `utils/health.js`：
 *      preventiveFormFromDto(dto) ↔ preventiveSaveReqFromForm(form)
 *      medicalFormFromDto ↔ medicalSaveReqFromForm
 *      medicationFormFromDto ↔ medicationSaveReqFromForm
 *      allergyFormFromDto ↔ allergySaveReqFromForm
 *      symptomFormFromDto ↔ symptomSaveReqFromForm
 *      weightFormFromDto ↔ weightSaveReqFromForm
 * ------------------------------------------------------------------ */

/** POST → 裸数字新 id；`type` 非法 / `itemName` 空 / `happenedAt` 空 → `B000407` */
export function createPreventive (pid, body) {
  return api.post(MODULE, '/health/' + pid + '/preventive', { body: body })
}

/** PUT → `data:null`；记录不存在或不是自己的 → `B000402` */
export function updatePreventive (id, body) {
  return api.put(MODULE, '/health/preventive/' + id, { body: body })
}

/** DELETE → `data:null`（逻辑删除）；`B000402`。
 *  ⚠️ 成功分支**从未真调过**（契约受约束只打不存在的 id）→ 按"成功即从列表移除"处理，失败回滚。 */
export function removePreventive (id) {
  return api.del(MODULE, '/health/preventive/' + id)
}

/** `visitAt` 空**或 `diagnosis` 空** → `B000407`（诊断必填） */
export function createMedical (pid, body) {
  return api.post(MODULE, '/health/' + pid + '/medical', { body: body })
}

export function updateMedical (id, body) {
  return api.put(MODULE, '/health/medical/' + id, { body: body })
}

export function removeMedical (id) {
  return api.del(MODULE, '/health/medical/' + id)
}

/** `medicineName` 空或 `startAt` 空 → `B000407`；`status` 不传时由 `endAt` 推断 */
export function createMedication (pid, body) {
  return api.post(MODULE, '/health/' + pid + '/medication', { body: body })
}

/** 结束用药也走这里：`status` 补上 + `endAt` 补上（**其余字段必须一起传**，PUT 全量覆盖） */
export function updateMedication (id, body) {
  return api.put(MODULE, '/health/medication/' + id, { body: body })
}

export function removeMedication (id) {
  return api.del(MODULE, '/health/medication/' + id)
}

/** `allergen` 空 → `B000407`；`severity` 不传默认 1 */
export function createAllergy (pid, body) {
  return api.post(MODULE, '/health/' + pid + '/allergy', { body: body })
}

export function updateAllergy (id, body) {
  return api.put(MODULE, '/health/allergy/' + id, { body: body })
}

export function removeAllergy (id) {
  return api.del(MODULE, '/health/allergy/' + id)
}

/** `occurredAt` 空 / 既不传 `symptomId` 也不传 `symptomName` → `B000407`；`symptomId` 非法 → `B000409` */
export function createSymptom (pid, body) {
  return api.post(MODULE, '/health/' + pid + '/symptom', { body: body })
}

export function updateSymptom (id, body) {
  return api.put(MODULE, '/health/symptom/' + id, { body: body })
}

export function removeSymptom (id) {
  return api.del(MODULE, '/health/symptom/' + id)
}

/** `weightKg` 必须在 0.05 ~ 200 kg，否则 `A000001` + 「体重需在 0.05 ~ 200 kg 之间」；`measuredAt` 空 → `B000407` */
export function createWeight (pid, body) {
  return api.post(MODULE, '/health/' + pid + '/weight', { body: body })
}

export function updateWeight (id, body) {
  return api.put(MODULE, '/health/weight/' + id, { body: body })
}

export function removeWeight (id) {
  return api.del(MODULE, '/health/weight/' + id)
}

/* ------------------------------------------------------------------
 * 附件上传 / 导出 / AI 解读
 * ------------------------------------------------------------------ */

/**
 * 上传健康档案图片（化验单 / 影像 / 症状照片），`multipart/form-data` 字段名 **`file`**，≤10MB
 * @param {string} filePath `uni.chooseImage` 给的本地临时路径
 * @returns {Promise<{url:string, objectKey:string}>}
 *
 * ⚠️ 记录 DTO 里**没有任何字段能装 `objectKey`** → 落库只有绝对 URL（挂账 #67：删不掉 MinIO 对象）。
 * ⚠️ 下发的 URL **按本次请求 Host 推导**（`X-Forwarded-Host` → `Host`）：用 `127.0.0.1` 存下的附件
 *    在手机上看必然裂图（手机上 `127.0.0.1` 是手机自己）。真机联调要用局域网 IP 起请求。
 *    存量数据由展示层兜底（`utils/health.js` 的 `displayAssetList`，只重写 `127.0.0.1` / `localhost`），
 *    但**新上传必须让请求打到手机能到的 Host** —— 兜底只治历史遗留，不治新写坏的数据。
 * ⚠️ 本端**从未真调过**这个接口（后端已实测通过）：调用方按失败可重试处理。
 * 这里不走 `api.*`：`uni.request` 发不了 multipart，必须用 `uni.uploadFile`。
 */
export function uploadHealthImage (filePath) {
  // 复用 utils/request.js 的 buildUrl：H5 走 devServer 代理（相对路径）、App 直连 BASE，
  // 与普通请求**同一个地址规则**（真机换网段后上传和查询不会一个通一个 404）。
  return new Promise(function (resolve, reject) {
    uni.uploadFile({
      url: buildUrl('healthy', '/health/file'),
      filePath: filePath,
      name: 'file',
      header: authHeaders(),
      success: function (res) {
        let env = res.data
        try {
          env = JSON.parse(res.data)
        } catch (e) {
          reject(new Error('上传返回的不是 JSON：' + String(res.data).slice(0, 120)))
          return
        }
        if (res.statusCode === 401 || res.statusCode === 403) {
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        if (!env || env.code !== '0' || !env.data) {
          reject(new Error((env && env.message) || '图片上传失败'))
          return
        }
        resolve(env.data)
      },
      fail: function () {
        reject(new Error('图片上传失败（连不上后端或文件读不到）'))
      }
    })
  })
}

/**
 * 导出完整健康档案（结构化 JSON）
 * ⚠️ **付费墙**：档位 `healthExport=0` → `B000404`（实测 `probe114904` 正是）。
 *    **成功形状至今未观测**（需要 AI_249 / 24.9 及以上档位）→ 调用方必须把 `B000404`
 *    当**正常状态**（明确的升级提示），不要当报错、不要重试。
 */
export function exportArchive (pid) {
  return api.get(MODULE, '/health/' + pid + '/export')
}

/**
 * AI 症状解读（会员功能，`POST` 而不是 GET：每次都真调大模型、结果不可缓存）
 *
 * 返回形状 `{pid, petName, symptomCount, analysis, hasKnowledge, sources:[{id,title,categoryCode}],
 * disclaimer, generatedAt}`（**成功体未观测**）。
 * ⚠️ 闸门顺序「归属 → `healthAiAnalysis` → 有没有症状记录 → 调模型」：
 *    档位不支持 → `B000411`（实测本账号就是这条，60ms、模型未被调用、0 token）；
 *    `B000412`（还没有症状记录）在无解读权限的档位上**永远不可达**；`B000413` ai 服务异常。
 * ⚠️ **不消耗 RAG 问答配额**。返回体里的 `disclaimer` 前端**必须展示**。
 * ⚠️ 长回答要有心理预期（一次十几秒）→ 这里显式放宽超时。
 */
export function aiAnalysis (pid) {
  return api.post(MODULE, '/health/' + pid + '/ai-analysis', { timeout: 120000 })
}