import type { RetrievalCandidate, TraceCandidate } from '@/types/rag'

export type RetrievalLimitKey = 'keywordLimit' | 'vectorLimit' | 'fusionLimit' | 'rerankLimit' | 'finalLimit'

export const RETRIEVAL_LIMIT_FIELDS: ReadonlyArray<{
  key: RetrievalLimitKey
  label: string
  help: string
}> = [
  { key: 'keywordLimit', label: '关键词召回数', help: '关键词检索最多保留的候选数量' },
  { key: 'vectorLimit', label: '向量召回数', help: '向量相似度检索最多保留的候选数量' },
  { key: 'fusionLimit', label: '融合候选数', help: '两路结果去重并进行 RRF 融合后保留的数量' },
  { key: 'rerankLimit', label: '精排候选数', help: '发送给 Rerank 模型的候选数量' },
  { key: 'finalLimit', label: '最终结果数', help: '精排完成后最终返回的结果数量' },
]

export function finalTraceCandidates(candidates: TraceCandidate[]): TraceCandidate[] {
  return [...candidates]
    .filter((candidate) => candidate.finalRank != null)
    .sort((left, right) => (left.finalRank ?? Number.MAX_SAFE_INTEGER) - (right.finalRank ?? Number.MAX_SAFE_INTEGER))
}

export function rerankText(candidate: Pick<TraceCandidate, 'rerankScore' | 'rerankRank'>): string {
  if (candidate.rerankScore == null || candidate.rerankRank == null) return '未进入精排'
  return `${candidate.rerankScore.toFixed(4)} · #${candidate.rerankRank}`
}

export function sourceLocation(candidate: Pick<RetrievalCandidate, 'metadata'>): string {
  const metadata = candidate.metadata ?? {}
  const parts: string[] = []
  const append = (value?: string) => {
    if (value && !parts.includes(value)) parts.push(value)
  }
  if (metadata.page) append(`第 ${metadata.page} 页`)
  append(metadata.sheet)
  append(metadata.section)
  if (metadata.rowStart) {
    append(metadata.rowEnd && metadata.rowEnd !== metadata.rowStart
      ? `第 ${metadata.rowStart}-${metadata.rowEnd} 行`
      : `第 ${metadata.rowStart} 行`)
  }
  return parts.join(' · ') || '来源位置未标注'
}
