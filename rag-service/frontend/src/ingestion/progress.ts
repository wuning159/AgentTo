import type { IngestionJob, IngestionStage } from '@/types/rag'

export type IngestionProgressState = 'pending' | 'active' | 'success' | 'failed'

export interface IngestionProgressNode {
  code: string
  label: string
  state: IngestionProgressState
  stage: IngestionStage | null
}

const PIPELINE = [
  ['STORE', '存储'],
  ['PARSE', '解析'],
  ['CLEAN', '清洗'],
  ['CHUNK', '分块'],
  ['EMBED', '向量化'],
  ['INDEX', '索引'],
  ['COMPLETE', '完成'],
] as const

export function ingestionProgress(job: IngestionJob): IngestionProgressNode[] {
  const records = new Map(job.stages.map((stage) => [stage.stage, stage]))
  return PIPELINE.map(([code, label]) => {
    const stage = records.get(code) ?? null
    let state: IngestionProgressState = 'pending'
    if (job.status === 'SUCCEEDED' || stage?.status === 'SUCCEEDED') state = 'success'
    if (job.status === 'RUNNING' && job.currentStage === code && stage?.status !== 'SUCCEEDED') state = 'active'
    if ((job.status === 'FAILED' && job.currentStage === code) || stage?.status === 'FAILED') state = 'failed'
    return { code, label, state, stage }
  })
}
