<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import { createRetrievalJob, retrievalJob } from '@/api/rag'
import { errorMessage } from '@/api/http'
import type { RetrievalJobSnapshot, RetrievalResponse } from '@/types/rag'
import { score } from '@/utils'
import { RETRIEVAL_LIMIT_FIELDS, sourceLocation, type RetrievalLimitKey } from '@/retrieval/presentation'
import { formatStageDetail, retrievalProgress, type RetrievalProgressState } from '@/retrieval/progress'
import { startRetrievalJobPolling } from '@/retrieval/jobPolling'

const query = ref('预算调整需要经过哪些审查？')
const loading = ref(false)
const result = ref<RetrievalResponse | null>(null)
const jobSnapshot = ref<RetrievalJobSnapshot | null>(null)
const progressNodes = computed(() => retrievalProgress(jobSnapshot.value))
let stopPolling: (() => void) | null = null
const limits = ref<Record<RetrievalLimitKey, number>>({
  keywordLimit: 20,
  vectorLimit: 20,
  fusionLimit: 30,
  rerankLimit: 15,
  finalLimit: 8,
})

async function run() {
  if (!query.value.trim() || loading.value) return
  stopCurrentPolling()
  loading.value = true
  result.value = null
  jobSnapshot.value = null
  try {
    const created = await createRetrievalJob(query.value.trim(), limits.value)
    stopPolling = startRetrievalJobPolling(
      () => retrievalJob(created.jobUid),
      (snapshot) => {
        jobSnapshot.value = snapshot
        if (snapshot.status === 'COMPLETED') {
          result.value = snapshot.result
          loading.value = false
        } else if (snapshot.status === 'FAILED') {
          loading.value = false
          ElMessage.error(snapshot.error || '检索失败')
        }
      },
      (error) => {
        loading.value = false
        ElMessage.error(errorMessage(error, '检索进度查询失败，请重新执行'))
      },
    )
  } catch (error) {
    loading.value = false
    ElMessage.error(errorMessage(error, '检索失败'))
  }
}

function stopCurrentPolling() {
  stopPolling?.()
  stopPolling = null
}

const stateLabels: Record<RetrievalProgressState, string> = {
  pending: '等待',
  active: '执行中',
  success: '已完成',
  degraded: '已降级',
  skipped: '已跳过',
  failed: '失败',
}

onBeforeUnmount(stopCurrentPolling)
</script>

<template>
  <section>
    <PageHeading eyebrow="RETRIEVAL LAB" title="召回实验室" description="输入真实问题，观察关键词、向量、融合和精排对最终结果的影响。" />
    <article class="query-console">
      <div class="query-console__input"><label>TEST QUERY</label><el-input v-model="query" type="textarea" :rows="3" resize="none" @keydown.ctrl.enter="run" /><small>Ctrl + Enter 快速执行</small></div>
      <details class="advanced-settings">
        <summary>高级检索参数 <small>关键词 20 / 向量 20 / 融合 30 / 精排 15 / 最终 8</small></summary>
        <div class="limit-row">
          <label v-for="field in RETRIEVAL_LIMIT_FIELDS" :key="field.key">
            <span>{{ field.label }}</span>
            <el-tooltip :content="field.help" placement="top">
              <el-input-number v-model="limits[field.key]" :min="1" :max="100" controls-position="right" />
            </el-tooltip>
          </label>
        </div>
      </details>
      <el-button type="primary" size="large" :loading="loading" @click="run">运行混合检索</el-button>
    </article>
    <article v-if="loading || jobSnapshot" class="retrieval-progress" aria-live="polite">
      <div class="retrieval-progress__head">
        <div><p class="eyebrow">LIVE PIPELINE</p><h3>检索执行进度</h3></div>
        <span v-if="jobSnapshot">任务 {{ jobSnapshot.jobUid.slice(0, 8) }}</span>
      </div>
      <div class="retrieval-progress__scroll">
        <div class="retrieval-progress__nodes">
          <div
            v-for="(node, index) in progressNodes"
            :key="node.code"
            class="retrieval-progress__node"
            :class="`is-${node.state}`"
          >
            <div class="retrieval-progress__marker">{{ index + 1 }}</div>
            <div class="retrieval-progress__content">
              <div><b>{{ node.label }}</b><span>{{ stateLabels[node.state] }}</span></div>
              <small>{{ formatStageDetail(node.snapshot) }}</small>
            </div>
          </div>
        </div>
      </div>
      <el-alert
        v-if="jobSnapshot?.status === 'FAILED'"
        :title="jobSnapshot.error || '检索执行失败'"
        type="error"
        :closable="false"
        show-icon
      />
    </article>
    <el-alert v-if="result?.fallbackReason" :title="result.fallbackReason" type="warning" :closable="false" show-icon />
    <div v-if="result" class="timing-strip">
      <span v-for="(value, key) in result.timings" :key="key"><b>{{ value }}</b> ms<small>{{ key }}</small></span>
    </div>
    <article v-if="result" class="panel table-panel">
      <div class="panel__head"><div><p class="eyebrow">RESULT SET</p><h3>最终命中结果</h3></div><router-link :to="`/traces?trace=${result.traceUid}`">打开 Trace →</router-link></div>
      <el-table :data="result.candidates">
        <el-table-column prop="finalRank" label="#" width="58" />
        <el-table-column label="命中内容" min-width="420"><template #default="{ row }"><div class="result-content"><b>{{ row.title || '知识切片' }}</b><p>{{ row.content }}</p><small>{{ sourceLocation(row) }} · {{ row.chunkId }}</small></div></template></el-table-column>
        <el-table-column label="关键词" width="110"><template #default="{ row }"><b>{{ score(row.keywordScore) }}</b><small class="rank">#{{ row.keywordRank ?? '—' }}</small></template></el-table-column>
        <el-table-column label="向量" width="110"><template #default="{ row }"><b>{{ score(row.vectorScore) }}</b><small class="rank">#{{ row.vectorRank ?? '—' }}</small></template></el-table-column>
        <el-table-column label="RRF" width="110"><template #default="{ row }"><b>{{ score(row.rrfScore) }}</b><small class="rank">#{{ row.rrfRank ?? '—' }}</small></template></el-table-column>
        <el-table-column label="Rerank" width="110"><template #default="{ row }"><b>{{ score(row.rerankScore) }}</b><small class="rank">#{{ row.rerankRank ?? '—' }}</small></template></el-table-column>
      </el-table>
    </article>
    <el-empty v-else description="运行一次检索后，这里会展示各阶段得分" />
  </section>
</template>
