import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'
import router from '@/router'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 20000
})

request.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    const payload = response.data
    if (payload.code !== 200) {
      if (payload.code === 403) {
        router.push('/401')
      }
      ElMessage.error(payload.message || '请求失败，请稍后重试')
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return payload
  },
  error => {
    if (error.response?.status === 401) {
      removeToken()
      ElMessage.error('登录状态已失效，请重新登录')
      router.push('/login')
    } else if (error.response?.status === 403 || error.response?.data?.code === 403) {
      ElMessage.error(error.response?.data?.message || '没有访问权限')
      router.push('/401')
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络请求失败')
    }
    return Promise.reject(error)
  }
)

export default request
