import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { RetrievalJobSnapshot } from '@/types/rag'
import { startRetrievalJobPolling } from './jobPolling'

function snapshot(status: RetrievalJobSnapshot['status']): RetrievalJobSnapshot {
  return {
    jobUid: 'job-1',
    status,
    currentStage: null,
    stages: [],
    result: null,
    error: status === 'FAILED' ? '检索失败' : null,
    createdAt: '2026-07-16T01:00:00Z',
    completedAt: status === 'RUNNING' || status === 'QUEUED' ? null : '2026-07-16T01:00:03Z',
  }
}

describe('retrieval job polling', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('polls immediately and stops after completion', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce(snapshot('RUNNING'))
      .mockResolvedValueOnce(snapshot('COMPLETED'))
    const onUpdate = vi.fn()
    const onFailure = vi.fn()

    startRetrievalJobPolling(load, onUpdate, onFailure, 500)

    expect(load).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(500)
    expect(load).toHaveBeenCalledTimes(2)
    expect(onUpdate).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'COMPLETED' }))
    await vi.advanceTimersByTimeAsync(1500)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('stops and reports after three consecutive request errors', async () => {
    const load = vi.fn().mockRejectedValue(new Error('network error'))
    const onFailure = vi.fn()

    startRetrievalJobPolling(load, vi.fn(), onFailure, 500)

    await vi.advanceTimersByTimeAsync(1000)
    expect(load).toHaveBeenCalledTimes(3)
    expect(onFailure).toHaveBeenCalledOnce()
    await vi.advanceTimersByTimeAsync(1500)
    expect(load).toHaveBeenCalledTimes(3)
  })

  it('stops immediately when the caller disposes it', async () => {
    const load = vi.fn().mockResolvedValue(snapshot('RUNNING'))
    const stop = startRetrievalJobPolling(load, vi.fn(), vi.fn(), 500)

    stop()
    await vi.advanceTimersByTimeAsync(1500)

    expect(load).toHaveBeenCalledTimes(1)
  })
})
