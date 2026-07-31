import http, { unwrap } from './http'
import type {
  AdminProfile,
  ChunkView,
  DashboardOverview,
  DocumentDetail,
  DocumentSummary,
  IngestionJob,
  LoginResult,
  PageResult,
  RetrievalResponse,
  RetrievalJobCreated,
  RetrievalJobSnapshot,
  TraceDetail,
  TraceSummary,
  UploadResult,
} from '@/types/rag'

export async function login(username: string, password: string): Promise<LoginResult> {
  return unwrap(await http.post('/auth/login', { username, password }))
}

export async function me(): Promise<AdminProfile> {
  return unwrap(await http.get('/auth/me'))
}

export async function logout(): Promise<void> {
  unwrap(await http.post('/auth/logout'))
}

export async function dashboard(): Promise<DashboardOverview> {
  return unwrap(await http.get('/admin/dashboard'))
}

export async function documents(page = 0, size = 20): Promise<PageResult<DocumentSummary>> {
  return unwrap(await http.get('/admin/documents', { params: { page, size } }))
}

export async function documentDetail(id: number): Promise<DocumentDetail> {
  return unwrap(await http.get(`/admin/documents/${id}`))
}

export async function uploadDocument(file: File, category?: string): Promise<UploadResult> {
  const form = new FormData()
  form.append('file', file)
  if (category) form.append('category', category)
  return unwrap(await http.post('/admin/documents', form))
}

export async function chunks(versionId: number, page = 0, size = 20): Promise<PageResult<ChunkView>> {
  return unwrap(await http.get(`/admin/versions/${versionId}/chunks`, { params: { page, size } }))
}

export async function ingestionJob(jobId: number): Promise<IngestionJob> {
  return unwrap(await http.get(`/admin/ingestion/jobs/${jobId}`))
}

export async function latestVersionIngestion(versionId: number): Promise<IngestionJob> {
  return unwrap(await http.get(`/admin/versions/${versionId}/ingestion`))
}

export async function search(query: string, limits?: Record<string, number>): Promise<RetrievalResponse> {
  return unwrap(await http.post('/admin/retrieval/search', { query, ...limits }))
}

export async function createRetrievalJob(
  query: string,
  limits?: Record<string, number>,
): Promise<RetrievalJobCreated> {
  return unwrap(await http.post('/admin/retrieval/jobs', { query, ...limits }))
}

export async function retrievalJob(jobUid: string): Promise<RetrievalJobSnapshot> {
  return unwrap(await http.get(`/admin/retrieval/jobs/${jobUid}`))
}

export async function traces(limit = 30): Promise<TraceSummary[]> {
  return unwrap(await http.get('/admin/retrieval/traces', { params: { limit } }))
}

export async function traceDetail(traceUid: string): Promise<TraceDetail> {
  return unwrap(await http.get(`/admin/retrieval/traces/${traceUid}`))
}
