<script setup lang="ts">
/**
 * 最近 7 天照护节奏（柱图）
 *
 * 刻意做成**共享基线的柱状图**，而不是"进度条 + 背景槽"：
 * 后者是典型的模板味组件，前者才是真的在看趋势。
 */
import { computed } from 'vue'
import type { RhythmDay } from '@/api/home'
import { rhythmRate } from '@/api/home'

const props = defineProps<{
  days: RhythmDay[]
}>()

const rate = computed(() => rhythmRate(props.days))

/** 柱高按当天完成比例，最高柱占满 72px */
const bars = computed(() =>
  props.days.map((d) => ({
    ...d,
    ratio: d.total ? d.done / d.total : 0,
  })),
)

const summary = computed(() =>
  props.days.map((d) => `${d.label}${d.isToday ? '（今天）' : ''} ${d.done}/${d.total}`).join('，'),
)
</script>

<template>
  <figure class="rhythm">
    <figcaption class="rhythm__head">
      <span class="rhythm__label">最近 7 天</span>
      <span class="rhythm__rate"
        ><span class="mw-num">{{ rate }}</span
        >% 按时完成</span
      >
    </figcaption>

    <!-- role=img + aria-label：柱图对读屏用户也要说得清 -->
    <div class="rhythm__chart" role="img" :aria-label="`最近七天照护完成情况：${summary}`">
      <div v-for="b in bars" :key="b.label" class="col" :class="{ 'col--today': b.isToday }">
        <div class="col__track">
          <div class="col__bar" :style="{ height: `${Math.max(b.ratio * 100, 6)}%` }" />
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
