export function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

export function formatTime(value?: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

export function score(value?: number | null): string {
  return value == null ? '—' : value.toFixed(4)
}

export function statusLabel(value: string): string {
  return ({
    READY: '可检索', PROCESSING: '处理中', QUEUED: '等待处理', RUNNING: '运行中',
    SUCCEEDED: '已完成', FAILED: '失败', COMPLETE: '完成',
  } as Record<string, string>)[value] ?? value
}

export function statusType(value: string): 'success' | 'warning' | 'danger' | 'info' {
  if (['READY', 'SUCCEEDED', 'COMPLETE'].includes(value)) return 'success'
  if (['PROCESSING', 'QUEUED', 'RUNNING'].includes(value)) return 'warning'
  if (value === 'FAILED') return 'danger'
  return 'info'
}
