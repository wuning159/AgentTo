import type { ExecutionEvent, IngestionJob, TechnicalStageDetail } from '@/types/rag'

export function ingestionExecutionEvents(job: IngestionJob, now = new Date()): ExecutionEvent[] {
  const events = job.stages.map((stage): ExecutionEvent => ({
    stage: stage.stage,
    status: stage.status === 'FAILED' ? 'FAILED' : 'COMPLETED',
    startedAt: stage.startedAt,
    finishedAt: stage.finishedAt,
    elapsedMs: stage.elapsedMs,
    detail: stage.technicalDetail ?? fallbackDetail(stage.detail, stage.itemCount),
  }))
  if (job.status === 'RUNNING' && job.currentStage
    && !events.some((event) => event.stage === job.currentStage)) {
    events.push({
      stage: job.currentStage,
      status: 'RUNNING',
      startedAt: now.toISOString(),
      finishedAt: null,
      elapsedMs: null,
      detail: fallbackDetail('正在执行该步骤，完成后会保存实际参数、样例和耗时。', null),
    })
  }
  return events
}

export function ingestionElapsed(job: IngestionJob, now = new Date()): number | null {
  if (!job.startedAt) return null
  return Math.max(0, new Date(job.finishedAt ?? now).getTime() - new Date(job.startedAt).getTime())
}

function fallbackDetail(summary: string | null, itemCount: number | null): TechnicalStageDetail {
  return { summary: summary || '等待技术详情', inputCount: null, outputCount: itemCount, parameters: {}, metrics: {}, samples: [], raw: {} }
}
