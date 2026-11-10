import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '注册', public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/templates',
    children: [
      {
        path: 'templates',
        name: 'templates',
        component: () => import('@/views/TemplateManageView.vue'),
        meta: { title: '模板库', eyebrow: 'Template Library' }
      },
      {
        path: 'submit',
        name: 'submit',
        component: () => import('@/views/CheckSubmitView.vue'),
        meta: { title: '新建校验', eyebrow: 'New Audit' }
      },
      {
        path: 'tasks',
        name: 'tasks',
        component: () => import('@/views/TaskListView.vue'),
        meta: { title: '任务记录', eyebrow: 'Audit History' }
      },
      {
        path: 'tasks/:id',
        name: 'task-detail',
        component: () => import('@/views/TaskDetailView.vue'),
        meta: { title: '校验结果', eyebrow: 'Audit Report' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { title: '个人中心', eyebrow: 'Account Center' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/templates' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const token = localStorage.getItem('dc_token')
  if (to.meta.public) {
    return token ? '/templates' : true
  }
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '格式智检'} · 文档格式智能校验`
})

export default router
