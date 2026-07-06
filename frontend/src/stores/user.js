import { defineStore } from 'pinia'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { getCurrentUser, logout as logoutApi } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userInfo: null
  }),

  getters: {
    isLoggedIn: state => !!state.token,
    isAdmin: state => Number(state.userInfo?.isAdmin) === 1 || state.userInfo?.username === 'admin',
    isTemporary: state => state.userInfo?.accountType === 'TEMP'
  },

  actions: {
    setToken(token) {
      this.token = token
      setToken(token)
    },

    setUserInfo(userInfo) {
      this.userInfo = userInfo
    },

    async fetchUserInfo() {
      try {
        const res = await getCurrentUser()
        this.userInfo = res.data
        return res.data
      } catch (error) {
        this.clearSession()
        throw error
      }
    },

    clearSession() {
      this.token = ''
      this.userInfo = null
      removeToken()
    },

    async logout() {
      try {
        if (this.token) {
          await logoutApi()
        }
      } catch (error) {
        console.warn('Logout request failed:', error)
      } finally {
        this.clearSession()
      }
    }
  }
})
