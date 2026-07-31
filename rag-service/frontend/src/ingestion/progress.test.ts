import { describe, expect, it } from 'vitest'
import type { IngestionJob, IngestionStage } from '@/types/rag'
import { ingestionProgress } from './progress'

function stage(stage: string, status = 'SUCCEEDED'): IngestionStage {
  return {
    stage,
    status,
    detail: `${stage}完成`,
    itemCount: 1,
    startedAt: '2026-07-15T08:00:00Z',
    finishedAt: '2026-07-15T08:00:01Z',
    elapsedMs: 1000,
    technicalDetail: null,
  }
}

function job(status: string, currentStage: string | null, stages: IngestionStage[] = []): IngestionJob {
  return {
    id: 1,
    documentId: 1,
    versionId: 1,
    status,
    currentStage,
    attemptNo: 1,
    errorCode: null,
    errorMessage: status === 'FAILED' ? '处理失败' : null,
    startedAt: null,
    finishedAt: null,
    createdAt: '2026-07-15T08:00:00Z',
    stages,
  }
}

describe('ingestion progress', () => {
  it('always returns the complete seven-stage pipeline for a queued job', () => {
    const result = ingestionProgress(job('QUEUED', null))

    expect(result.map((item) => item.code)).toEqual(['STORE', 'PARSE', 'CLEAN', 'CHUNK', 'EMBED', 'INDEX', 'COMPLETE'])
    expect(result.every((item) => item.state === 'pending')).toBe(true)
  })

  it('marks completed stages and the current running stage', () => {
    const result = ingestionProgress(job('RUNNING', 'CHUNK', [stage('STORE'), stage('PARSE'), stage('CLEAN')]))

    expect(result.map((item) => item.state)).toEqual(['success', 'success', 'success', 'active', 'pending', 'pending', 'pending'])
  })

  it('marks the failed current stage without completing later stages', () => {
    const result = ingestionProgress(job('FAILED', 'EMBED', [stage('STORE'), stage('PARSE'), stage('CLEAN'), stage('CHUNK')]))

    expect(result.map((item) => item.state)).toEqual(['success', 'success', 'success', 'success', 'failed', 'pending', 'pending'])
  })

  it('marks every stage complete after a successful job', () => {
    const result = ingestionProgress(job('SUCCEEDED', 'COMPLETE', [stage('STORE'), stage('PARSE'), stage('CLEAN'), stage('CHUNK'), stage('EMBED'), stage('INDEX'), stage('COMPLETE')]))

    expect(result.every((item) => item.state === 'success')).toBe(true)
  })
})
