<script setup lang="ts">
/**
 * 新建 / 编辑宠物（Web）—— 同一个表单，靠路由名区分
 *
 * - `/pets/new`        新建：POST /pet，成功直接用返回的 pid 跳详情
 * - `/pets/:pid/edit`  编辑：PUT /pet/{pid}，只提交用户能改的字段
 *
 * 契约细节（真调用验证过）：
 * - 名字**后端不校验**，空名字能建成功（库里已有这种脏数据），所以这里必须拦住
 * - 大类是**中文**白名单（猫/狗/蛇/兔/鸟/仓鼠/龟/鱼/其他），传 CAT/DOG 会 A000001
 * - 编辑时字段不传 = 不改动，所以「未填」就是不带该字段
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { PhArrowLeft } from '@phosphor-icons/vue'
import { PET_SPECIES } from '@/api/pet'
import { usePetsStore } from '@/stores/pets'

const route = useRoute()
const router = useRouter()
const pets = usePetsStore()

const isEdit = computed(() => route.name === 'pet-edit')
const pid = computed(() => Number(route.params.pid))
const current = computed(() => pets.byId(pid.value))
/** 编辑一只不在列表里的宠物（例如直接粘链接进来）时，没法预填 */
const notFound = ref(false)

const form = reactive({
  pName: '',
  species: '猫' as string,
  specificSpecies: '',
  ownerCallName: '',
  birth: '',
  gender: undefined as number | undefined,
  sterilizedState: undefined as number | undefined,
})

const nameError = ref('')
const submitError = ref('')
const saving = ref(false)

onMounted(async () => {
  if (!pets.loaded) await pets.fetchMine()
  if (!isEdit.value) return
  const p = current.value
  if (!p) {
    notFound.value = true
    return
  }
  form.pName = p.pName ?? ''
  form.species = p.species
  form.specificSpecies = p.specificSpecies ?? ''
  form.ownerCallName = p.ownerCallName ?? ''
  form.birth = p.birth ?? ''
  form.gender = p.gender ?? undefined
  form.sterilizedState = p.sterilizedState ?? undefined
})

function validate() {
  const n = form.pName.trim()
  if (!n) {
    nameError.value = '给宠物起个名字吧，不然列表里只能显示「未命名宠物」'
    return false
  }
  if (n.length > 20) {
    nameError.value = '名字最多 20 个字'
    return false
  }
  nameError.value = ''
  return true
}

async function onSubmit() {
  if (saving.value) return
  if (!validate()) return
  saving.value = true
  submitError.value = ''
  const body = {
    pName: form.pName.trim(),
    species: form.species,
    specificSpecies: form.specificSpecies.trim() || undefined,
    ownerCallName: form.ownerCallName.trim() || undefined,
    birth: form.birth || undefined,
    gender: form.gender,
    sterilizedState: form.sterilizedState,
  }
  try {
    if (isEdit.value) {
      await pets.update(pid.value, body)
      void router.replace({ name: 'pet-detail', params: { pid: pid.value } })
    } else {
      const created = await pets.create(body)
      void router.replace({ name: 'pet-detail', params: { pid: created.pid } })
    }
  } catch (e) {
    submitError.value = e instanceof Error ? e.message : '保存失败，请稍后再试'
  } finally {
    saving.value = false
  }
}

function cancel() {
  if (window.history.length > 1) router.back()
  else void router.replace({ name: 'pets' })
}
</script>

<template>
  <div class="mw-container mw-page">
    <RouterLink class="back" :to="{ name: 'pets' }">
      <PhArrowLeft :size="16" aria-hidden="true" />
      萌宠
    </RouterLink>

    <header class="head">
      <h1 class="mw-h1">{{ isEdit ? '编辑宠物' : '添加宠物' }}</h1>
      <p class="mw-caption">先建档，体重和疫苗记录之后都挂在它名下</p>
    </header>

    <div v-if="notFound" class="mw-alert mw-alert--danger">
      <p>这只宠物不在你的档案里（可能已被删除），没法编辑。</p>
      <RouterLink class="mw-btn mw-btn--ghost" :to="{ name: 'pets' }">回到萌宠</RouterLink>
    </div>

    <form v-else class="mw-card form" novalidate @submit.prevent="onSubmit">
      <div class="mw-field">
        <label for="pName">叫什么</label>
        <input
          id="pName"
          v-model="form.pName"
          class="mw-input"
          type="text"
          maxlength="20"
          placeholder="比如 喵喵"
          autocomplete="off"
        />
        <p v-if="nameError" class="mw-field__error">{{ nameError }}</p>
      </div>

      <div class="mw-field">
        <label for="species">大类</label>
        <select id="species" v-model="form.species" class="mw-input">
          <option v-for="s in PET_SPECIES" :key="s" :value="s">{{ s }}</option>
        </select>
        <p class="hint">必填，取值必须是后端白名单里的这几个</p>
      </div>

      <div class="mw-field">
        <label for="specificSpecies">具体品种</label>
        <input
          id="specificSpecies"
          v-model="form.specificSpecies"
          class="mw-input"
          type="text"
          maxlength="30"
          placeholder="比如 中华田园猫，可以不填"
          autocomplete="off"
        />
      </div>

      <div class="mw-field">
        <label for="ownerCallName">它怎么称呼你</label>
        <input
          id="ownerCallName"
          v-model="form.ownerCallName"
          class="mw-input"
          type="text"
          maxlength="20"
          placeholder="比如 铲屎的，可以不填"
          autocomplete="off"
        />
      </div>

      <div class="mw-field">
        <label for="birth">生日</label>
        <input id="birth" v-model="form.birth" class="mw-input" type="date" />
        <p class="hint">填了才能算出年龄，不填就显示「生日未填」</p>
      </div>

      <div class="mw-field">
        <span class="label">性别</span>
        <div class="mw-seg">
          <button
            type="button"
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': form.gender === undefined }"
            @click="form.gender = undefined"
          >
            未填
          </button>
          <button
            type="button"
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': form.gender === 0 }"
            @click="form.gender = 0"
          >
            雌性
          </button>
          <button
            type="button"
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': form.gender === 1 }"
            @click="form.gender = 1"
          >
            雄性
          </button>
        </div>
      </div>

      <div class="mw-field">
        <span class="label">绝育</span>
        <div class="mw-seg">
          <button
            type="button"
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': form.sterilizedState === undefined }"
            @click="form.sterilizedState = undefined"
          >
            未填
          </button>
          <button
            type="button"
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': form.sterilizedState === 0 }"
            @click="form.sterilizedState = 0"
          >
            未绝育
          </button>
          <button
            type="button"
            class="mw-seg__item"
            :class="{ 'mw-seg__item--on': form.sterilizedState === 1 }"
            @click="form.sterilizedState = 1"
          >
            已绝育
          </button>
        </div>
        <p v-if="isEdit" class="hint">「未填」表示这次不改动这一项（后端只更新传了的字段）</p>
      </div>

      <div v-if="submitError" class="mw-alert mw-alert--danger">
        <p>{{ submitError }}</p>
      </div>

      <div class="actions">
        <button class="mw-btn mw-btn--primary" type="submit" :disabled="saving">
          {{ saving ? '保存中' : isEdit ? '保存修改' : '添加宠物' }}
        </button>
        <button class="mw-btn mw-btn--ghost" type="button" @click="cancel">取消</button>
      </div>
    </form>
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

.head {
  margin-bottom: var(--mw-space-6);
}
.head .mw-caption {
  margin-top: var(--mw-space-1);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
  padding: var(--mw-space-6);
  max-width: 560px;
}

/* .mw-seg__item 是给 div 写的样式，这里换成 button 要清掉浏览器默认 */
.mw-seg__item {
  border: none;
  background: transparent;
  font: inherit;
  cursor: pointer;
}

.label {
  display: block;
  font-size: var(--mw-font-caption);
  font-weight: 600;
  color: var(--mw-ink-2);
}

.hint {
  margin-top: var(--mw-space-1);
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.actions {
  display: flex;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-2);
}
.actions .mw-btn--primary {
  flex: 1;
}
</style>
