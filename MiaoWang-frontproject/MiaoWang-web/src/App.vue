<script setup lang="ts">
/**
 * 应用外壳。
 *
 * `RemindPopup` 挂在**全局**：照护提醒的软件内弹窗是"进入站点 / 回到前台"
 * （`document.visibilitychange`）触发的，必须在任何一个页面都能弹出来 ——
 * 挂在首页里的话，用户直接进 `/mine` 就永远不会被提醒（见 components/RemindPopup.vue）。
 */
import AppNav from '@/components/AppNav.vue'
import RemindPopup from '@/components/RemindPopup.vue'
</script>

<template>
  <AppNav />
  <main>
    <RouterView />
  </main>
  <RemindPopup />
</template>
