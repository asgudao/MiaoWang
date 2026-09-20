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
    // 帖子详情：社区里唯一带正文的接口。
    // 社区经网关的每个接口都要登录（连版块列表都要），所以这里挂 requiresAuth，
    // 未登录点进来会带着 redirect 去登录页，而不是停在一个必然报错的空页上
    {
      path: '/community/post/:postId(\\d+)',
      name: 'community-post',
      component: () => import('@/views/PostDetailView.vue'),
      meta: { title: '帖子', requiresAuth: true },
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
    // 健康记录：壳页 + 八个子页 + 新建/编辑。
    // 路径故意放在 `/pets/:pid/edit` 之后（vue-router 按具体度排序，顺序不影响匹配），
    // 名字必须与页面里的 `{ name: 'health-xxx' }` 逐字一致：概览的六个计数块就是用
    // `health-${type}` 拼出来的（preventive / medical / medication / allergy / symptom / weight）
    {
      path: '/pets/:pid(\\d+)/health',
      component: () => import('@/views/health/HealthShellView.vue'),
      meta: { title: '健康记录', requiresAuth: true },
      children: [
        {
          path: '',
          name: 'health-overview',
          component: () => import('@/views/health/HealthOverviewView.vue'),
          meta: { title: '健康概览' },
        },
        {
          path: 'timeline',
          name: 'health-timeline',
          component: () => import('@/views/health/HealthTimelineView.vue'),
          meta: { title: '健康时间线' },
        },
        {
          path: 'weight',
          name: 'health-weight',
          component: () => import('@/views/health/HealthWeightView.vue'),
          meta: { title: '体重曲线' },
        },
        {
          path: 'preventive',
          name: 'health-preventive',
          component: () => import('@/views/health/HealthPreventiveView.vue'),
          meta: { title: '疫苗与驱虫' },
        },
        {
          path: 'medical',
          name: 'health-medical',
          component: () => import('@/views/health/HealthMedicalView.vue'),
          meta: { title: '就诊记录' },
        },
        {
          path: 'medication',
          name: 'health-medication',
          component: () => import('@/views/health/HealthMedicationView.vue'),
          meta: { title: '用药记录' },
        },
        {
          path: 'allergy',
          name: 'health-allergy',
          component: () => import('@/views/health/HealthAllergyView.vue'),
          meta: { title: '过敏史' },
        },
        {
          path: 'symptom',
          name: 'health-symptom',
          component: () => import('@/views/health/HealthSymptomView.vue'),
          meta: { title: '症状日记' },
        },
        // 新建与编辑共用同一个表单页；`kind` 用正则限定成六类，
        // 避免拼错的 URL 进来之后页面要处理"未知类型"这种本不该存在的状态
        {
          path: 'new/:kind(preventive|medical|medication|allergy|symptom|weight)',
          name: 'health-record-new',
          component: () => import('@/views/health/HealthRecordFormView.vue'),
          meta: { title: '新增健康记录' },
        },
        {
          path: ':kind(preventive|medical|medication|allergy|symptom|weight)/:id(\\d+)/edit',
          name: 'health-record-edit',
          component: () => import('@/views/health/HealthRecordFormView.vue'),
          meta: { title: '编辑健康记录' },
        },
      ],
    },
    {
      path: '/knowledge',
      name: 'knowledge',
      component: () => import('@/views/KnowledgeView.vue'),
      meta: { title: '知识库' },
    },
    // AI 问答：/ai 是新对话，/ai/:id 打开历史会话（同一个组件，靠 watch 参数切换）
    {
      path: '/ai',
      name: 'ai',
      component: () => import('@/views/AiChatView.vue'),
      meta: { title: 'AI 问诊', requiresAuth: true },
    },
    {
      path: '/ai/:conversationId(\\d+)',
      name: 'ai-chat',
      component: () => import('@/views/AiChatView.vue'),
      meta: { title: 'AI 问诊', requiresAuth: true },
    },
    {
      path: '/mine',
      name: 'mine',
      component: () => import('@/views/MineView.vue'),
      meta: { title: '我的', requiresAuth: true },
    },
    // 会员：AI 问答页额度用完时引导过来的落地页。
    // 注意 `GET /vip/plan` 实测经网关**需要 token**（匿名返回 state:-1），所以必须挂 requiresAuth
    {
      path: '/vip',
      name: 'vip',
      component: () => import('@/views/VipView.vue'),
      meta: { title: '会员', requiresAuth: true },
    },
    // 支付宝沙箱付完款会按 return_url 跳回这里（query 带 out_trade_no）。
    // 页面只把 query 当成"要查哪一笔"，结果一律以 POST /vip/order/{no}/sync 的返回为准
    {
      path: '/vip/pay/return',
      name: 'vip-pay-return',
      component: () => import('@/views/VipPayReturnView.vue'),
      meta: { title: '支付结果', requiresAuth: true },
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
