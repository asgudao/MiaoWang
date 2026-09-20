<script setup lang="ts">
/**
 * 知识碎片卡片（**文字为主，没有图**）
 *
 * 为什么没图：`KnowledgeFragmentRespDTO` 实测只有 12 个字段，**没有任何封面/图片字段**
 * （`cover*` / `*url` / `*image*` 全部零命中）。所以卡片的信息层次靠**排版**挣：
 * 类型徽标 → 归类行 → 标题 → 正文 → 标签 → 浏览量与入口。
 *
 * 两条实测口径决定了这里的取舍：
 * 1. **正文极短**（平均 12 字、最长 43 字）→ 这实质是"碎片"不是"文章"，
 *    所以标题用正文级字号（不摆大标题），正文按行显示而不做"摘要折叠"。
 * 2. **`viewCount` 绝大多数是 0**（409 条里只有 2 条非零）→ 只在**有浏览量时才显示**，
 *    否则每张卡片都挂一句"暂无浏览"是噪音。
 */
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { PhCaretRight, PhEye } from '@phosphor-icons/vue'
import {
  contentTypeText,
  contentTypeTone,
  excerpt,
  parseTags,
  speciesText,
  titleText,
  viewCountText,
  type KnowledgeFragment,
} from '@/api/knowledge'

const props = defineProps<{
  item: KnowledgeFragment
  /** 分类名（由页面按 `code` 查好传进来；查不到就是空串，此时不渲染归类） */
  categoryName?: string
}>()

/** 内容类型 → 一个「角色」而不是一个颜色；具体色值全交给设计系统令牌 */
const tone = computed(() => contentTypeTone(props.item.contentType))
const typeLabel = computed(() => contentTypeText(props.item.contentType))
const tags = computed(() => parseTags(props.item.tags))
const views = computed(() => Number(props.item.viewCount ?? 0) || 0)
const viewsLabel = computed(() => viewCountText(props.item.viewCount))
const sp = computed(() => speciesText(props.item.species))

/**
 * 类型徽标用的类：复用 main.css 里已有的状态徽标（`.mw-badge*`），
 * **不新增全局类、不写死颜色**。`brand` 档（护理日程 = "该做事了"）用琥珀的"时间"语义。
 */
const badgeClass = computed(() => {
  switch (tone.value) {
    case 'danger':
      return 'mw-badge--danger'
    case 'accent':
      return 'mw-badge--time'
    default:
      return 'mw-badge--neutral'
  }
})

/** 「安全警示」是唯一会被漏看就会出事的类型 → 只给它加左侧色条，其余保持安静 */
const highlight = computed(() => tone.value === 'danger')
</script>

<template>
  <article class="kcard" :class="{ 'kcard--alert': highlight }">
    <RouterLink class="kcard__link" :to="{ name: 'knowledge-detail', params: { id: item.id } }">
      <p class="kcard__top">
        <span v-if="typeLabel" class="mw-badge" :class="badgeClass">{{ typeLabel }}</span>
        <span v-if="sp" class="kcard__species">{{ sp }}</span>
        <span v-if="categoryName" class="kcard__cat">{{ categoryName }}</span>
      </p>

      <h3 class="kcard__title">{{ titleText(item.title) }}</h3>

      <p v-if="excerpt(item.content, 70)" class="kcard__body">
        {{ excerpt(item.content, 70) }}
      </p>

      <p v-if="tags.length" class="kcard__tags">
        <span v-for="t in tags" :key="t" class="ktag">{{ t }}</span>
      </p>

      <p class="kcard__meta">
        <span v-if="views > 0" class="kcard__views">
          <PhEye :size="14" aria-hidden="true" />
          <span class="mw-num">{{ viewsLabel }}</span>
        </span>
        <span class="kcard__more">
          看这条
          <PhCaretRight :size="12" weight="bold" aria-hidden="true" />
        </span>
      </p>
    </RouterLink>
  </article>
</template>

<style scoped>
.kcard {
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}
.kcard:hover {
  border-color: rgba(194, 65, 12, 0.35);
  box-shadow: var(--mw-shadow-float);
}
/* 安全警示：一条左侧色条，让"可能出事"的内容在网格里一眼可辨 */
.kcard--alert {
  border-left: 3px solid var(--mw-danger);
}

.kcard__link {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  padding: var(--mw-space-4);
  color: inherit;
  height: 100%;
}
.kcard__link:hover {
  text-decoration: none;
}

.kcard__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--mw-space-2);
}
/* 类型徽标的「中性」档：只在本地 scoped 里定义，不动全局令牌 */
.kcard__top .mw-badge--neutral {
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
}
.kcard__species,
.kcard__cat {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.kcard__title {
  font-size: var(--mw-font-body);
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: -0.01em;
  color: var(--mw-ink);
}

.kcard__body {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
  line-height: 1.6;
}

.kcard__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-1);
}
.ktag {
  padding: 1px var(--mw-space-2);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-tag);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.kcard__meta {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  margin-top: auto;
  padding-top: var(--mw-space-1);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.kcard__views {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-1);
}
.kcard__more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  color: var(--mw-brand-ink);
  font-weight: 600;
}
</style>