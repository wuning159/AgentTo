import { defineStore } from 'pinia'
import { login, logout, me } from '@/api/rag'
import type { AdminProfile } from '@/types/rag'

const savedUser = localStorage.getItem('rag-admin-user')

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('rag-admin-token') as string | null,
    user: (savedUser ? JSON.parse(savedUser) : null) as AdminProfile | null,
    loading: false,
  }),
  getters: {
    authenticated: (state) => Boolean(state.token && state.user),
  },
  actions: {
    async signIn(username: string, password: string) {
      this.loading = true
      try {
        const session = await login(username, password)
        this.token = session.token
        this.user = session.profile
        localStorage.setItem('rag-admin-token', session.token)
        localStorage.setItem('rag-admin-user', JSON.stringify(session.profile))
      } finally {
        this.loading = false
      }
    },
    async restore() {
      if (!this.token) return false
      try {
        this.user = await me()
        localStorage.setItem('rag-admin-user', JSON.stringify(this.user))
        return true
      } catch {
        this.clear()
        return false
      }
    },
    async signOut() {
      try {
        await logout()
      } finally {
        this.clear()
      }
    },
    clear() {
      this.token = null
      this.user = null
      localStorage.removeItem('rag-admin-token')
      localStorage.removeItem('rag-admin-user')
    },
  },
})
