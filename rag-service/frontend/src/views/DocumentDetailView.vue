<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import ExecutionWorkbench from '@/components/observability/ExecutionWorkbench.vue'
import { chunks, documentDetail, latestVersionIngestion } from '@/api/rag'
import { errorMessage } from '@/api/http'
import type { ChunkView, DocumentDetail, IngestionJob } from '@/types/rag'
import { formatBytes, formatTime, statusLabel, statusType } from '@/utils'
import { ingestionElapsed, ingestionExecutionEvents } from '@/ingestion/executionReport'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const detail = ref<DocumentDetail | null>(null)
const chunkRows = ref<ChunkView[]>([])
const chunkTotal = ref(0)
const loading = ref(true)
const ingestion = ref<IngestionJob | null>(null)
const ingestionDrawer = ref(false)
const ingestionLoading = ref(false)
const currentVersion = computed(() => detail.value?.versions[0] ?? null)
const ingestionEvents = computed(() => ingestion.value ? ingestionExecutionEvents(ingestion.value) : [])
const ingestionTotalMs = computed(() => ingestion.value ? ingestionElapsed(ingestion.value) : null)

async function load() {
  loading.value = true
  try {
    detail.value = await documentDetail(id)
    if (currentVersion.value) {
      const result = await chunks(currentVersion.value.id, 0, 100)
      chunkRows.value = result.items; chunkTotal.value = result.total
    }
  } finally { loading.value = false }
}

async function openProcessing(versionId: number) {
  ingestionLoading.value = true
  ingestionDrawer.value = true
  try {
    ingestion.value = await latestVersionIngestion(versionId)
  } catch (error) {
    ingestionDrawer.value = false
    ElMessage.error(errorMessage(error, '读取处理过程失败'))
  } finally {
    ingestionLoading.value = false
  }
}
onMounted(load)
</script>

<template>
  <section v-loading="loading">
    <PageHeading eyebrow="DOCUMENT INSPECTOR" :title="detail?.document.name ?? '文档详情'" description="核对原始版本、处理结果和每一条结构化切片。">
      <el-button @click="router.back()">返回列表</el-button>
      <el-button v-if="currentVersion" type="primary" @click="openProcessing(currentVersion.id)">查看处理过程</el-button>
    </PageHeading>
    <div v-if="detail" class="detail-grid">
      <article class="panel metadata-card">
        <div class="panel__head"><div><p class="eyebrow">SOURCE</p><h3>文件信息</h3></div><el-tag :type="statusType(detail.document.status)">{{ statusLabel(detail.document.status) }}</el-tag></div>
        <dl><dt>分类</dt><dd>{{ detail.document.category || '未分类' }}</dd><dt>文件大小</dt><dd>{{ formatBytes(currentVersion?.fileSize ?? 0) }}</dd><dt>切片数量</dt><dd>{{ currentVersion?.chunkCount ?? 0 }}</dd><dt>索引版本</dt><dd>{{ currentVersion?.indexVersion || '—' }}</dd><dt>SHA-256</dt><dd class="hash">{{ currentVersion?.sha256 }}</dd><dt>上传时间</dt><dd>{{ formatTime(currentVersion?.createdAt) }}</dd></dl>
      </article>
      <article class="panel version-card">
        <div class="panel__head"><div><p class="eyebrow">VERSIONS</p><h3>版本记录</h3></div></div>
        <div v-for="version in detail.versions" :key="version.id" class="version-row"><b>v{{ version.versionNo }}</b><span>{{ version.filename }}</span><el-tag size="small" :type="statusType(version.processingStatus)">{{ statusLabel(version.processingStatus) }}</el-tag></div>
      </article>
    </div>
    <article class="panel chunks-panel">
      <div class="panel__head"><div><p class="eyebrow">CHUNKS</p><h3>结构化切片</h3></div><span class="quiet">共 {{ chunkTotal }} 条</span></div>
      <div class="chunk-list">
        <article v-for="chunk in chunkRows" :key="chunk.chunkUid" class="chunk-card">
          <div class="chunk-card__meta"><b>#{{ chunk.ordinal + 1 }}</b><span>{{ chunk.title || chunk.sectionPath || '正文' }}</span><small v-if="chunk.page">第 {{ chunk.page }} 页</small><small v-if="chunk.sheetName">{{ chunk.sheetName }} · 行 {{ chunk.rowStart }}</small></div>
          <p>{{ chunk.content }}</p><code>{{ chunk.chunkUid }}</code>
        </article>
      </div>
      <el-empty v-if="!chunkRows.length" description="文件还没有生成切片" />
    </article>
    <el-drawer v-model="ingestionDrawer" title="文档处理过程" size="92%">
      <div v-loading="ingestionLoading" class="detail-ingestion-drawer">
        <ExecutionWorkbench v-if="ingestion" mode="ingestion" :events="ingestionEvents" :total-ms="ingestionTotalMs" :result-count="currentVersion?.chunkCount ?? null" />
      </div>
    </el-drawer>
  </section>
</template>
