<script setup lang="ts">
/**
 * 宠物档案详情（Web）
 *
 * 数据来源是列表 store（列表已经拉过就不再请求）；刷新只做一次，成功后不再自动重拉。
 * 健康记录已经有真实入口（`/pets/:pid/health`），这里只给入口与一句话说明，不在本页重复拉健康数据
 * （避免进一次档案页就打两遍健康接口）。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { PhArrowLeft, PhHeartbeat, PhPencilSimple, PhTrash } from '@phosphor-icons/vue'
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

const route = useRoute()
const router = useRouter()
const pets = usePetsStore()
const { loaded } = storeToRefs(pets)

const pid = computed(() => Number(route.params.pid))
const pet = computed(() => pets.byId(pid.value))

const loading = ref(true)
const confirming = ref(false)
const busy = ref(false)
const error = ref('')

onMounted(async () => {
  if (!loaded.value) await pets.fetchMine()
  loading.value = false
})

async function onDelete() {
  if (busy.value) return
  if (!confirming.value) {
    confirming.value = true
    return
  }
  busy.value = true
  error.value = ''
  try {
    await pets.remove(pid.value)
    void router.replace({ name: 'pets' })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '删除失败，请稍后再试'
  } finally {
    busy.value = false
    confirming.value = false
  }
}
</script>

<template>
  <div class="mw-container mw-page">
    <RouterLink class="back" :to="{ name: 'pets' }">
      <PhArrowLeft :size="16" aria-hidden="true" />
      萌宠
    </RouterLink>

    <div v-if="loading" class="mw-state">
      <p>正在读取档案</p>
    </div>

    <div v-else-if="!pet" class="mw-state">
      <p class="mw-state__title">找不到这只宠物</p>
      <p>它可能已经被删除了，或者链接里的编号不对。</p>
      <RouterLink class="mw-btn mw-btn--primary" :to="{ name: 'pets' }">回到萌宠</RouterLink>
    </div>

    <template v-else>
      <section class="mw-night hero">
        <span class="hero__avatar" aria-hidden="true">{{ initials(pet) }}</span>
        <div class="hero__main">
          <p class="hero__name">{{ petDisplayName(pet) }}</p>
          <p class="hero__meta">{{ breedLabel(pet) }} {{ ageLabel(pet.birth) }}</p>
          <p v-if="pet.ownerCallName" class="hero__call">它叫你「{{ pet.ownerCallName }}」</p>
          <p v-else class="hero__call hero__call--dim">还没告诉它该怎么称呼你</p>
        </div>
        <RouterLink
          class="mw-btn mw-btn--brand"
          :to="{ name: 'pet-edit', params: { pid: pet.pid } }"
        >
          <PhPencilSimple :size="16" aria-hidden="true" />
          编辑
        </RouterLink>
      </section>

      <p v-if="needsName(pet)" class="mw-alert mw-alert--info fix">
        <span
          >这只宠物的名字是空的（后端目前不校验名字，历史数据里会出现空名）。点「编辑」补一个吧。</span
        >
      </p>

      <section class="mw-card panel">
        <h2 class="mw-h2">基本信息</h2>
        <dl class="rows">
          <div>
            <dt>大类</dt>
            <dd>{{ pet.species }}</dd>
          </div>
          <div>
            <dt>具体品种</dt>
            <dd>{{ pet.specificSpecies || '未填' }}</dd>
          </div>
          <div>
            <dt>生日</dt>
            <dd class="mw-num">{{ pet.birth || '未填' }}</dd>
          </div>
          <div>
            <dt>性别</dt>
            <dd>{{ genderLabel(pet.gender) }}</dd>
          </div>
          <div>
            <dt>绝育</dt>
            <dd>{{ sterilizedLabel(pet.sterilizedState) }}</dd>
          </div>
          <div>
            <dt>档案编号</dt>
            <dd class="mw-num">#{{ pet.pid }}</dd>
          </div>
        </dl>
      </section>

      <section class="mw-card panel">
        <h2 class="mw-h2">健康记录</h2>
        <p class="mw-caption health__desc">
          体重曲线、疫苗与驱虫提醒、就诊、用药、过敏史、症状日记都在健康模块里。概览页会算出"该做什么"（含逾期提醒）与"最近发生了什么"，记录本身在各自的分类页里增删改。
        </p>
        <div class="health__actions">
          <RouterLink
            class="mw-btn mw-btn--primary"
            :to="{ name: 'health-overview', params: { pid: pet.pid } }"
          >
            <PhHeartbeat :size="16" aria-hidden="true" />
            打开健康记录
          </RouterLink>
          <RouterLink
            class="mw-btn mw-btn--ghost"
            :to="{ name: 'health-weight', params: { pid: pet.pid } }"
          >
            体重曲线
          </RouterLink>
          <RouterLink
            class="mw-btn mw-btn--ghost"
            :to="{ name: 'health-preventive', params: { pid: pet.pid } }"
          >
            疫苗与驱虫
          </RouterLink>
          <RouterLink
            class="mw-btn mw-btn--ghost"
            :to="{ name: 'health-record-new', params: { pid: pet.pid, kind: 'weight' } }"
          >
            记一条体重
          </RouterLink>
        </div>
      </section>

      <div v-if="error" class="mw-alert mw-alert--danger">
        <p>{{ error }}</p>
      </div>

      <section class="zone">
        <div class="zone__actions">
          <button
            class="mw-btn mw-btn--ghost danger"
            type="button"
            :disabled="busy"
            @click="onDelete"
          >
            <PhTrash :size="16" aria-hidden="true" />
            {{ busy ? '删除中' : confirming ? '确认删除' : '删除档案' }}
          </button>
          <button
            v-if="confirming"
            class="mw-btn mw-btn--ghost"
            type="button"
            @click="confirming = false"
          >
            先不删
          </button>
        </div>
        <p class="mw-caption">
          {{
            confirming
              ? '删除后它会从列表里消失。后端做的是标记删除，原始数据仍留在库里。'
              : '删除会同时失去它的健康记录入口。'
          }}
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-4);
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
  text-decoration: none;
}
.back:hover {
  color: var(--mw-ink);
}

.hero {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  padding: var(--mw-space-6);
  border-radius: var(--mw-radius-card);
}
.hero__avatar {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(255, 138, 91, 0.16);
  color: var(--mw-brand);
  font-size: 26px;
  font-weight: 700;
}
.hero__main {
  flex: 1;
  min-width: 0;
}
.hero__name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.hero__meta {
  margin-top: 2px;
  font-size: var(--mw-font-caption);
  color: var(--mw-on-night-2);
}
.hero__call {
  margin-top: var(--mw-space-2);
  font-size: var(--mw-font-caption);
  color: var(--mw-brand);
}
.hero__call--dim {
  color: var(--mw-on-night-2);
}
.hero .mw-btn {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  text-decoration: none;
}

.fix {
  margin-top: var(--mw-space-4);
}

.panel {
  margin-top: var(--mw-space-4);
  padding: var(--mw-space-6);
}
.panel .mw-h2 {
  margin-bottom: var(--mw-space-3);
}
.rows {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--mw-space-3) var(--mw-space-6);
  margin: 0;
}
.rows dt {
  font-size: var(--mw-font-micro);
  color: var(--mw-ink-2);
}
.rows dd {
  margin: 2px 0 0;
  font-size: 15px;
  font-weight: 600;
}

.health__desc {
  margin-bottom: var(--mw-space-4);
  max-width: 680px;
}
.health__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
}
.health__actions .mw-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
  text-decoration: none;
}

.zone {
  margin-top: var(--mw-space-6);
}
.zone__actions {
  display: flex;
  gap: var(--mw-space-2);
}
.zone__actions .mw-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--mw-space-2);
}
.danger {
  color: var(--mw-danger);
  border-color: rgba(229, 72, 77, 0.3);
}
</style>
