<script setup lang="ts">
/**
 * 记录行内的"删除 + 行内二次确认"按钮（六类记录列表共用）。
 *
 * 为什么不用原生 `confirm`：设计规范明确禁止；而且原生弹窗在移动端浏览器里长得像钓鱼框。
 * 这里的做法是按钮就地变成「确认删除 / 先不删」两个按钮，与宠物档案页的删除一致。
 *
 * 删除在后端是**逻辑删除**（`del_flag=1`），不是物理删除 —— 界面上不要许诺"永久删除"。
 */
import { ref } from 'vue'
import { PhTrash } from '@phosphor-icons/vue'

defineProps<{
  /** 父页面正在删（禁用按钮防连点） */
  busy?: boolean
  /** 未确认时的按钮文案 */
  label?: string
  /** 确认态的主按钮文案 */
  confirmText?: string
}>()

const emit = defineEmits<{ confirm: [] }>()

const confirming = ref(false)

function ask() {
  confirming.value = true
}

function cancel() {
  confirming.value = false
}

/** 父页面删除失败时可以让按钮回到初始态 */
function reset() {
  confirming.value = false
}

defineExpose({ confirming, reset })
</script>

<template>
  <button v-if="!confirming" class="mw-btn mw-btn--ghost act" type="button" :disabled="busy" @click="ask">
    <PhTrash :size="16" aria-hidden="true" />
    {{ label || '删除' }}
  </button>
  <template v-else>
    <button
      class="mw-btn mw-btn--ghost act act--danger"
      type="button"
      :disabled="busy"
      @click="emit('confirm')"
    >
      {{ busy ? '删除中' : confirmText || '确认删除' }}
    </button>
    <button class="mw-btn mw-btn--ghost act" type="button" :disabled="busy" @click="cancel">
      先不删
    </button>
  </template>
</template>

<style scoped>
.act {
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
}
.act--danger {
  color: var(--mw-danger);
  border-color: rgba(229, 72, 77, 0.3);
}
</style>