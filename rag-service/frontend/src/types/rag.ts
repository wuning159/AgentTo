export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
}

export interface AdminProfile {
  userId: number
  username: string
  displayName: string
}

export interface LoginResult {
  token: string
  expiresAt: string
  profile: AdminProfile
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface DocumentSummary {
  id: number
  name: string
  category: string | null
  sourceType: string
  status: string
  currentVersionId: number | null
  createdAt: string
  updatedAt: string
}

export interface DocumentVersion {
  id: number
  versionNo: number
  filename: string
  contentType: string | null
  fileSize: number
  sha256: string
  processingStatus: string
  chunkCount: number
  indexVersion: string | null
  createdAt: string
}

export interface DocumentDetail {
  document: DocumentSummary
  versions: DocumentVersion[]
}

export interface UploadResult {
  documentId: number
  versionId: number
  jobId: number | null
  objectKey: string
  duplicate: boolean
  message: string
}

export interface ChunkView {
  chunkUid: string
  ordinal: number
  title: string | null
  content: string
  page: number | null
  sectionPath: string | null
  sheetName: string | null
  rowStart: number | null
  rowEnd: number | null
  metadataJson: string | null
}

export interface TechnicalStageDetail {
  summary: string
  inputCount: number | null
  outputCount: number | null
  parameters: Record<string, unknown>
  metrics: Record<string, unknown>
  samples: Array<Record<string, unknown>>
  raw: Record<string, unknown>
}

export interface ExecutionEvent {
  stage: string
  status: 'COMPLETED' | 'DEGRADED' | 'SKIPPED' | 'FAILED' | 'RUNNING'
  startedAt: string
  finishedAt: string | null
  elapsedMs: number | null
  detail: TechnicalStageDetail
}

export interface ExecutionReport {
  historicalSnapshot: boolean
  events: ExecutionEvent[]
}

export interface IngestionStage {
  stage: string
  status: string
  detail: string | null
  itemCount: number | null
  startedAt: string
  finishedAt: string | null
  elapsedMs: number | null
  technicalDetail: TechnicalStageDetail | null
}

export interface IngestionJob {
  id: number
  documentId: number
  versionId: number
  status: string
  currentStage: string | null
  attemptNo: number
  errorCode: string | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  stages: IngestionStage[]
}

export interface RetrievalCandidate {
  chunkId: string
  content: string
  title: string | null
  documentId: number | null
  versionId: number | null
  ordinal: number | null
  metadata: Record<string, string>
  keywordScore: number | null
  keywordRank: number | null
  vectorScore: number | null
  vectorRank: number | null
  rrfScore: number | null
  rrfRank: number | null
  rerankScore: number | null
  rerankRank: number | null
  finalRank: number | null
  contentHash?: string | null
  dedupeStatus?: 'PENDING' | 'KEPT' | 'DUPLICATE' | null
  duplicateOfChunkId?: string | null
}

export interface RetrievalTimings {
  embeddingMs: number
  keywordMs: number
  vectorMs: number
  fusionMs: number
  rerankMs: number
  totalMs: number
}

export interface RetrievalResponse {
  traceUid: string
  candidates: RetrievalCandidate[]
  fallbackReason: string | null
  timings: RetrievalTimings
}

export type RetrievalStageCode = 'PREPROCESS' | 'KEYWORD' | 'EMBEDDING' | 'VECTOR' | 'FUSION' | 'DEDUPE' | 'RERANK' | 'COMPLETE'
export type RetrievalStageStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'DEGRADED' | 'SKIPPED' | 'FAILED'
export type RetrievalJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface RetrievalStageSnapshot {
  stage: RetrievalStageCode
  status: RetrievalStageStatus
  elapsedMs: number | null
  itemCount: number | null
  message: string | null
}

export interface RetrievalJobCreated {
  jobUid: string
}

export interface RetrievalJobSnapshot {
  jobUid: string
  status: RetrievalJobStatus
  currentStage: RetrievalStageCode | null
  stages: RetrievalStageSnapshot[]
  result: RetrievalResponse | null
  error: string | null
  createdAt: string
  completedAt: string | null
}

export interface TraceSummary {
  traceUid: string
  query: string
  retrievalMode: string
  fallbackReason: string | null
  totalMs: number
  resultCount: number
  createdAt: string
}

export interface TraceCandidate {
  chunkId: string
  contentHash: string | null
  dedupeStatus: 'PENDING' | 'KEPT' | 'DUPLICATE' | null
  duplicateOfChunkId: string | null
  title: string | null
  content: string | null
  documentId: number | null
  versionId: number | null
  metadataJson: string | null
  keywordScore: number | null
  keywordRank: number | null
  vectorScore: number | null
  vectorRank: number | null
  rrfScore: number | null
  rrfRank: number | null
  rerankScore: number | null
  rerankRank: number | null
  finalRank: number | null
  selected: boolean
}

export interface TraceDetail extends TraceSummary {
  limits: {
    keywordLimit: number
    vectorLimit: number
    fusionLimit: number
    rerankLimit: number
    finalLimit: number
  }
  rankConstant: number
  deduplicatedCount: number
  timings: RetrievalTimings
  executionReport: ExecutionReport
  candidates: TraceCandidate[]
}

export interface DependencyState {
  name: string
  healthy: boolean
  detail: string
}

export interface DashboardOverview {
  totalDocuments: number
  readyDocuments: number
  processingDocuments: number
  failedDocuments: number
  totalChunks: number
  totalTraces: number
  runningJobs: number
  failedJobs: number
  dependencies: DependencyState[]
}
