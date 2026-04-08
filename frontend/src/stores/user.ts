import { defineStore } from 'pinia'
import { loginApi, meApi } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({ token: localStorage.getItem('token') || '', profile: null as any }),
  actions: {
    async login(username: string, password: string) {
      const res = await loginApi(username, password)
      this.token = res.data.data.access_token
      localStorage.setItem('token', this.token)
      await this.fetchMe()
    },
    async fetchMe() {
      const res = await meApi()
      this.profile = res.data.data
    },
    logout() {
      this.token = ''
      this.profile = null
      localStorage.removeItem('token')
    }
  }
})
