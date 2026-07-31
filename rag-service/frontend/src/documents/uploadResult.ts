import type { UploadResult } from '@/types/rag'

export function uploadAction(result: UploadResult): 'OPEN_EXISTING' | 'WATCH_JOB' {
  return result.duplicate || result.jobId == null ? 'OPEN_EXISTING' : 'WATCH_JOB'
}
