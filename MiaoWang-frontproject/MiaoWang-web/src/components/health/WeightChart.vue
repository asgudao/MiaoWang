<script setup lang="ts">
/**
 * 体重曲线（手写 SVG，**不引任何图表依赖**）
 *
 * 接口给的是"按天聚合的点"：`{date: 'yyyy-MM-dd', weightKg, count}`，`date` **升序**。
 * 手写而不用图表库的理由：只有一条折线 + 一层面积，引一个几十 KB 的依赖不划算，
 * 而且极值/单点/负变化这些边界自己写反而更可控。
 *
 * 覆盖到的边界：
 * - **0 个点**：不画空坐标系，直接说"还没有数据"
 * - **1 个点**：不画折线（两点才成线），只画一个点 + 说明"多记几次才看得出趋势"
 * - **所有点同重**（极差为 0）：Y 轴上下各撑开 0.25kg，否则折线会贴在边框上
 * - **区间很窄**（≤0.2kg）：也撑开到 0.1kg，免得把 0.02kg 的抖动画成悬崖
 * - **只测一天多笔**（`count > 1`）：X 轴无法按时间铺开，把这些点聚在中间
 */
import { computed } from 'vue'
import { healthDayText, healthMonthDayText, parseHealthTime, type WeightCurvePoint } from '@/api/health'

const props = defineProps<{
  points: WeightCurvePoint[]
  /** 空数据时的引导文案（由页面给，页面知道用户下一步能做什么） */
  emptyHint?: string
}>()

/* 画布：固定 viewBox，宽度交给 CSS 自适应（等比缩放，线宽不会被拉变形） */
const W = 720
const H = 240
const PAD = { left: 56, right: 20, top: 20, bottom: 36 }

interface Pt {
  date: string
  t: number
  kg: number
  count: number
}

/** 排序 + 解析（`date` 是 `yyyy-MM-dd`，用 parseHealthTime 手工解析，避免 iOS 上 Invalid Date） */
const pts = computed<Pt[]>(() =>
  (props.points ?? [])
    .map((p) => ({
      date: healthDayText(p.date),
      t: parseHealthTime(p.date)?.getTime() ?? Number.NaN,
      kg: Number(p.weightKg),
      count: Number(p.count ?? 1),
    }))
    .filter((p) => Number.isFinite(p.kg) && Number.isFinite(p.t))
    .sort((a, b) => a.t - b.t),
)

const hasData = computed(() => pts.value.length > 0)
const isSingle = computed(() => pts.value.length === 1)

/** Y 轴范围：同重或极窄时也要撑开，避免折线重叠/贴边 */
const yDomain = computed(() => {
  const kgs = pts.value.map((p) => p.kg)
  let min = Math.min(...kgs)
  let max = Math.max(...kgs)
  const span = max - min
  const pad = span < 0.2 ? 0.25 : span * 0.15
  min -= pad
  max += pad
  return { min, max }
})

/** X 轴范围：所有点同一天时退化成中轴线 */
const xDomain = computed(() => {
  const ts = pts.value.map((p) => p.t)
  const min = Math.min(...ts)
  const max = Math.max(...ts)
  return { min, max, flat: min === max }
})

const plotW = W - PAD.left - PAD.right
const plotH = H - PAD.top - PAD.bottom

function xOf(t: number): number {
  const { min, max, flat } = xDomain.value
  if (flat) return PAD.left + plotW / 2
  return PAD.left + ((t - min) / (max - min)) * plotW
}

function yOf(kg: number): number {
  const { min, max } = yDomain.value
  if (max === min) return PAD.top + plotH / 2
  return PAD.top + (1 - (kg - min) / (max - min)) * plotH
}

const dots = computed(() =>
  pts.value.map((p) => ({ ...p, x: xOf(p.t), y: yOf(p.kg) })),
)

const linePath = computed(() =>
  dots.value.map((d, i) => `${i === 0 ? 'M' : 'L'}${d.x.toFixed(1)},${d.y.toFixed(1)}`).join(' '),
)

/** 面积 = 折线 + 收到基线上；单点没有面积 */
const areaPath = computed(() => {
  if (dots.value.length < 2) return ''
  const base = PAD.top + plotH
  const firstDot = dots.value[0]
  const lastDot = dots.value[dots.value.length - 1]
  if (!firstDot || !lastDot) return ''
  return `${linePath.value} L${lastDot.x.toFixed(1)},${base} L${firstDot.x.toFixed(1)},${base} Z`
})

/** 三条参考线（上/中/下）与它们的 kg 标签 */
const gridLines = computed(() => {
  const { min, max } = yDomain.value
  const mid = (min + max) / 2
  return [max, mid, min].map((kg, i) => ({ kg, y: PAD.top + (i * plotH) / 2 }))
})

const first = computed(() => dots.value[0] ?? null)
const last = computed(() => dots.value[dots.value.length - 1] ?? null)

/** 首尾差（正=长胖，负=变轻，0=持平）；单点时无意义 */
const deltaLabel = computed(() => {
  if (dots.value.length < 2 || !first.value || !last.value) return ''
  const diff = last.value.kg - first.value.kg
  const abs = Math.abs(diff).toFixed(2)
  if (Math.abs(diff) < 0.005) return '这段时间体重基本没变'
  return diff > 0 ? `这段时间重了 ${abs} kg` : `这段时间轻了 ${abs} kg`
})

const ariaLabel = computed(() => {
  if (!hasData.value) return '体重曲线，还没有数据'
  return `体重曲线，从 ${first.value?.date} 到 ${last.value?.date}，共 ${dots.value.length} 个测量日`
})
</script>

<template>
  <div class="chart">
    <div v-if="!hasData" class="mw-state inline">
      <p class="mw-state__title">还没有可以画的点</p>
      <p>{{ emptyHint || '记一次体重，这里就会开始有曲线。' }}</p>
    </div>

    <template v-else>
      <svg class="svg" :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="ariaLabel">
        <!-- 参考线 + kg 刻度 -->
        <g class="grid">
          <template v-for="line in gridLines" :key="line.kg">
            <line :x1="PAD.left" :x2="W - PAD.right" :y1="line.y" :y2="line.y" />
            <text class="grid__label" :x="PAD.left - 8" :y="line.y + 4" text-anchor="end">
              {{ line.kg.toFixed(2) }}
            </text>
          </template>
        </g>

        <!-- 面积 + 折线（单点时不画，两点才成线） -->
        <path v-if="areaPath" class="area" :d="areaPath" />
        <path v-if="dots.length > 1" class="line" :d="linePath" />

        <!-- 点：测量次数 >1 的当天画大一点（一天测了多次） -->
        <g class="dots">
          <circle
            v-for="d in dots"
            :key="d.date"
            :cx="d.x"
            :cy="d.y"
            :r="d.count > 1 ? 5.5 : 4"
            :class="{ 'dot--multi': d.count > 1 }"
          />
        </g>

        <!-- 末点数值：直接标在图上，省得再去读列表 -->
        <text
          v-if="last"
          class="last-label"
          :x="Math.min(last.x, W - PAD.right - 4)"
          :y="Math.max(last.y - 12, PAD.top + 10)"
          text-anchor="end"
        >
          {{ last.kg.toFixed(2) }} kg
        </text>

        <!-- X 轴：首尾日期（单点只标一个） -->
        <text class="axis" :x="first?.x ?? 0" :y="H - 12" text-anchor="start">
          {{ healthMonthDayText(first?.date ?? '') }}
        </text>
        <text
          v-if="dots.length > 1"
          class="axis"
          :x="last?.x ?? 0"
          :y="H - 12"
          text-anchor="end"
        >
          {{ healthMonthDayText(last?.date ?? '') }}
        </text>
      </svg>

      <p class="summary mw-caption">
        <span class="mw-num">{{ dots.length }}</span> 个测量日
        <template v-if="deltaLabel">，{{ deltaLabel }}</template>
        <template v-if="isSingle">。只有一次记录，多记几次才看得出趋势。</template>
      </p>
    </template>
  </div>
</template>

<style scoped>
.chart {
  padding: var(--mw-space-4) var(--mw-space-4) var(--mw-space-2);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
  background: var(--mw-surface);
}
.svg {
  display: block;
  width: 100%;
  height: auto;
}
.grid line {
  stroke: var(--mw-line);
  stroke-width: 1;
}
.grid__label,
.axis {
  fill: var(--mw-ink-2);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.area {
  fill: rgba(255, 138, 91, 0.16);
}
.line {
  fill: none;
  stroke: var(--mw-brand-ink);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.dots circle {
  fill: var(--mw-surface);
  stroke: var(--mw-brand-ink);
  stroke-width: 2;
}
.dot--multi {
  fill: var(--mw-brand-ink);
}
.last-label {
  fill: var(--mw-ink);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.summary {
  margin-top: var(--mw-space-2);
}
.inline {
  padding: var(--mw-space-6);
  border: 1px dashed var(--mw-line);
  border-radius: var(--mw-radius-card);
}
</style>