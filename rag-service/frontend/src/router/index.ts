import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import LoginView from '@/views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    {
      path: '/',
      component: AppLayout,
      meta: { auth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '运行概览' } },
        { path: 'documents', component: () => import('@/views/DocumentsView.vue'), meta: { title: '文档与入库' } },
        { path: 'documents/:id', component: () => import('@/views/DocumentDetailView.vue'), meta: { title: '文档详情' } },
        { path: 'retrieval', component: () => import('@/views/RetrievalView.vue'), meta: { title: '召回实验室' } },
        { path: 'traces', component: () => import('@/views/TracesView.vue'), meta: { title: '检索 Trace' } },
        { path: 'health', component: () => import('@/views/HealthView.vue'), meta: { title: '服务状态' } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('rag-admin-token')
  if (to.meta.auth && !token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path === '/login' && token) return '/dashboard'
})

export default router
