<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dashboard as fetchDashboard } from '@/api/rag'
import type { DashboardOverview } from '@/types/rag'
import PageHeading from '@/components/PageHeading.vue'

const data = ref<DashboardOverview | null>(null)
const loading = ref(true)
const router = useRouter()
const healthyCount = computed(() => data.value?.dependencies.filter((item) => item.healthy).length ?? 0)

async function load() {
  loading.value = true
  try { data.value = await fetchDashboard() } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section v-loading="loading">
    <PageHeading eyebrow="RAG OPERATIONS" title="运行概览" description="今天的知识入库、检索行为和基础服务状态集中在这里。">
      <el-button @click="load">刷新状态</el-button>
      <el-button type="primary" @click="router.push('/documents')">上传文件</el-button>
    </PageHeading>

    <div class="metric-grid">
      <article class="metric-card metric-card--dark"><span>文档总数</span><strong>{{ data?.totalDocuments ?? 0 }}</strong><small>{{ data?.readyDocuments ?? 0 }} 份可检索</small></article>
      <article class="metric-card"><span>知识切片</span><strong>{{ data?.totalChunks ?? 0 }}</strong><small>已写入索引的最小知识单元</small></article>
      <article class="metric-card"><span>检索 Trace</span><strong>{{ data?.totalTraces ?? 0 }}</strong><small>每次检索均保留过程</small></article>
      <article class="metric-card"><span>依赖健康</span><strong>{{ healthyCount }}/{{ data?.dependencies.length ?? 0 }}</strong><small>{{ data?.failedJobs ?? 0 }} 个失败任务待处理</small></article>
    </div>

    <div class="dashboard-grid">
      <article class="panel">
        <div class="panel__head"><div><p class="eyebrow">PIPELINE</p><h3>入库链路</h3></div><span class="quiet">可观察的七个阶段</span></div>
        <div class="pipeline">
          <div v-for="(stage, index) in ['存储','解析','清洗','分块','向量化','索引','完成']" :key="stage" class="pipeline__stage">
            <b>{{ String(index + 1).padStart(2, '0') }}</b><span>{{ stage }}</span>
          </div>
        </div>
        <div class="notice-row"><span class="status-dot status-dot--warning" />处理中 {{ data?.processingDocuments ?? 0 }} 份</div>
      </article>
      <article class="panel">
        <div class="panel__head"><div><p class="eyebrow">DEPENDENCIES</p><h3>服务连通性</h3></div><router-link to="/health">查看全部</router-link></div>
        <div class="dependency-list">
          <div v-for="item in data?.dependencies" :key="item.name"><span :class="['status-dot', item.healthy ? 'status-dot--ok' : 'status-dot--bad']" /><b>{{ item.name }}</b><small>{{ item.healthy ? '正常' : '异常' }}</small></div>
        </div>
      </article>
    </div>
  </section>
</template>
