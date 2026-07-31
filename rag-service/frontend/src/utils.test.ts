import { describe, expect, it } from 'vitest'
import { formatBytes, score, statusLabel, statusType } from './utils'

describe('RAG 管理台格式化工具', () => {
  it('formats file sizes and scores for operators', () => {
    expect(formatBytes(1536)).toBe('1.5 KB')
    expect(score(0.123456)).toBe('0.1235')
    expect(score(null)).toBe('—')
  })

  it('maps backend states to visible labels', () => {
    expect(statusLabel('READY')).toBe('可检索')
    expect(statusType('PROCESSING')).toBe('warning')
    expect(statusType('FAILED')).toBe('danger')
  })
})
