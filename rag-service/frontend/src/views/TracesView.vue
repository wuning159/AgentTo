<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageHeading from '@/components/PageHeading.vue'
import ExecutionWorkbench from '@/components/observability/ExecutionWorkbench.vue'
import { traceDetail, traces } from '@/api/rag'
import type { TraceDetail, TraceSummary } from '@/types/rag'
import { formatTime, score } from '@/utils'
import { finalTraceCandidates, rerankText } from '@/retrieval/presentation'

const rows = ref<TraceSummary[]>([])
const selected = ref<TraceDetail | null>(null)
const drawer = ref(false)
const candidateView = ref<'final' | 'all'>('final')
const route = useRoute()
const displayedCandidates = computed(() => {
  if (!selected.value) return []
  return candidateView.value === 'final'
    ? finalTraceCandidates(selected.value.candidates)
    : selected.value.candidates
})

async function load() {
  rows.value = await traces(50)
  if (typeof route.query.trace === 'string') await open(route.query.trace)
}
async function open(uid: string) {
  selected.value = await traceDetail(uid)
  candidateView.value = 'final'
  drawer.value = true
}
onMounted(load)
</script>

<template>
  <section>
    <PageHeading eyebrow="OBSERVABILITY" title="检索 Trace" description="回看一次检索从候选召回、融合到最终精排的完整过程。"><el-button @click="load">刷新</el-button></PageHeading>
    <article class="panel table-panel">
      <el-table :data="rows" @row-click="(row: TraceSummary) => open(row.traceUid)">
        <el-table-column prop="query" label="检索问题" min-width="320" />
        <el-table-column prop="retrievalMode" label="模式" width="110" />
        <el-table-column prop="resultCount" label="结果" width="80" />
        <el-table-column prop="totalMs" label="耗时" width="100"><template #default="{ row }">{{ row.totalMs }} ms</template></el-table-column>
        <el-table-column label="降级" width="110"><template #default="{ row }"><el-tag :type="row.fallbackReason ? 'warning' : 'success'">{{ row.fallbackReason ? '有降级' : '完整链路' }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="时间" width="190"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
      </el-table>
    </article>
    <el-drawer v-model="drawer" title="检索过程详情" size="72%">
      <template v-if="selected">
        <div class="trace-title"><p class="eyebrow">{{ selected.traceUid }}</p><h2>{{ selected.query }}</h2><span>{{ formatTime(selected.createdAt) }}</span></div>
        <ExecutionWorkbench
          :events="selected.executionReport.events"
          :total-ms="selected.timings.totalMs"
          :result-count="selected.resultCount"
          :fallback-reason="selected.fallbackReason"
          :candidates="selected.candidates"
          :rank-constant="selected.rankConstant"
        />
        <el-alert v-if="!selected.executionReport.events.length" title="这是一条旧 Trace，未保存分阶段技术快照；下方候选分数仍可正常查看。" type="info" :closable="false" />
        <el-tabs v-model="candidateView" class="trace-tabs">
          <el-tab-pane :label="`最终结果 (${finalTraceCandidates(selected.candidates).length})`" name="final" />
          <el-tab-pane :label="`全部候选 (${selected.candidates.length})`" name="all" />
        </el-tabs>
        <el-table :data="displayedCandidates" height="calc(100vh - 400px)">
          <el-table-column label="候选内容" min-width="330"><template #default="{ row }"><div class="trace-candidate-cell"><b>{{ row.title || '知识切片' }}</b><p>{{ row.content || '历史记录未保存正文快照' }}</p><code>{{ row.chunkId }}</code></div></template></el-table-column>
          <el-table-column label="Keyword" width="115"><template #default="{ row }">{{ score(row.keywordScore) }} <small>#{{ row.keywordRank ?? '—' }}</small></template></el-table-column>
          <el-table-column label="Vector" width="115"><template #default="{ row }">{{ score(row.vectorScore) }} <small>#{{ row.vectorRank ?? '—' }}</small></template></el-table-column>
          <el-table-column label="RRF" width="115"><template #default="{ row }">{{ score(row.rrfScore) }} <small>#{{ row.rrfRank ?? '—' }}</small></template></el-table-column>
          <el-table-column label="Rerank" width="145"><template #default="{ row }"><span :class="{ 'stage-missed': row.rerankRank == null }">{{ rerankText(row) }}</span></template></el-table-column>
          <el-table-column prop="finalRank" label="Final" width="80"><template #default="{ row }"><b>{{ row.finalRank ?? '—' }}</b></template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </section>
</template>
