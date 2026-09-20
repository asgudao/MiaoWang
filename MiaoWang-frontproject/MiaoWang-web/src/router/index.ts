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
    // 用户主页：社区帖子 / 评论 / 回复的作者名点进来。
    // uid 只能从路由参数或社区 DTO 拿（user 模块没有"按 uid 查"，`/user/{username}` 的 id 恒 null），
    // 所以路径参数用 \d+ 限定成数字，别让它退化成用户名。
    // **挂 requiresAuth**：主页的四个接口经网关全部要求登录（社区一条白名单都没有，用户已拍板不改网关）
    {
      path: '/user/:uid(\\d+)',
      name: 'user-profile',
      component: () => import('@/views/UserProfileView.vue'),
      meta: { title: '用户主页', requiresAuth: true },
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
    // 宠物对话（ai-pet）：一只宠物一条会话，没有"话题列表"这种东西，所以只有一页。
    // **挂 requiresAuth**：`/ai/pet` 的三个端点经网关**全部要求登录**
    // （网关白名单故意没有它们，匿名一律 state:-1），未登录点进来会带着 redirect 去登录页，
    // 而不是停在一个必然报错的空页上。页内 `needLogin` 软门槛是第二道。
    // 两条路由指向同一个组件：不带 pid 时页面自己读 `GET /pet` 取第一只（chat/greet 的缺省口径也是"第一只"）。
    // ⚠️ `/pets/chat` 不会被 `/pets/:pid(\\d+)` 抢走（那条用 `\\d+` 限定了数字），两条也不会互相干扰。
    {
      path: '/pets/chat',
      name: 'pet-chat',
      component: () => import('@/views/PetChatView.vue'),
      meta: { title: '宠物对话', requiresAuth: true },
    },
    {
      path: '/pets/:pid(\\d+)/chat',
      name: 'pet-chat-pet',
      component: () => import('@/views/PetChatView.vue'),
      meta: { title: '宠物对话', requiresAuth: true },
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
    // 知识库：公开内容区，**刻意不挂 requiresAuth** ——
    // 经网关的 5 个知识库接口（category / fragment / fragment/page / fragment/{id} / fragment/search）
    // 全部匿名可读（网关白名单按"读路径"逐条放行），请求层也走 anonymous，
    // 所以未登录用户必须能正常浏览，不能被推到登录页。
    // 详情用 \d+ 限定成数字 id：与 `/knowledge` 本身不冲突，也顺手挡住脏地址。
    {
      path: '/knowledge',
      name: 'knowledge',
      component: () => import('@/views/KnowledgeView.vue'),
      meta: { title: '知识库' },
    },
    {
      path: '/knowledge/:id(\\d+)',
      name: 'knowledge-detail',
      component: () => import('@/views/KnowledgeDetailView.vue'),
      meta: { title: '知识详情' },
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
    // 个人资料：入口是「我的」页那一行的 profile 行。
    // ⚠️ 与 `/user/:uid`（user-profile，看**别人**）是两回事：这里是"我自己的资料"。
    // 路径不同、名字不同，两条路由不冲突（一条是固定段 `/profile`，一条带数字参数 `/user/:uid`）。
    // **挂 requiresAuth**：这一页要读 `GET /user/{username}`（需鉴权头）、`PUT /user`、
    // `POST /user/avatar`、社区自查资料，经网关**全部要求登录**（user 与 community 都没有白名单）。
    // 未登录会被守卫带着 redirect 送去登录页；页内 `needLogin` 软门槛是第二道（见 ProfileView.vue）。
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { title: '个人资料', requiresAuth: true },
    },
    // 关于喵汪：入口是「我的」页那一行的 about 行。
    // **刻意不挂 requiresAuth**：这一页是纯静态（零网络请求），内容全是工程自身的可核对事实
    // （版本、依赖许可、3D 模型授权、后端现状），没有任何按人区分的数据。
    // 未登录也必须能看 —— 挂上 requiresAuth 只会让"会话过期的人想看一眼版本号"被推去登录页。
    {
      path: '/about',
      name: 'about',
      component: () => import('@/views/AboutView.vue'),
      meta: { title: '关于喵汪' },
    },
    // 消息通知：入口是「我的」页那一行 + 顶部导航的铃铛。
    // **挂 requiresAuth**：通知的四个接口经网关全部要求登录（社区一条白名单都没有，用户已拍板不改网关），
    // 未登录点铃铛会带着 redirect 去登录页，而不是停在一个必然报错的空页上。
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/NotificationsView.vue'),
      meta: { title: '消息通知', requiresAuth: true },
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
    // 提醒设置：入口是「我的」页那一行的 remind 行（App 侧的弹窗也会链到这里）。
    // **挂 requiresAuth**：这一页要读 `/pet` 与每只宠物 `/health/{pid}/preventive`
    // （经网关全部要求登录），未登录时连一条提醒都算不出来 → 送登录页，而不是停在必然报错的空页上。
    // 页内 `needLogin` 软门槛是第二道（见 views/remind/SettingView.vue）。
    {
      path: '/remind/setting',
      name: 'remind-setting',
      component: () => import('@/views/remind/SettingView.vue'),
      meta: { title: '提醒设置', requiresAuth: true },
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
