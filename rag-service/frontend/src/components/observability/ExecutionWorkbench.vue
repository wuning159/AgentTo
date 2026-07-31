<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ExecutionEvent, TraceCandidate } from '@/types/rag'
import {
  eventStatusLabel,
  eventTone,
  prettyTechnicalValue,
  redactTechnicalValue,
  rrfContribution,
} from '@/observability/presentation'

const props = withDefaults(defineProps<{
  events: ExecutionEvent[]
  totalMs?: number | null
  resultCount?: number | null
  fallbackReason?: string | null
  candidates?: TraceCandidate[]
  rankConstant?: number
  mode?: 'retrieval' | 'ingestion'
}>(), {
  totalMs: null,
  resultCount: null,
  fallbackReason: null,
  candidates: () => [],
  rankConstant: 60,
  mode: 'retrieval',
})

const activeStage = ref('')
const tab = ref<'explain' | 'parameters' | 'samples' | 'raw'>('explain')
const reportOpen = ref(false)

watch(() => props.events, (events) => {
  if (!events.length) return
  const preferred = props.mode === 'retrieval' ? events.find((event) => event.stage === 'FUSION') : null
  if (!events.some((event) => event.stage === activeStage.value)) {
    activeStage.value = (preferred ?? events[0]!).stage
  }
}, { immediate: true })

const active = computed<ExecutionEvent>(() => props.events.find((event) => event.stage === activeStage.value)
  ?? props.events[0]
  ?? {
    stage: 'PENDING', status: 'RUNNING', startedAt: new Date(0).toISOString(), finishedAt: null,
    elapsedMs: null,
    detail: { summary: '', inputCount: null, outputCount: null, parameters: {}, metrics: {}, samples: [], raw: {} },
  })
const hasFallback = computed(() => Boolean(props.fallbackReason)
  || props.events.some((event) => ['DEGRADED', 'SKIPPED', 'FAILED'].includes(event.status)))
const selectedCandidate = computed(() => props.candidates.find((candidate) => candidate.finalRank === 1)
  ?? props.candidates.find((candidate) => candidate.rrfRank != null)
  ?? null)
const parameterEntries = computed(() => Object.entries(active.value?.detail.parameters ?? {}))
const metricEntries = computed(() => Object.entries(active.value?.detail.metrics ?? {}))
const rawJson = computed(() => JSON.stringify(redactTechnicalValue(active.value?.detail.raw ?? {}), null, 2))

function stageLabel(stage: string): string {
  return ({
    PREPROCESS: '查询预处理', KEYWORD: '关键词召回', EMBEDDING: '查询向量', VECTOR: '向量召回',
    FUSION: 'RRF 融合', DEDUPE: '内容去重', RERANK: '精排', COMPLETE: '完成',
    STORE: '文件存储', PARSE: '文档解析', CLEAN: '内容清洗', CHUNK: '结构分块',
    EMBED: '分块向量化', INDEX: '建立索引',
  } as Record<string, string>)[stage] ?? stage
}

function formatElapsed(value: number | null): string {
  if (value == null) return '—'
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s`
}

function clock(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3, hour12: false,
  }).format(new Date(value))
}
</script>

<template>
  <div v-if="events.length" class="execution-workbench">
    <section class="execution-summary">
      <div><span>EXECUTION SUMMARY</span><b>{{ mode === 'retrieval' ? '关键词 + 向量 → RRF → 去重 → 精排' : '存储 → 解析 → 清洗 → 分块 → 向量化 → 索引' }}</b></div>
      <div><span>TOTAL</span><strong>{{ formatElapsed(totalMs ?? events.reduce((sum, item) => sum + (item.elapsedMs ?? 0), 0)) }}</strong></div>
      <div><span>RESULT</span><strong>{{ resultCount ?? active?.detail.outputCount ?? '—' }} <small>条</small></strong></div>
      <div><span>FALLBACK</span><em :class="hasFallback ? 'is-warning' : 'is-success'">{{ hasFallback ? '存在降级' : '未降级' }}</em></div>
    </section>

    <section class="execution-timeline-card">
      <header><div><b>A · 全链路时间线</b><span>点击任一步，下面的技术检查器同步切换</span></div></header>
      <div class="execution-timeline" :style="{ '--stage-count': events.length }">
        <button v-for="(event, index) in events" :key="`${event.stage}-${index}`" type="button"
          :class="[`is-${eventTone(event.status)}`, { 'is-active': activeStage === event.stage }]"
          @click="activeStage = event.stage">
          <span class="execution-timeline__head"><i>{{ index + 1 }}</i><b>{{ stageLabel(event.stage) }}</b></span>
          <small>{{ formatElapsed(event.elapsedMs) }} · {{ event.detail.outputCount ?? eventStatusLabel(event.status) }}</small>
        </button>
      </div>
    </section>

    <section class="execution-main-grid">
      <article class="execution-inspector">
        <header>
          <div><h3>B · 技术检查器：{{ stageLabel(active.stage) }}</h3><p>只解释当前选中的一个步骤</p></div>
          <em :class="`is-${eventTone(active.status)}`">{{ eventStatusLabel(active.status) }}</em>
        </header>
        <nav>
          <button :class="{ active: tab === 'explain' }" @click="tab = 'explain'">处理说明</button>
          <button :class="{ active: tab === 'parameters' }" @click="tab = 'parameters'">参数与公式</button>
          <button :class="{ active: tab === 'samples' }" @click="tab = 'samples'">输入输出样本</button>
          <button :class="{ active: tab === 'raw' }" @click="tab = 'raw'">原始数据</button>
        </nav>
        <div class="execution-inspector__body">
          <template v-if="tab === 'explain'">
            <div class="execution-explain"><i>i</i><div><b>这一步做了什么？</b><p>{{ active.detail.summary }}</p></div></div>
            <div class="execution-counts">
              <div><span>输入数量</span><b>{{ active.detail.inputCount ?? '—' }}</b></div>
              <div><span>输出数量</span><b>{{ active.detail.outputCount ?? '—' }}</b></div>
              <div><span>执行耗时</span><b>{{ formatElapsed(active.elapsedMs) }}</b></div>
              <div><span>执行状态</span><b>{{ eventStatusLabel(active.status) }}</b></div>
            </div>
          </template>
          <template v-else-if="tab === 'parameters'">
            <div v-if="active.stage === 'FUSION'" class="rrf-formula">
              <code>RRF(d) = Σ 1 / (<mark>k</mark> + rankᵢ(d))</code>
              <p>k = <b>{{ rankConstant }}</b>，它是平滑常数，不是候选数量。名次越靠前贡献越大。</p>
              <table v-if="selectedCandidate"><tbody>
                <tr><td>关键词排名</td><td>#{{ selectedCandidate.keywordRank ?? '—' }}</td><td>{{ rrfContribution(selectedCandidate.keywordRank, rankConstant).toFixed(5) }}</td></tr>
                <tr><td>向量排名</td><td>#{{ selectedCandidate.vectorRank ?? '—' }}</td><td>{{ rrfContribution(selectedCandidate.vectorRank, rankConstant).toFixed(5) }}</td></tr>
                <tr><th>融合得分</th><th>#{{ selectedCandidate.rrfRank ?? '—' }}</th><th>{{ selectedCandidate.rrfScore?.toFixed(5) ?? '—' }}</th></tr>
              </tbody></table>
            </div>
            <div class="execution-kv-grid">
              <div v-for="([key, value]) in parameterEntries" :key="key"><span>{{ key }}</span><b>{{ prettyTechnicalValue(value) }}</b></div>
              <div v-for="([key, value]) in metricEntries" :key="`metric-${key}`"><span>{{ key }}</span><b>{{ prettyTechnicalValue(value) }}</b></div>
            </div>
            <el-empty v-if="!parameterEntries.length && !metricEntries.length && active.stage !== 'FUSION'" description="本步骤没有额外参数" :image-size="48" />
          </template>
          <template v-else-if="tab === 'samples'">
            <div class="execution-samples">
              <pre v-for="(sample, index) in active.detail.samples" :key="index">{{ JSON.stringify(redactTechnicalValue(sample), null, 2) }}</pre>
            </div>
            <el-empty v-if="!active.detail.samples.length" description="本步骤没有保存样例" :image-size="48" />
          </template>
          <template v-else><pre class="execution-raw">{{ rawJson }}</pre></template>
        </div>
      </article>

      <aside class="execution-side">
        <header><h3>本步骤输入与输出</h3><p>观察数据如何进入、收敛或被降级</p></header>
        <div class="execution-flow"><div><b>{{ active.detail.inputCount ?? '—' }}</b><span>输入</span></div><i>→</i><div><b>{{ active.detail.outputCount ?? '—' }}</b><span>输出</span></div></div>
        <article v-for="(sample, index) in active.detail.samples.slice(0, 3)" :key="index" class="execution-sample-card">
          <b>#{{ index + 1 }} · {{ String(sample.chunkId ?? sample.title ?? '样例') }}</b>
          <p>{{ String(sample.content ?? prettyTechnicalValue(sample)) }}</p>
        </article>
        <div v-if="active.status === 'DEGRADED' || active.status === 'SKIPPED'" class="execution-warning">{{ active.detail.summary }}</div>
        <div v-if="active.stage === 'DEDUPE'" class="execution-note">重复候选保留在完整 Trace 中用于排查，但不会重复进入精排和最终结果。</div>
      </aside>
    </section>

    <section class="execution-report">
      <header>
        <div><h3>C · 完整执行报告</h3><p>保留所有阶段的状态、参数快照、输入输出数量和异常信息。</p></div>
        <el-button @click="reportOpen = !reportOpen">{{ reportOpen ? '收起' : `展开完整报告（${events.length} 个事件）` }}</el-button>
      </header>
      <el-collapse-transition>
        <div v-if="reportOpen" class="execution-report__table">
          <div class="is-head"><span>时间</span><span>事件</span><span>执行记录</span><span>结果</span></div>
          <div v-for="event in events" :key="event.stage"><span>{{ clock(event.startedAt) }}</span><b>{{ event.stage }}</b><p>{{ event.detail.summary }}</p><em :class="`is-${eventTone(event.status)}`">{{ eventStatusLabel(event.status) }} · {{ formatElapsed(event.elapsedMs) }}</em></div>
        </div>
      </el-collapse-transition>
    </section>
  </div>
</template>
