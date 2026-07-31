const SENSITIVE_KEY = /(api[-_]?key|token|secret|password|authorization|credential)/i

export function rrfContribution(rank: number | null | undefined, rankConstant: number): number {
  if (rank == null || rank < 1) return 0
  return 1 / (rankConstant + rank)
}

export function eventTone(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'DEGRADED' || status === 'SKIPPED') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

export function eventStatusLabel(status: string): string {
  if (status === 'COMPLETED') return '完成'
  if (status === 'DEGRADED') return '已降级'
  if (status === 'SKIPPED') return '已跳过'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '执行中'
  return status
}

export function redactTechnicalValue(value: unknown, key = ''): unknown {
  if (SENSITIVE_KEY.test(key)) return '***'
  if (Array.isArray(value)) return value.map((item) => redactTechnicalValue(item))
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>)
      .map(([childKey, childValue]) => [childKey, redactTechnicalValue(childValue, childKey)]))
  }
  return value
}

export function prettyTechnicalValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(redactTechnicalValue(value), null, 2)
  return String(value)
}
