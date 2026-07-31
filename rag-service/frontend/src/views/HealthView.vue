<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeading from '@/components/PageHeading.vue'
import { dashboard } from '@/api/rag'
import type { DashboardOverview } from '@/types/rag'

const data = ref<DashboardOverview | null>(null)
const loading = ref(false)
async function load() { loading.value = true; try { data.value = await dashboard() } finally { loading.value = false } }
onMounted(load)
</script>

<template>
  <section v-loading="loading">
    <PageHeading eyebrow="INFRASTRUCTURE" title="服务状态" description="检查数据库、对象存储、搜索引擎和模型服务能否正常响应。"><el-button type="primary" @click="load">重新检测</el-button></PageHeading>
    <div class="health-grid">
      <article v-for="item in data?.dependencies" :key="item.name" class="health-card" :class="{ 'health-card--bad': !item.healthy }">
        <div><span :class="['status-dot', item.healthy ? 'status-dot--ok' : 'status-dot--bad']" /><small>{{ item.healthy ? 'CONNECTED' : 'UNAVAILABLE' }}</small></div>
        <h3>{{ item.name }}</h3><p>{{ item.detail }}</p><b>{{ item.healthy ? '运行正常' : '需要检查' }}</b>
      </article>
    </div>
    <article class="panel boundary-note"><p class="eyebrow">DEPLOYMENT BOUNDARY</p><h3>当前开发环境</h3><p>前端与后端在本机运行；MySQL、MinIO、Elasticsearch、TEI Embedding 和 TEI Rerank 作为远程依赖服务接入。页面只展示连通性，不展示连接密钥。</p></article>
  </section>
</template>
