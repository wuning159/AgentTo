import { describe, expect, it } from 'vitest'
import type { RetrievalJobSnapshot, RetrievalStageSnapshot } from '@/types/rag'
import { formatStageDetail, retrievalProgress } from './progress'

function stage(
  code: RetrievalStageSnapshot['stage'],
  status: RetrievalStageSnapshot['status'],
  elapsedMs: number | null = null,
  itemCount: number | null = null,
  message: string | null = null,
): RetrievalStageSnapshot {
  return { stage: code, status, elapsedMs, itemCount, message }
}

function job(stages: RetrievalStageSnapshot[]): RetrievalJobSnapshot {
  return {
    jobUid: 'job-1',
    status: 'RUNNING',
    currentStage: 'FUSION',
    stages,
    result: null,
    error: null,
    createdAt: '2026-07-16T01:00:00Z',
    completedAt: null,
  }
}

describe('retrieval progress', () => {
  it('always returns the fixed eight-stage pipeline', () => {
    const result = retrievalProgress(null)

    expect(result.map((item) => item.code)).toEqual([
      'PREPROCESS', 'KEYWORD', 'EMBEDDING', 'VECTOR', 'FUSION', 'DEDUPE', 'RERANK', 'COMPLETE',
    ])
    expect(result.every((item) => item.state === 'pending')).toBe(true)
  })

  it('maps completed, active, degraded and skipped states', () => {
    const result = retrievalProgress(job([
      stage('KEYWORD', 'COMPLETED', 86, 20),
      stage('EMBEDDING', 'DEGRADED', 315, null, '模型不可用'),
      stage('VECTOR', 'SKIPPED', null, null, '已跳过向量召回'),
      stage('FUSION', 'RUNNING'),
    ]))

    expect(result.map((item) => item.state)).toEqual([
      'pending', 'success', 'degraded', 'skipped', 'active', 'pending', 'pending', 'pending',
    ])
  })

  it('formats counts and short or long elapsed time', () => {
    expect(formatStageDetail(stage('KEYWORD', 'COMPLETED', 86, 20))).toBe('20 条 / 86 ms')
    expect(formatStageDetail(stage('RERANK', 'COMPLETED', 2820, 15))).toBe('15 条 / 2.82 s')
    expect(formatStageDetail(stage('EMBEDDING', 'COMPLETED', 315))).toBe('315 ms')
  })

  it('keeps degradation and skipped reasons visible', () => {
    expect(formatStageDetail(stage('EMBEDDING', 'DEGRADED', 315, null, '模型不可用')))
      .toBe('315 ms · 模型不可用')
    expect(formatStageDetail(stage('VECTOR', 'SKIPPED', null, null, '已跳过向量召回')))
      .toBe('已跳过向量召回')
  })
})
