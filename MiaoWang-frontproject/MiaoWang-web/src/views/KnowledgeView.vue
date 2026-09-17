<script setup lang="ts">
import { ref } from 'vue'

const categories = ['全部', '喂养', '健康', '行为', '新手']
const active = ref('全部')
const keyword = ref('')

interface Article {
  id: number
  title: string
  summary: string
  minutes: number
  category: string
}

const articles: Article[] = [
  {
    id: 1,
    title: '猫咪应激的 6 个信号，第 3 个最容易被忽略',
    summary: '频繁舔毛、躲进柜子、突然不吃东西……这些都不是"闹脾气"，而是它在说压力太大了。',
    minutes: 4,
    category: '行为',
  },
  {
    id: 2,
    title: '换粮要不要过渡？7 天新旧比例表',
    summary: '直接换粮是软便最常见的原因。这张比例表照着做，肠胃基本不会出问题。',
    minutes: 3,
    category: '喂养',
  },
  {
    id: 3,
    title: '狗狗一年该打哪些疫苗，一张时间表说清',
    summary: '幼犬首年、成年加强、狂犬疫苗的时间点与间隔，附常见误区和注意事项。',
    minutes: 5,
    category: '健康',
  },
  {
    id: 4,
    title: '第一次养猫，前两周只需要做对这 5 件事',
    summary: '别急着买一堆东西。先把安全区、猫砂盆、饮水和就医路线安排好。',
    minutes: 6,
    category: '新手',
  },
]
</script>

<template>
  <div class="mw-container mw-page">
    <header class="head">
      <h1 class="mw-h1">知识库</h1>
      <p class="mw-caption">经人工审校的养护内容 · 仅供参考，具体请咨询兽医</p>
    </header>

    <div class="toolbar">
      <input
        v-model="keyword"
        class="mw-input search"
        type="search"
        placeholder="搜症状、食材、疫苗…"
      />
      <div class="chips">
        <button
          v-for="c in categories"
          :key="c"
          class="chip"
          :class="{ 'chip--on': active === c }"
          type="button"
          @click="active = c"
        >
          {{ c }}
        </button>
      </div>
    </div>

    <div class="layout">
      <ul class="list">
        <li v-for="a in articles" :key="a.id" class="item">
          <div class="item__main">
            <h2 class="item__title">{{ a.title }}</h2>
            <p class="item__summary">{{ a.summary }}</p>
            <p class="item__meta">
              <span class="mw-badge mw-badge--done">{{ a.category }}</span>
              <span class="item__time"
                >约 <span class="mw-num">{{ a.minutes }}</span> 分钟</span
              >
            </p>
          </div>
        </li>
      </ul>

      <aside class="side">
        <div class="panel">
          <h3 class="panel__title">智能问答</h3>
          <p class="panel__desc">知识库已接入检索增强问答，可以直接问"我家猫吐毛球怎么办"。</p>
          <button class="mw-btn mw-btn--primary" type="button">去提问</button>
        </div>
        <p class="note">演示数据 · 知识库接口免登录可读，服务启动后切真数据</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.head {
  margin-bottom: var(--mw-space-6);
}
.head .mw-caption {
  margin-top: var(--mw-space-2);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--mw-space-4);
  margin-bottom: var(--mw-space-6);
}
.search {
  max-width: 320px;
}
.chips {
  display: flex;
  gap: var(--mw-space-2);
}
.chip {
  padding: var(--mw-space-1) var(--mw-space-4);
  min-height: 34px;
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-pill);
  background: var(--mw-surface);
  color: var(--mw-ink-2);
  font-family: inherit;
  font-size: var(--mw-font-caption);
  font-weight: 600;
  cursor: pointer;
}
.chip--on {
  background: var(--mw-brand-ink);
  border-color: var(--mw-brand-ink);
  color: #fff;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--mw-space-8);
  align-items: start;
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.item {
  padding: var(--mw-space-6);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.item__title {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.01em;
}
.item__summary {
  margin-top: var(--mw-space-2);
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
}
.item__meta {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
  margin-top: var(--mw-space-3);
}
.item__time {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

.side {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-4);
}
.panel {
  padding: var(--mw-space-6);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.panel__title {
  font-size: var(--mw-font-h2);
  font-weight: 600;
  margin-bottom: var(--mw-space-2);
}
.panel__desc {
  color: var(--mw-ink-2);
  font-size: var(--mw-font-caption);
  margin-bottom: var(--mw-space-4);
}
.note {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .search {
    max-width: none;
  }
}
</style>
