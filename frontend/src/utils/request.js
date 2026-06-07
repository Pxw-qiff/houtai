import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：注入积分系统登录态
request.interceptors.request.use(
  (config) => {
    const token = window.localStorage.getItem('chuamgwei_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：解包 Result { code, message, data }
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res === undefined || res === null) return response

    if (res.code === 200) {
      return res.data !== undefined ? res.data : res
    }

    // 令牌过期或未授权，跳转到登录页
    if (res.code === 401) {
      ElMessage.error('登录已过期，请重新登录')
      window.localStorage.removeItem('chuamgwei_token')
      router.push('/login')
      return Promise.reject(new Error('未授权'))
    }

    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // HTTP 401 状态码也触发跳转
    if (error.response?.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      window.localStorage.removeItem('chuamgwei_token')
      router.push('/login')
      return Promise.reject(error)
    }

    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request