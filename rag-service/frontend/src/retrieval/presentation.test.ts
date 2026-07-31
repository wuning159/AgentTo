import { describe, expect, it } from 'vitest'
import type { TraceCandidate } from '@/types/rag'
import { finalTraceCandidates, rerankText, sourceLocation } from './presentation'

function traceCandidate(finalRank: number | null, rerankRank: number | null): TraceCandidate {
  return {
    chunkId: `chunk-${finalRank ?? 'none'}-${rerankRank ?? 'none'}`,
    contentHash: null,
    dedupeStatus: null,
    duplicateOfChunkId: null,
    title: null,
    content: '测试内容',
    documentId: 1,
    versionId: 1,
    metadataJson: null,
    keywordScore: null,
    keywordRank: null,
    vectorScore: null,
    vectorRank: null,
    rrfScore: 0.03,
    rrfRank: 1,
    rerankScore: rerankRank == null ? null : 0.9,
    rerankRank,
    finalRank,
    selected: finalRank != null,
  }
}

describe('retrieval presentation', () => {
  it('keeps only final trace candidates and sorts them by final rank', () => {
    const values = [traceCandidate(null, null), traceCandidate(2, 2), traceCandidate(1, 1)]

    expect(finalTraceCandidates(values).map((value) => value.finalRank)).toEqual([1, 2])
  })

  it('labels a fused candidate that did not enter rerank', () => {
    expect(rerankText(traceCandidate(null, null))).toBe('未进入精排')
    expect(rerankText(traceCandidate(1, 1))).toBe('0.9000 · #1')
  })

  it('formats source locations from chunk metadata', () => {
    const pdf = { metadata: { page: '3', section: '审批管理' } }
    const excel = { metadata: { sheet: '预算表', rowStart: '2', rowEnd: '4' } }

    expect(sourceLocation(pdf)).toBe('第 3 页 · 审批管理')
    expect(sourceLocation(excel)).toBe('预算表 · 第 2-4 行')
  })

  it('does not repeat a pdf page when section metadata contains the same page label', () => {
    expect(sourceLocation({ metadata: { page: '6', section: '第 6 页' } })).toBe('第 6 页')
  })
})
