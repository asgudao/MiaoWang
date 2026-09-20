<script setup lang="ts">
/**
 * 健康档案附件上传（就诊的 `attachments` / 症状的 `images` 共用）
 *
 * 契约要点：
 * - `POST /health/file`，`multipart/form-data`，字段名固定 **`file`**，**一次一个文件**
 * - 返回 `{url, objectKey}`，但记录层只存 **url**（后端记录表没有 objectKey 列），
 *   所以这里只把 `url` 追加进列表，objectKey 丢弃
 * - 记录层的入参是**逗号串**（提交时由 `medicalBody()` / `symptomBody()` 里的 `toCommaString()` 转换），
 *   组件这一层始终用数组，别在界面里做字符串拼接
 * - 后端限制（common 的 `MinioProperties` 代码默认值）：后缀 `jpg/jpeg/png/webp`、单文件 ≤50MB
 *   → 超限/类型不符时后端会回 `A000601` / `A000602`，message 里带真实上限，直接展示
 */
import { ref } from 'vue'
import { PhUploadSimple, PhX } from '@phosphor-icons/vue'
import { healthErrorMessage, uploadAttachment } from '@/api/health'

/** 已上传的图片 URL 数组（v-model） */
const list = defineModel<string[]>({ required: true })

defineProps<{ hint?: string }>()

const busy = ref(false)
const progress = ref('')
const error = ref('')

async function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  // 清空 value，否则连着选同一张图不会再触发 change
  input.value = ''
  if (!files.length) return

  busy.value = true
  error.value = ''
  try {
    const added: string[] = []
    for (const [i, file] of files.entries()) {
      progress.value = `正在上传 ${i + 1}/${files.length}`
      const res = await uploadAttachment(file)
      if (res && res.url) added.push(res.url)
    }
    if (added.length) list.value = [...list.value, ...added]
  } catch (err) {
    // 部分成功：已成功的保留，失败的那张给出原因
    error.value = healthErrorMessage(err)
  } finally {
    busy.value = false
    progress.value = ''
  }
}

function remove(url: string) {
  list.value = list.value.filter((u) => u !== url)
}
</script>

<template>
  <div class="uploader">
    <div class="uploader__actions">
      <label class="mw-btn mw-btn--ghost pick">
        <PhUploadSimple :size="16" aria-hidden="true" />
        {{ busy ? progress || '上传中' : '添加照片' }}
        <input
          class="pick__input"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          multiple
          :disabled="busy"
          @change="onPick"
        />
      </label>
      <span class="mw-caption">
        {{ hint || '支持 jpg / jpeg / png / webp，单张不超过 50MB；上传后只保存图片地址。' }}
      </span>
    </div>

    <p v-if="error" class="mw-alert mw-alert--danger err">{{ error }}</p>

    <ul v-if="list.length" class="thumbs">
      <li v-for="url in list" :key="url" class="thumb">
        <a :href="url" target="_blank" rel="noopener noreferrer">
          <img :src="url" alt="已上传的图片" loading="lazy" />
        </a>
        <button class="thumb__x" type="button" :disabled="busy" @click="remove(url)">
          <PhX :size="12" aria-hidden="true" />
          <span class="sr">移除这张</span>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.uploader__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--mw-space-3);
}
.pick {
  position: relative;
  min-height: 36px;
  padding: 0 var(--mw-space-4);
  font-size: var(--mw-font-caption);
  overflow: hidden;
}
.pick__input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
.err {
  margin-top: var(--mw-space-2);
}
.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mw-space-2);
  margin-top: var(--mw-space-3);
}
.thumb {
  position: relative;
  width: 72px;
  height: 72px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-tag);
  overflow: hidden;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb__x {
  position: absolute;
  top: 2px;
  right: 2px;
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: var(--mw-radius-pill);
  background: rgba(29, 21, 18, 0.72);
  color: var(--mw-on-night);
  cursor: pointer;
}
.sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>