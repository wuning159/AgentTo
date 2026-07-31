import { describe, expect, it } from 'vitest'
import type { IngestionJob } from '@/types/rag'
import { ingestionElapsed, ingestionExecutionEvents } from './executionReport'

describe('ingestion execution report', () => {
  it('adds the currently running stage when it has not been persisted yet', () => {
    const job = baseJob()
    job.status = 'RUNNING'
    job.currentStage = 'EMBED'
    expect(ingestionExecutionEvents(job, new Date('2026-07-16T01:00:02Z')).at(-1))
      .toMatchObject({ stage: 'EMBED', status: 'RUNNING' })
  })

  it('calculates elapsed time from the persisted timestamps', () => {
    const job = baseJob()
    job.startedAt = '2026-07-16T01:00:00Z'
    job.finishedAt = '2026-07-16T01:00:02Z'
    expect(ingestionElapsed(job)).toBe(2000)
  })
})

function baseJob(): IngestionJob {
  return {
    id: 1, documentId: 1, versionId: 1, status: 'QUEUED', currentStage: null, attemptNo: 1,
    errorCode: null, errorMessage: null, startedAt: null, finishedAt: null,
    createdAt: '2026-07-16T01:00:00Z', stages: [],
  }
}
