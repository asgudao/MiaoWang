<script setup lang="ts">
/**
 * 关于喵汪（纯静态页，零网络请求）
 *
 * 三条纪律（派单铁律 + `miaowang-design-system` §7）：
 * 1) **不许编**：版本号、依赖版本与许可证、3D 模型授权，**全部来自可核对的文件**
 *    （`views/aboutFacts.ts` 是唯一的事实表，每条都写了读的是哪个文件），页面上写明来源；
 * 2) **不许吹**：不写"已上线""服务 N 万用户"这类无法证实的话 —— 本工程只在本地开发；
 * 3) 可见文案里不用破折号（`—` / `——`）、不用 `·` 当装饰分隔符。
 *
 * 事实表单独放在 `views/aboutFacts.ts`，是为了让离线门禁 `tools/check-web-mine-rest.js`
 * 能把那份表**真跑起来**再逐条对着真文件比对（package.json / node_modules / GLB 元数据）。
 *
 * 与手机端 `MiaoWang-phone/pages/about/about.vue` 是同一套口径（两端同源）：
 * 手机端的版本号读 `manifest.json`（HBuilderX 工程运行时读不到那个文件，所以镜像成常量），
 * Web 端的版本号读 `package.json`。两边都**照实显示脚手架默认的版本号**，不美化。
 */
import {
  ABOUT_VERSION,
  ABOUT_VERSION_SRC,
  ENGINE_STATUS,
  MODEL_FACT,
  WEB_DEPS,
} from './aboutFacts'
</script>

<template>
  <div class="mw-container mw-page">
    <!-- 深色英雄块：与首页舞台 / 我的页资料卡同族 -->
    <section class="hero mw-night">
      <p class="hero__row">
        <span class="hero__brand">喵汪</span>
        <span class="hero__ver mw-num">v{{ ABOUT_VERSION }}</span>
      </p>
      <p class="hero__desc">
        猫狗照护助手与电子宠物伴侣。这是一个边学边做的演示工程，不是已上线的商业产品。
      </p>
    </section>

    <section class="mw-card card">
      <h2 class="mw-h2">关于这个工程</h2>
      <p class="card__p">{{ ENGINE_STATUS.frontend }}</p>
      <p class="card__p">后端是一套 {{ ENGINE_STATUS.backend }}</p>
      <p class="card__p">{{ ENGINE_STATUS.content }}</p>
      <p class="card__note">
        当前状态：{{ ENGINE_STATUS.summary }}所以这个页面不写任何访问量、注册量或服务人数。
      </p>
    </section>

    <section class="mw-card card">
      <h2 class="mw-h2">版本</h2>
      <div class="kv">
        <span class="kv__k">版本号</span>
        <span class="kv__v mw-num">{{ ABOUT_VERSION }}</span>
      </div>
      <p class="card__note">
        来源：{{ ABOUT_VERSION_SRC }}。全页只有这一处常量（页脚与英雄区引用的是同一个），
        离线门禁会拿 package.json 逐字比对，改一边漏一边就变红。
        这个值现在是脚手架的默认值，工程从来没有对外发布过版本，页面上照实显示，不编一个更好看的号。
      </p>
    </section>

    <section class="mw-card card">
      <h2 class="mw-h2">第三方依赖与开源许可</h2>
      <ul class="deps">
        <li v-for="d in WEB_DEPS" :key="d.name" class="dep">
          <div class="dep__head">
            <span class="dep__name">{{ d.name }}</span>
            <span class="dep__ver mw-num">{{ d.version }}</span>
          </div>
          <p class="dep__meta">
            <span class="mw-badge mw-badge--done">{{ d.license }}</span>
            <span class="dep__where">{{ d.where }}</span>
            <span class="dep__declared mw-num">package.json 声明 {{ d.declared }}</span>
          </p>
          <p class="dep__use">{{ d.use }}</p>
          <p class="dep__src">来源：{{ d.src }}</p>
        </li>
      </ul>
      <p class="card__note">
        每条的实际版本与许可证都直接读自它自己声明的文件（node_modules/&lt;包名&gt;/package.json
        的 version 与 license 两个字段），声明的版本区间读自 MiaoWang-web/package.json，
        都不是凭记忆写的。这里列的是会进产物或决定产物的那几项；开发工具链
        （eslint / oxlint / prettier / vue-tsc 等）没有列进页面，它们的版本同样真实存在，
        要看全量请读 package.json 与 package-lock.json。
        手机端的框架 uni-app 由 HBuilderX 提供，工程内没有 package.json，所以本页不列它。
      </p>
    </section>

    <section class="mw-card card">
      <h2 class="mw-h2">3D 模型授权</h2>
      <div class="kv">
        <span class="kv__k">文件</span>
        <span class="kv__v kv__v--wrap">{{ MODEL_FACT.file }}</span>
      </div>
      <div class="kv">
        <span class="kv__k">体积</span>
        <span class="kv__v mw-num">{{ MODEL_FACT.bytesText }}</span>
      </div>
      <div class="kv">
        <span class="kv__k">标题</span>
        <span class="kv__v">{{ MODEL_FACT.title }}</span>
      </div>
      <div class="kv">
        <span class="kv__k">作者</span>
        <span class="kv__v">{{ MODEL_FACT.author }}</span>
      </div>
      <div class="kv">
        <span class="kv__k">许可</span>
        <span class="kv__v"><span class="mw-badge mw-badge--done">{{ MODEL_FACT.license }}</span></span>
      </div>
      <p class="card__p mono">{{ MODEL_FACT.licenseUrl }}</p>
      <p class="card__p mono">{{ MODEL_FACT.source }}</p>
      <p class="card__p">
        以上五项是模型文件自己的元数据（GLB 里 asset.extras 那一段）写明的，不是我们贴上去的标签。
        {{ MODEL_FACT.license }} 允许商用，但署名是强制条款：必须标明作者 {{ MODEL_FACT.author }}
        并附上许可链接。
      </p>
      <p class="card__note">
        红线与现状要分开说。工程内任何界面此前都没有展示过这份署名，这一页是第一次；
        也就是说，署名义务目前还没有被满足，上架或对外分发前必须补齐，否则就是违反许可，与免不免费无关。
        技能库 .agents/skills/miaowang-3d-model/SKILL.md 把这条记为「免费不等于可商用，
        付费会员权益上线前必须确认模型素材的商业授权」，口径比模型自带元数据更保守；
        以模型文件自带的 {{ MODEL_FACT.license }} 为准的话，要补的是署名，而不是再买一次授权。
        两处不一致的地方如实列在这里，免得以后有人只读到其中一处就下结论。
      </p>
    </section>

    <section class="mw-card card">
      <h2 class="mw-h2">内容与免责</h2>
      <p class="card__p">
        知识库与 AI 问答给出的养护建议仅供参考，不能替代执业兽医的当面诊断。宠物出现异常请及时就医。
      </p>
      <p class="card__p">
        社区内容由用户发布，点赞、评论、关注与消息通知都只对本人可见。
      </p>
    </section>

    <p class="foot mw-num">喵汪演示工程 v{{ ABOUT_VERSION }}</p>
  </div>
</template>

<style scoped>
/* ---- 深色英雄块 ---- */
.hero {
  padding: var(--mw-space-6);
  border-radius: var(--mw-radius-card);
  margin-bottom: var(--mw-space-4);
}
.hero__row {
  display: flex;
  align-items: baseline;
  gap: var(--mw-space-2);
}
.hero__brand {
  font-size: var(--mw-font-h1);
  font-weight: 700;
  letter-spacing: -0.02em;
}
.hero__ver {
  color: var(--mw-brand);
  font-size: var(--mw-font-caption);
  font-weight: 600;
}
.hero__desc {
  margin-top: var(--mw-space-2);
  max-width: 62ch;
  color: var(--mw-on-night-2);
  font-size: var(--mw-font-caption);
  line-height: 1.7;
}

/* ---- 内容卡 ---- */
.card {
  margin-bottom: var(--mw-space-4);
}
.card__p {
  margin-top: var(--mw-space-2);
  max-width: 78ch;
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
}
/* 说明性文字（讲来源、讲现状），比正文更弱一档 */
.card__note {
  margin-top: var(--mw-space-3);
  padding-top: var(--mw-space-3);
  border-top: 1px solid var(--mw-line);
  max-width: 78ch;
  color: var(--mw-muted);
  font-size: var(--mw-font-micro);
  line-height: 1.7;
}
/* 长 URL：允许断行，别撑破卡片 */
.mono {
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}

/* ---- 键值行 ---- */
.kv {
  display: flex;
  align-items: flex-start;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-2);
}
.kv__k {
  flex: none;
  width: 64px;
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
}
.kv__v {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  font-size: var(--mw-font-caption);
  overflow-wrap: anywhere;
}
.kv__v--wrap {
  font-weight: 400;
}

/* ---- 依赖条目 ---- */
.deps {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-3);
}
.dep {
  padding: var(--mw-space-3) var(--mw-space-4);
  background: var(--mw-canvas);
  border-radius: var(--mw-radius-input);
}
.dep__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--mw-space-2);
}
.dep__name {
  min-width: 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.dep__ver {
  flex: none;
  color: var(--mw-brand-ink);
  font-size: var(--mw-font-caption);
  font-weight: 600;
}
.dep__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-1);
}
.dep__where,
.dep__declared {
  color: var(--mw-muted);
  font-size: var(--mw-font-micro);
}
.dep__use {
  margin-top: var(--mw-space-1);
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
}
.dep__src {
  margin-top: var(--mw-space-1);
  color: var(--mw-muted);
  font-size: var(--mw-font-micro);
  word-break: break-all;
}

.foot {
  margin-top: var(--mw-space-6);
  text-align: center;
  color: var(--mw-muted);
  font-size: var(--mw-font-micro);
}
</style>