import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '@/api/request'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '首页' },
    },
    {
      path: '/community',
      name: 'community',
      component: () => import('@/views/CommunityView.vue'),
      meta: { title: '社区' },
    },
    {
      path: '/pets',
      name: 'pets',
      component: () => import('@/views/PetsView.vue'),
      meta: { title: '萌宠', requiresAuth: true },
    },
    // 注意顺序与约束：/pets/new 必须能匹配，所以详情用 \d+ 限定成数字 pid
    {
      path: '/pets/new',
      name: 'pet-new',
      component: () => import('@/views/PetFormView.vue'),
      meta: { title: '添加宠物', requiresAuth: true },
    },
    {
      path: '/pets/:pid(\\d+)',
      name: 'pet-detail',
      component: () => import('@/views/PetDetailView.vue'),
      meta: { title: '宠物档案', requiresAuth: true },
    },
    {
      path: '/pets/:pid(\\d+)/edit',
      name: 'pet-edit',
      component: () => import('@/views/PetFormView.vue'),
      meta: { title: '编辑宠物', requiresAuth: true },
    },
    {
      path: '/knowledge',
      name: 'knowledge',
      component: () => import('@/views/KnowledgeView.vue'),
      meta: { title: '知识库' },
    },
    {
      path: '/mine',
      name: 'mine',
      component: () => import('@/views/MineView.vue'),
      meta: { title: '我的', requiresAuth: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { title: '注册' },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

/**
 * 登录守卫
 *
 * 只看本地有没有会话就放行，真正的有效性由请求层判定（401 或业务未登录码会清会话）。
 * 这样后端没启动时不会把用户反复弹回登录页。
 */
router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true
  if (auth.token && auth.username) return true
  return { name: 'login', query: { redirect: to.fullPath } }
})

router.afterEach((to) => {
  const t = to.meta.title as string | undefined
  document.title = t ? `${t} · 喵汪` : '喵汪'
})

export default router
