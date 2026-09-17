<script setup lang="ts">
import { ref } from 'vue'

const sections = ['推荐', '猫咪', '狗狗', '求助']
const active = ref('推荐')

interface Post {
  id: number
  author: string
  initial: string
  time: string
  text: string
  likes: number
  comments: number
  tag?: string
}

const posts: Post[] = [
  {
    id: 1,
    author: '三点水的喵',
    initial: '喵',
    time: '12 分钟前',
    text: '第一次给它剪指甲，全程没挠我，奖励了一根猫条。分享一下我用的姿势：把它抱在腿上背对我，一次只剪一只。',
    likes: 42,
    comments: 8,
    tag: '猫咪',
  },
  {
    id: 2,
    author: '柯基屁股研究所',
    initial: '汪',
    time: '1 小时前',
    text: '换粮第三天，软便终于好了。记录一下：7 天过渡法真的有用，前三天新旧 1:3，别心急。',
    likes: 76,
    comments: 15,
    tag: '狗狗',
  },
  {
    id: 3,
    author: '夜班铲屎官',
    initial: '夜',
    time: '昨天 22:40',
    text: '猫咪半夜跑酷有解吗？试了睡前 20 分钟逗猫棒，连续三天它都能睡到早上六点。',
    likes: 128,
    comments: 31,
    tag: '求助',
  },
]
</script>

<template>
  <div class="mw-container mw-page">
    <header class="head">
      <div>
        <h1 class="mw-h1">社区</h1>
        <p class="mw-caption">养宠人的日常，都在这里</p>
      </div>
      <button class="mw-btn mw-btn--primary" type="button">发帖</button>
    </header>

    <div class="layout">
      <div>
        <div class="chips">
          <button
            v-for="s in sections"
            :key="s"
            class="chip"
            :class="{ 'chip--on': active === s }"
            type="button"
            @click="active = s"
          >
            {{ s }}
          </button>
        </div>

        <ul class="posts">
          <li v-for="p in posts" :key="p.id" class="post">
            <div class="post__head">
              <span class="avatar" aria-hidden="true">{{ p.initial }}</span>
              <div>
                <p class="post__author">{{ p.author }}</p>
                <p class="post__time">{{ p.time }}</p>
              </div>
              <span v-if="p.tag" class="mw-badge mw-badge--done">{{ p.tag }}</span>
            </div>
            <p class="post__text">{{ p.text }}</p>
            <div class="post__foot">
              <span
                ><span class="mw-num">{{ p.likes }}</span> 赞</span
              >
              <span
                ><span class="mw-num">{{ p.comments }}</span> 评论</span
              >
            </div>
          </li>
        </ul>
      </div>

      <aside class="side">
        <div class="panel">
          <h3 class="panel__title">热门话题</h3>
          <ul class="links">
            <li>猫咪应激的 6 个信号</li>
            <li>夏天剃毛到底对不对</li>
            <li>新手养狗第一个月清单</li>
          </ul>
        </div>
        <p class="note">演示数据 · 社区接口已就绪，服务启动后切真数据</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: var(--mw-space-6);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--mw-space-8);
  align-items: start;
}

.chips {
  display: flex;
  gap: var(--mw-space-2);
  margin-bottom: var(--mw-space-4);
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

.posts {
  display: flex;
  flex-direction: column;
  gap: var(--mw-space-3);
}
.post {
  padding: var(--mw-space-6);
  background: var(--mw-surface);
  border: 1px solid var(--mw-line);
  border-radius: var(--mw-radius-card);
}
.post__head {
  display: flex;
  align-items: center;
  gap: var(--mw-space-3);
}
.post__head > .mw-badge {
  margin-left: auto;
}
.avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: var(--mw-radius-pill);
  background: rgba(10, 125, 109, 0.1);
  color: var(--mw-brand-ink);
  font-weight: 700;
}
.post__author {
  font-weight: 600;
  font-size: var(--mw-font-caption);
}
.post__time {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}
.post__text {
  margin-top: var(--mw-space-3);
}
.post__foot {
  display: flex;
  gap: var(--mw-space-6);
  margin-top: var(--mw-space-4);
  padding-top: var(--mw-space-3);
  border-top: 1px solid var(--mw-line);
  font-size: var(--mw-font-caption);
  color: var(--mw-ink-2);
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
.links li {
  padding: var(--mw-space-2) 0;
  border-bottom: 1px solid var(--mw-line);
  font-size: var(--mw-font-caption);
}
.links li:last-child {
  border-bottom: none;
}
.note {
  font-size: var(--mw-font-micro);
  color: var(--mw-muted);
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
