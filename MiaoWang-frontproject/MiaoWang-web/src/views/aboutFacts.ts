/**
 * 「关于喵汪」页上全部**可核对**的事实常量（Web 端）
 *
 * 为什么单独一个纯数据模块（而不是写在 SFC 里）：
 * `views/AboutView.vue` 只负责排版，事实集中在这里，离线门禁
 * `tools/check-web-mine-rest.js` 就能把这份表**真跑起来**（TS 转译后直接求值），
 * 再逐条对着真文件比对：
 * - `ABOUT_VERSION` ↔ `MiaoWang-web/package.json` 的 `version`（逐字）
 * - `WEB_DEPS[].declared` ↔ `MiaoWang-web/package.json` 的对应依赖声明（逐字）
 * - `WEB_DEPS[].version` / `.license` ↔ `node_modules/<包名>/package.json`（逐字）
 * - `MODEL_FACT` ↔ GLB 自己的 JSON chunk（`asset.extras`）与文件字节数（逐字）
 *
 * 所以改这里的任何一条而不同步真文件，门禁就会红 —— 这是"不许编数据"的落地手段，
 * 不是装饰。反过来，真文件变了（比如升级了依赖）也必须改这里，否则同样红。
 *
 * ⚠️ 三条纪律（派单铁律 + `miaowang-design-system` §7）：
 * 1) **不许编**：每条都写明 `src`（读的哪个文件），读不到来源的宁可空着也不猜。
 * 2) **不许吹**：不写"已上线""服务 N 万用户"这类无法证实的话（本工程只在本地开发）。
 * 3) 这些字符串会**直接显示给用户**，所以不用破折号（`—` / `——`）、不用 `·` 当装饰分隔符。
 */

/**
 * 版本号：来源 `MiaoWang-web/package.json` 的 `version` 字段。
 *
 * ⚠️ 它现在的值是 `0.0.0` —— 那是 create-vue 脚手架的默认值，**这个工程从来没有发布过版本**。
 * 页面上照实显示，并明说这件事；**不编一个更好看的号**（比如 1.0.0）来撑门面。
 * 全页只有这一处常量（页脚与英雄区引用的是同一个），门禁会拿 package.json 逐字比对。
 */
export const ABOUT_VERSION = '0.0.0'

/** `ABOUT_VERSION` 的读取来源（页面上要原样写出来，让人能自己核） */
export const ABOUT_VERSION_SRC = 'MiaoWang-web/package.json 的 version'

/**
 * 第三方依赖与开源许可。
 *
 * - `declared`：`MiaoWang-web/package.json` 里声明的版本区间（原样，含 `^` / `~`）
 * - `version`：**实际装上的**版本，读自 `node_modules/<name>/package.json` 的 `version`
 * - `license`：同一个文件里的 `license` 字段
 * - `where`：运行期依赖还是开发期依赖（`dependencies` / `devDependencies`）
 *
 * 范围说明（诚实地划边界，不是漏写）：这里列的是**会进产物或决定产物**的那几项。
 * 开发工具链（eslint / oxlint / prettier / vue-tsc / npm-run-all2 等）没有列进页面，
 * 它们的版本同样真实存在，要看全量请读 `package.json` 与 `package-lock.json`。
 *
 * ⚠️ `@types/three` 是**只有类型**的包（运行期不产生任何代码），列出来是因为它写在
 * `dependencies` 里；它不参与打包。
 */
export interface WebDep {
  name: string
  declared: string
  version: string
  license: string
  where: string
  use: string
  src: string
}

export const WEB_DEPS: WebDep[] = [
  {
    name: 'vue',
    declared: '^3.5.42',
    version: '3.5.42',
    license: 'MIT',
    where: '运行期',
    use: '前端框架',
    src: 'node_modules/vue/package.json',
  },
  {
    name: 'vue-router',
    declared: '^5.3.1',
    version: '5.3.1',
    license: 'MIT',
    where: '运行期',
    use: '页面路由与登录守卫',
    src: 'node_modules/vue-router/package.json',
  },
  {
    name: 'pinia',
    declared: '^4.0.3',
    version: '4.0.3',
    license: 'MIT',
    where: '运行期',
    use: '状态管理（登录态、宠物、健康记录等）',
    src: 'node_modules/pinia/package.json',
  },
  {
    name: 'three',
    declared: '^0.160.0',
    version: '0.160.0',
    license: 'MIT',
    where: '运行期',
    use: '首页 3D 电子宠物舞台',
    src: 'node_modules/three/package.json',
  },
  {
    name: '@types/three',
    declared: '^0.160.0',
    version: '0.160.0',
    license: 'MIT',
    where: '运行期依赖里的类型包',
    use: 'three 的类型声明（只有类型，不进打包产物）',
    src: 'node_modules/@types/three/package.json',
  },
  {
    name: '@phosphor-icons/vue',
    declared: '^2.2.1',
    version: '2.2.1',
    license: 'MIT',
    where: '运行期',
    use: '图标库（官方图标，不手绘 SVG 路径）',
    src: 'node_modules/@phosphor-icons/vue/package.json',
  },
  {
    name: 'qrcode',
    declared: '^1.5.4',
    version: '1.5.4',
    license: 'MIT',
    where: '运行期',
    use: '渲染支付宝沙箱的支付二维码',
    src: 'node_modules/qrcode/package.json',
  },
  {
    name: 'vite',
    declared: '^8.2.2',
    version: '8.3.0',
    license: 'MIT',
    where: '开发期',
    use: '开发服务器与生产构建（含到网关的 dev 代理）',
    src: 'node_modules/vite/package.json',
  },
  {
    name: 'typescript',
    declared: '~6.0.0',
    version: '6.0.3',
    license: 'Apache-2.0',
    where: '开发期',
    use: '类型检查（vue-tsc 基于它）',
    src: 'node_modules/typescript/package.json',
  },
]

/**
 * 3D 模型的真实元数据。
 *
 * 前五项**不是我们贴上去的标签**，而是 GLB 文件自己的 JSON chunk 里 `asset.extras` 那一段
 * （读法可复现：GLB 头 12 字节之后是第一个 chunk，先读它的长度再解析 JSON）。
 * `bytes` 是文件本身的大小。
 *
 * 这份表在 Web 端指的是 `public/models/` 那一份；手机端工程内还有一份同内容的副本
 * （`MiaoWang-phone/static/model/cat/`），字节数一致。
 */
export interface ModelFact {
  file: string
  bytes: number
  bytesText: string
  title: string
  author: string
  authorUrl: string
  license: string
  licenseUrl: string
  source: string
  generator: string
}

export const MODEL_FACT: ModelFact = {
  file: 'public/models/toon_cat_free.glb',
  bytes: 205288,
  bytesText: '205,288 字节（约 200 KB）',
  title: 'Toon Cat FREE',
  author: 'Omabuarts Studio',
  authorUrl: 'https://sketchfab.com/omabuarts',
  license: 'CC-BY-4.0',
  licenseUrl: 'http://creativecommons.org/licenses/by/4.0/',
  source: 'https://sketchfab.com/3d-models/toon-cat-free-b2bd1ee7858444bda366110a2d960386',
  generator: 'Sketchfab-12.66.0',
}

/**
 * 后端与工程现状的诚实口径。
 *
 * 这几句是**产品事实**，不是文案修辞：本工程只在本地开发，没有上线运营，
 * 也没有任何真实用户规模数据可以公布，所以页面上**一个数字都不写**。
 * 与手机端 `MiaoWang-phone/pages/about/about.vue` 是同一套口径（两端同源）。
 */
export const ENGINE_STATUS = {
  /** 一句话现状 */
  summary: '本地开发中的演示工程，没有上线运营，也没有真实用户规模数据可以公布。',
  /** 后端形态（照着仓库里的模块写的） */
  backend: 'Spring Cloud Alibaba 微服务：网关加用户、宠物、社区、知识库、AI 问答、电子宠物、会员、健康等模块。',
  /** 前端形态 */
  frontend: '手机端（uni-app 加 Vue 3）与 Web 端（Vue 3 加 TypeScript 加 Vite）两个前端。',
  /** 内容来源 */
  content: '知识库文章由大模型生成后人工审校；AI 问答的答案会带上引用来源。',
} as const