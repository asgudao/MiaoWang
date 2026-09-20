<script setup lang="ts">
/**
 * 最近 7 天**记录条数**（柱图）
 *
 * ⚠️ 口径改过一次，别再写回"完成率"：后端**没有打卡历史**（healthy README 明说
 * "完成打卡历史归 reminder 模块"），能数的只有 `timeline` 里**每天写了多少条记录**。
 * 所以标题是「最近 7 天记录」、数字是**条数**，不叫完成率、不出现 `done/total`。
 *
 * 版式仍是**共享基线的柱状图**（不是"进度条 + 背景槽"）：柱高 = 当天条数 / 本周最高的一天。
 * 0 条的那天不画柱（留出基线），**不用最小高度假装有记录**。
 */
import { computed } from 'vue'
import type { RhythmDay } from '@/api/home'
import { rhythmTotal } from '@/api/home'

const props = defineProps<{
  days: RhythmDay[]
}>()

/** 本周记录条数合计（标题右侧的数字） */
const total = computed(() => rhythmTotal(props.days))

/** 柱高按"当天条数 / 本周最多的一天"，最高柱占满 72px；全为 0 时所有柱高 0 */
const bars = computed(() => {
  const max = props.days.reduce((n, d) => Math.max(n, Number(d.count || 0)), 0)
  return props.days.map((d) => {
    const count = Number(d.count || 0)
    return { ...d, count, ratio: max > 0 ? count / max : 0 }
  })
})

const summary = computed(() =>
  props.days
    .map((d) => `${d.label}${d.isToday ? '（今天）' : ''} ${Number(d.count || 0)} 条`)
    .join('，'),
)
</script>

<template>
  <figure class="rhythm">
    <figcaption class="rhythm__head">
      <span class="rhythm__label">最近 7 天记录</span>
      <span class="rhythm__rate"
        ><span class="mw-num">{{ total }}</span
        > 条</span
      >
    </figcaption>

    <!-- role=img + aria-label：柱图对读屏用户也要说得清 -->
    <div class="rhythm__chart" role="img" :aria-label="`最近七天记录条数：${summary}`">
      <div v-for="b in bars" :key="b.label" class="col" :class="{ 'col--today': b.isToday }">
        <div class="col__track">
          <div
            class="col__bar"
            :style="{ height: b.count > 0 ? `${Math.max(b.ratio * 100, 6)}%` : '0%' }"
          />
        </div>
        <span class="col__label">{{ b.label }}</span>
      </div>
    </div>
  </figure>
</template>

<style scoped>
.rhythm {
  margin: 0;
}

.rhythm__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--mw-space-4);
}

.rhythm__label {
  font-size: var(--mw-font-caption);
  font-weight: 600;
  color: var(--mw-ink);
}

.rhythm__rate {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}

.rhythm__chart {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: var(--mw-space-2);
  align-items: end;
}

.col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mw-space-2);
}

/* 共享基线：所有柱子从同一条线长起来 */
.col__track {
  display: flex;
  align-items: flex-end;
  width: 100%;
  height: 72px;
}

.col__bar {
  width: 100%;
  border-radius: 6px 6px 2px 2px;
  background: var(--mw-brand-ink);
  opacity: 0.34;
}

.col--today .col__bar {
  opacity: 1;
}

.col__label {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.col--today .col__label {
  color: var(--mw-brand-ink);
  font-weight: 600;
}
</style>
