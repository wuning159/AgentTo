<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Collection, DataAnalysis, Document, Monitor, Search, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const items = [
  { path: '/dashboard', label: '运行概览', hint: 'Overview', icon: Monitor },
  { path: '/documents', label: '文档与入库', hint: 'Documents', icon: Document },
  { path: '/retrieval', label: '召回实验室', hint: 'Retrieval', icon: Search },
  { path: '/traces', label: '检索 Trace', hint: 'Observability', icon: DataAnalysis },
  { path: '/health', label: '服务状态', hint: 'Infrastructure', icon: Collection },
]

const title = computed(() => String(route.meta.title ?? 'RAG 技术管理台'))

async function signOut() {
  await auth.signOut()
  router.replace('/login')
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand__mark"><span /><span /><span /></div>
        <div>
          <strong>AgentTo</strong>
          <small>RAG CONTROL ROOM</small>
        </div>
      </div>

      <div class="sidebar__caption">WORKSPACE</div>
      <nav>
        <router-link v-for="item in items" :key="item.path" :to="item.path" class="nav-link">
          <el-icon><component :is="item.icon" /></el-icon>
          <span><b>{{ item.label }}</b><small>{{ item.hint }}</small></span>
          <i />
        </router-link>
      </nav>

      <div class="sidebar__foot">
        <div class="operator">
          <div class="operator__avatar">{{ auth.user?.displayName?.slice(0, 1) ?? '管' }}</div>
          <div><b>{{ auth.user?.displayName ?? '技术管理员' }}</b><small>{{ auth.user?.username }}</small></div>
        </div>
        <button class="icon-button" title="退出登录" @click="signOut"><el-icon><SwitchButton /></el-icon></button>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div><small>AgentTo / RAG</small><strong>{{ title }}</strong></div>
        <div class="environment"><span /> DEV ENVIRONMENT</div>
      </header>
      <main class="content"><router-view /></main>
    </section>
  </div>
</template>
