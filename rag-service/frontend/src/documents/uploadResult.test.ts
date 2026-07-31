import { describe, expect, it } from 'vitest'
import { uploadAction } from './uploadResult'

describe('uploadAction', () => {
  it('opens the existing document for an exact duplicate', () => {
    expect(uploadAction({
      documentId: 1,
      versionId: 2,
      jobId: null,
      objectKey: 'manual/existing.docx',
      duplicate: true,
      message: '该文件已经入库',
    })).toBe('OPEN_EXISTING')
  })

  it('watches ingestion for a new upload', () => {
    expect(uploadAction({
      documentId: 1,
      versionId: 2,
      jobId: 3,
      objectKey: 'manual/new.docx',
      duplicate: false,
      message: '文件已进入处理队列',
    })).toBe('WATCH_JOB')
  })
})
