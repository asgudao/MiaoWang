<script setup lang="ts">
/**
 * 萌宠列表（Web）
 *
 * 已接真接口。原来这张页面是演示数据（体重 4.2kg、疫苗倒计时等），
 * 那些字段属于 healthy 模块、pet 接口里没有 —— **不编造**，所以卡片只展示真实有的字段：
 * 名字、大类与品种、按出生日期算的年龄、性别、绝育状态、它怎么称呼你。
 * （体重与疫苗的真实数据在 `/pets/:pid/health` 里，卡片上不放"看起来像真数据"的假倒计时。）
 */
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { storeToRefs } from 'pinia'
import { PhArrowClockwise, PhCaretRight, PhPlus } from '@phosphor-icons/vue'
import { usePetsStore } from '@/stores/pets'
import {
  ageLabel,
  breedLabel,
  genderLabel,
  initials,
  needsName,
  petDisplayName,
  sterilizedLabel,
} from '@/utils/pet'

const pets = usePetsStore()
const { list, loading, loaded, error } = storeToRefs(pets)

onMounted(() => {
  void pets.fetchMine()
})

const isEmpty = computed(() => loaded.value && list.value.length === 0)
const showSkeleton = computed(() => loading.value && !loaded.value)
</script>

<template>
  <div class="mw-container mw-page">
    <header class="head">
      <div>
        <h1 class="mw-h1">萌宠</h1>
        <p class="mw-caption">档案、体重曲线、疫苗与驱虫记录</p>
      </div>
      <RouterLink class="mw-btn mw-btn--primary" :to="{ name: 'pet-new' }">
        <PhPlus :size="16" weight="bold" aria-hidden="true" />
        添加宠物
      </RouterLink>
    </header>

    <!-- 首次加载：骨架屏，避免空页面闪一下 -->
    <ul v-if="showSkeleton" class="grid">
      <li v-for="i in 2" :key="i" class="mw-card card">
        <div class="card__head">
          <span class="mw-skeleton sk sk--avatar" />
          <div class="sk-lines">
            <span class="mw-skeleton sk sk--line" />
            <span class="mw-skeleton sk sk--line sk--short" />
          </div>
        </div>
        <div class="sk-lines">
          <span class="mw-skeleton sk sk--line" />
        </div>
      </li>
    </ul>

    <div v-else-if="error" class="mw-alert mw-alert--danger">
      <p>{{ error }}</p>
      <button class="mw-btn mw-btn--ghost" type="button" @click="pets.fetchMine(true)">
        <PhArrowClockwise :size="16" aria-hidden="true" />
        重试
      </button>
    </div>

    <div v-else-if="isEmpty" class="mw-state empty">
      <p class="mw-state__title">还没有宠物档案</p>
      <p>添加之后，体重曲线和疫苗提醒都会挂在它名下。</p>
      <RouterLink class="mw-btn mw-btn--primary" :to="{ name: 'pet-new' }">添加第一只</RouterLink>
    </div>

    <ul v-else class="grid">
      <li v-for="p in list" :key="p.pid" class="mw-card card">
        <RouterLink class="card__link" :to="{ name: 'pet-detail', params: { pid: p.pid } }">
          <div class="card__head">
            <span class="avatar" aria-hidden="true">{{ initials(p) }}</span>
            <div class="card__who">
              <p class="card__name">{{ petDisplayName(p) }}</p>
              <p class="card__meta">{{ breedLabel(p) }}</p>
            </div>
            <PhCaretRight class="card__arrow" :size="16" aria-hidden="true" />
          </div>

          <dl class="card__stats">
            <div>
              <dt>年龄</dt>
              <dd>{{ ageLabel(p.birth) }}</dd>
            </div>
            <div>
              <dt>性别</dt>
              <dd>{{ genderLabel(p.gender) }}</dd>
            </div>
            <div>
              <dt>绝育</dt>
              <dd>{{ sterilizedLabel(p.sterilizedState) }}</dd>
            </div>
          </dl>

          <p v-if="p.ownerCallName" class="card__call">它叫你「{{ p.ownerCallName }}」</p>
          <p v-if="needsName(p)" class="card__warn">档案里还没名字，点进去补一个</p>
        </RouterLink>
      </li>

      <li class="card card--add">
        <p class="add__title">再添一只</p>
        <p class="add__desc">猫狗和异宠都支持，档案分别管理</p>
        <RouterLink class="mw-btn mw-btn--ghost" :to="{ name: 'pet-new' }">添加</RouterLink>
      </li>
    </ul>

    <p v-if="!showSkeleton && !error && list.length" class="mw-caption tail">
      体重、疫苗与驱虫这些记录都在每只宠物的健康模块里，点进档案就能打开。
    </p>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-6);
}
.head .mw-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  text-decoration: none;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--mw-space-4);
}

.card {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  padding: var(--mw-space-6);
}
.card__link {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  color: inherit;
  text-decoration: none;
}

.card__head {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
}
.card__who {
  min-width: 0;
  flex: 1;
}
.avatar {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(194, 65, 12, 0.1);
  color: var(--mw-brand-ink);
  font-size: 20px;
  font-weight: 700;
}
.card__name {
  font-size: 18px;
  font-weight: 700;
}
.card__meta {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.card__arrow {
  flex: none;
  color: var(--mw-muted);
}

.card__stats {
  display: flex;
  gap: var(--mw-space-8);
  margin: 0;
}
.card__stats dt {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.card__stats dd {
  margin: 2px 0 0;
  font-size: 15px;
  font-weight: 600;
}

.card__call {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.card__warn {
  font-size: var(--mw-font-micro);
  color: var(--mw-accent-ink);
}

.card--add {
  justify-content: center;
  align-items: center;
  text-align: center;
  border-style: dashed;
  background: transparent;
}
.add__title {
  font-weight: 600;
}
.add__desc {
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
}
.card--add .mw-btn {
  text-decoration: none;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mw-space-2);
}
.empty .mw-btn {
  margin-top: var(--mw-space-2);
  text-decoration: none;
}

.tail {
  margin-top: var(--mw-space-6);
}

/* 骨架屏 */
.sk {
  display: block;
  border-radius: var(--mw-radius-input);
}
.sk--avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--mw-radius-pill);
}
.sk--line {
  height: 12px;
  width: 100%;
}
.sk--short {
  width: 60%;
}
.sk-lines {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-2);
  flex: 1;
}
</style>
