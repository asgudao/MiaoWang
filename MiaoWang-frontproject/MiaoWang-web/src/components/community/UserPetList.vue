<script setup lang="ts">
/**
 * 他的宠物（`GET /community/user/pet` 的裸数组）
 *
 * 这个接口**一次性返回全部宠物**（没有游标信封），所以这里没有"加载更多"。
 * 展示口径直接复用 `utils/pet.ts`（与"萌宠""宠物详情"页同一套）：
 * 空名兜底「未命名宠物」、年龄按 `birth` 现算、品种/性别/绝育各自成词。
 *
 * ⚠️ 这里刻意**不**去调 pet 模块的 `GET /pet/user/{uid}`（`api/pet.ts` 里那个也存在）：
 * 主页其余三个接口都在社区、鉴权口径一致，混着调会让"少带一个头"这类问题只在宠物段暴露。
 */
import { PhArrowClockwise, PhPawPrint, PhTray } from '@phosphor-icons/vue'
import type { UserPetBrief } from '@/api/community'
import {
  ageLabel,
  breedLabel,
  genderLabel,
  initials,
  petDisplayName,
  sterilizedLabel,
} from '@/utils/pet'

defineProps<{
  pets: UserPetBrief[]
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="mw-rows" aria-hidden="true">
    <span v-for="i in 2" :key="i" class="mw-skeleton sk" />
  </div>

  <div v-else-if="error" class="mw-state mw-state--box">
    <PhArrowClockwise :size="24" aria-hidden="true" />
    <p class="mw-state__title">没能读到 TA 的宠物</p>
    <p>{{ error }}</p>
    <button class="mw-btn mw-btn--ghost" type="button" @click="emit('retry')">重试</button>
  </div>

  <div v-else-if="!pets.length" class="mw-state mw-state--box">
    <PhTray :size="24" aria-hidden="true" />
    <p class="mw-state__title">TA 还没有宠物档案</p>
    <p>宠物档案是自愿填的，也有人的档案设成了不公开。</p>
  </div>

  <ul v-else class="mw-rows">
    <li v-for="pet in pets" :key="pet.pid" class="mw-row">
      <span class="pet__badge" aria-hidden="true">
        <PhPawPrint v-if="!pet.pName" :size="16" />
        <template v-else>{{ initials(pet) }}</template>
      </span>
      <div class="mw-row__main">
        <p class="mw-row__title">{{ petDisplayName(pet) }}</p>
        <!-- 用逗号连成一句，不做 `A / B / C` 式标签串（设计规范禁止那种 meta 串） -->
        <p class="mw-row__sub">{{ breedLabel(pet) }}，{{ ageLabel(pet.birth) }}</p>
      </div>
      <div class="mw-row__actions">
        <span class="pet__tag">{{ genderLabel(pet.gender) }}</span>
        <span class="pet__tag">{{ sterilizedLabel(pet.sterilizedState) }}</span>
      </div>
    </li>
  </ul>
</template>

<style scoped>
.sk {
  display: block;
  height: 62px;
}
.pet__badge {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: var(--mw-radius-tag);
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
  font-weight: 700;
}
/* 中性小标签：性别与绝育状态都不是"时间/完成"，所以刻意不用琥珀与苔绿 */
.pet__tag {
  padding: 2px var(--mw-space-2);
  border-radius: var(--mw-radius-tag);
  background: var(--mw-canvas);
  color: var(--mw-ink-2);
  font-size: var(--mw-font-micro);
  font-weight: 600;
  white-space: nowrap;
}
</style>