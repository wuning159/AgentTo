import axios from 'axios'
import type { ApiResponse } from '@/types/rag'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 60_000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('rag-admin-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('rag-admin-token')
      localStorage.removeItem('rag-admin-user')
      if (location.pathname !== '/login') location.assign('/login')
    }
    return Promise.reject(error)
  },
)

export function unwrap<T>(response: { data: ApiResponse<T> }): T {
  if (response.data.code !== 'OK') throw new Error(response.data.message)
  return response.data.data
}

export function errorMessage(error: unknown, fallback = '请求失败'): string {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return error.response?.data?.message || error.message || fallback
  }
  return error instanceof Error ? error.message : fallback
}

export default http
