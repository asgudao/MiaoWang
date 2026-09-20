<script setup lang="ts">
/**
 * 六类记录的类型图标（概览的"最近时间线"与时间线页共用）。
 *
 * 图标政策：Web 端统一用官方图标库 `@phosphor-icons/vue`，不手绘 SVG 图标路径。
 * 未知类型兜底成"记录"图标，不编新类型。
 */
import { computed } from 'vue'
import {
  PhClipboardText,
  PhPill,
  PhScales,
  PhStethoscope,
  PhSyringe,
  PhWarningDiamond,
} from '@phosphor-icons/vue'

const props = defineProps<{ type: string | null | undefined; size?: number }>()

const MAP: Record<string, unknown> = {
  preventive: PhSyringe,
  medical: PhStethoscope,
  medication: PhPill,
  allergy: PhWarningDiamond,
  symptom: PhClipboardText,
  weight: PhScales,
}

const icon = computed(() => MAP[String(props.type ?? '')] ?? PhClipboardText)
</script>

<template>
  <component :is="icon" :size="size ?? 18" aria-hidden="true" />
</template>