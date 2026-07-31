import type { RetrievalJobSnapshot, RetrievalStageCode, RetrievalStageSnapshot } from '@/types/rag'

export type RetrievalProgressState = 'pending' | 'active' | 'success' | 'degraded' | 'skipped' | 'failed'

export interface RetrievalProgressNode {
  code: RetrievalStageCode
  label: string
  state: RetrievalProgressState
  snapshot: RetrievalStageSnapshot | null
}

export const RETRIEVAL_STAGES: ReadonlyArray<Readonly<{ code: RetrievalStageCode; label: string }>> = [
  { code: 'PREPROCESS', label: '查询预处理' },
  { code: 'KEYWORD', label: '关键词召回' },
  { code: 'EMBEDDING', label: '查询向量生成' },
  { code: 'VECTOR', label: '向量召回' },
  { code: 'FUSION', label: '结果融合' },
  { code: 'DEDUPE', label: '内容去重' },
  { code: 'RERANK', label: '精排' },
  { code: 'COMPLETE', label: '完成' },
]

const STATE_BY_STATUS: Record<RetrievalStageSnapshot['status'], RetrievalProgressState> = {
  PENDING: 'pending',
  RUNNING: 'active',
  COMPLETED: 'success',
  DEGRADED: 'degraded',
  SKIPPED: 'skipped',
  FAILED: 'failed',
}

export function retrievalProgress(job: RetrievalJobSnapshot | null): RetrievalProgressNode[] {
  const records = new Map((job?.stages ?? []).map((stage) => [stage.stage, stage]))
  return RETRIEVAL_STAGES.map(({ code, label }) => {
    const snapshot = records.get(code) ?? null
    return {
      code,
      label,
      state: snapshot ? STATE_BY_STATUS[snapshot.status] : 'pending',
      snapshot,
    }
  })
}

export function formatStageDetail(stage: RetrievalStageSnapshot | null): string {
  if (!stage || stage.status === 'PENDING') return '等待执行'
  if (stage.status === 'RUNNING') return '执行中'

  const metrics: string[] = []
  if (stage.itemCount !== null) metrics.push(`${stage.itemCount} 条`)
  if (stage.elapsedMs !== null) metrics.push(formatElapsed(stage.elapsedMs))
  const detail = metrics.join(' / ')
  if (stage.message) return detail ? `${detail} · ${stage.message}` : stage.message
  return detail || statusLabel(stage.status)
}

function formatElapsed(elapsedMs: number): string {
  if (elapsedMs < 1000) return `${elapsedMs} ms`
  return `${Number((elapsedMs / 1000).toFixed(2))} s`
}

function statusLabel(status: RetrievalStageSnapshot['status']): string {
  switch (status) {
    case 'COMPLETED': return '已完成'
    case 'DEGRADED': return '已降级'
    case 'SKIPPED': return '已跳过'
    case 'FAILED': return '执行失败'
    case 'RUNNING': return '执行中'
    default: return '等待执行'
  }
}
