<script setup lang="ts">
/**
 * 症状选择器（新建/编辑症状日记用）
 *
 * 为什么要有它：契约实测「**传 `symptomId` 会覆盖 `symptomName`**」——
 * 知识库标题胜出，POST/PUT 都成立。所以界面必须把两条路分清：
 * - **从列表选**（写 `symptomId`）：名字以知识库为准，手写的名字会被后端覆盖，提交时干脆不发名字
 * - **自由填写**（不写 `symptomId`，只写名字）：用于列表里没有的症状
 * 两条路混着填是"看起来生效、其实被覆盖"的经典坑，这里直接做成互斥的两个模式。
 *
 * 选项来自 `GET /health/symptom/options`（实测猫 52 条 / 狗 46 条，字段含知识库正文与建议）。
 * ⚠️ 这个接口**经网关也要登录**（技能表里写"可免登录"，实测不成立）。
 */
import { computed, onMounted, ref } from 'vue'
import { PhCheck, PhMagnifyingGlass } from '@phosphor-icons/vue'
import {
  healthErrorMessage,
  listSymptomOptions,
  speciesText,
  type SymptomOption,
} from '@/api/health'

/** 选中的知识库条目 id（null = 自由填写） */
const symptomId = defineModel<number | null>('symptomId', { required: true })
/** 症状名（选了条目时只是显示用，提交时不会发给后端） */
const symptomName = defineModel<string>('symptomName', { required: true })

const props = defineProps<{
  /** 1=猫 2=狗；其它物种不传，后端回全部 */
  species?: number
}>()

/** 初始模式由传入的 symptomId 决定：带 id 的就是"从列表选" */
const mode = ref<'pick' | 'free'>(symptomId.value ? 'pick' : 'free')

const options = ref<SymptomOption[]>([])
const loading = ref(true)
const error = ref('')
const keyword = ref('')

const filtered = computed(() => {
  const k = keyword.value.trim()
  if (!k) return options.value
  return options.value.filter(
    (o) => o.name.includes(k) || (o.description ?? '').includes(k) || (o.remark ?? '').includes(k),
  )
})

const selected = computed(() => options.value.find((o) => o.symptomId === symptomId.value) ?? null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await listSymptomOptions(props.species)
    options.value = Array.isArray(list) ? list : []
  } catch (e) {
    error.value = healthErrorMessage(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function pick(o: SymptomOption) {
  symptomId.value = o.symptomId
  // 名字同步成知识库标题，界面才能立刻显示正确名字（提交时仍不发这个字段）
  symptomName.value = o.name
}

function usePickMode() {
  mode.value = 'pick'
  symptomId.value = null
  symptomName.value = ''
}

function useFreeMode() {
  mode.value = 'free'
  symptomId.value = null
  symptomName.value = ''
}
</script>

<template>
  <div class="picker">
    <div class="mw-chips" role="group" aria-label="症状的填写方式">
      <button
        class="mw-chip"
        :class="{ 'mw-chip--on': mode === 'pick' }"
        type="button"
        @click="usePickMode"
      >
        从列表选
      </button>
      <button
        class="mw-chip"
        :class="{ 'mw-chip--on': mode === 'free' }"
        type="button"
        @click="useFreeMode"
      >
        自由填写
      </button>
    </div>

    <template v-if="mode === 'pick'">
      <p class="mw-caption tip">
        选列表里的症状，名字以知识库为准（后端会覆盖手写的名字），这样同一个症状不会出现好几种写法。
      </p>

      <label class="mw-field search">
        <span>搜索症状</span>
        <span class="search__box">
          <PhMagnifyingGlass :size="16" aria-hidden="true" />
          <input
            v-model="keyword"
            class="mw-input search__input"
            type="search"
            placeholder="比如：呕吐、食欲"
          />
        </span>
      </label>

      <p v-if="loading" class="mw-caption">正在读取症状列表</p>
      <p v-else-if="error" class="mw-alert mw-alert--danger">
        <span>{{ error }}</span>
        <button class="mw-link" type="button" @click="load">重试</button>
      </p>

      <template v-else>
        <p class="mw-caption count">
          共 <span class="mw-num">{{ filtered.length }}</span> 条
          <template v-if="species">，{{ speciesText(species) }}的症状</template>
        </p>
        <ul v-if="filtered.length" class="options">
          <li v-for="o in filtered" :key="o.symptomId">
            <button
              class="option"
              :class="{ 'option--on': o.symptomId === symptomId }"
              type="button"
              @click="pick(o)"
            >
              <span class="option__name">{{ o.name }}</span>
              <PhCheck v-if="o.symptomId === symptomId" :size="16" aria-hidden="true" />
            </button>
          </li>
        </ul>
        <p v-else class="mw-caption">没有匹配的症状，可以切到"自由填写"。</p>

        <div v-if="selected" class="detail">
          <p class="detail__name">{{ selected.name }}</p>
          <p v-if="selected.description" class="mw-caption">可能的原因：{{ selected.description }}</p>
          <p v-if="selected.remark" class="mw-caption">建议：{{ selected.remark }}</p>
        </div>
      </template>
    </template>

    <template v-else>
      <label class="mw-field">
        <span>症状名</span>
        <input
          v-model="symptomName"
          class="mw-input"
          type="text"
          maxlength="30"
          placeholder="比如：偶尔打喷嚏"
        />
      </label>
      <p class="mw-caption tip">
        自由填写的症状不进知识库，不同写法会被当成不同症状，AI 解读与统计也就认不出是同一件事。
      </p>
    </template>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.tip {
  max-width: 620px;
}
.search__box {
  display: flex;
  align-items: center;
  gap: var(--mw-space-2);
  padding-left: var(--mw-space-3);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-input);
  background: var(--mw-surface);
}
.search__box:focus-within {
  border-color: var(--mw-brand-ink);
  box-shadow: 0 0 0 3px rgba(255, 138, 91, 0.18);
}
.search__input {
  border: 0;
  box-shadow: none;
}
.search__input:focus {
  box-shadow: none;
}
.count {
  margin-top: calc(-1 * var(--mw-space-1));
}
.options {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-1);
  max-height: 260px;
  overflow-y: auto;
  padding: var(--mw-space-2);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-input);
  background: var(--mw-canvas);
}
.option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mw-space-2);
  width: 100%;
  min-height: 36px;
  padding: 0 var(--mw-space-3);
  border: 1px solid transparent;
  border-radius: var(--mw-radius-tag);
  background: var(--mw-surface);
  color: var(--mw-ink);
  font-family: inherit;
  font-size: var(--mw-font-caption);
  text-align: left;
  cursor: pointer;
}
.option:hover {
  border-color: var(--mw-line);
}
.option--on {
  border-color: var(--mw-brand-ink);
  color: var(--mw-brand-ink);
  font-weight: 600;
}
.option__name {
  min-width: 0;
}
.detail {
  padding: var(--mw-space-3) var(--mw-space-4);
  border: 1px solid rgba(194, 65, 12, 0.2);
  border-radius: var(--mw-radius-input);
  background: rgba(194, 65, 12, 0.06);
}
.detail__name {
  font-weight: 600;
  color: var(--mw-brand-ink);
}
</style>