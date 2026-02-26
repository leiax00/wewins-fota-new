import axios, { type AxiosInstance, type AxiosResponse, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/locales'
import { useUserStore } from '@/stores/user'
import { getToken, clearToken } from '@/utils/auth'
import { ERROR_CODES, isTokenInvalidError, isAuthError } from '@/constants/errorCodes'

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

const getErrorMessage = (code: number | string, backendMessage?: string): string => {
  try {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const messages = i18n.global.messages.value[i18n.global.locale.value] as Record<string, any>
    const errorMessages = messages?.error
    if (errorMessages && errorMessages[String(code)]) {
      return errorMessages[String(code)]
    }
  } catch {
    // ignore
  }
  return backendMessage || 'Unknown error'
}

const handleAuthFailure = () => {
  const currentRoute = router.currentRoute.value
  const redirectPath = currentRoute.path === '/login' ? '' : currentRoute.fullPath

  try {
    const userStore = useUserStore()
    userStore.clearAuth({
      redirect: currentRoute.path !== '/login',
      redirectPath,
    })
  } catch {
    clearToken()
    if (currentRoute.path !== '/login') {
      router.push({
        path: '/login',
        query: redirectPath ? { redirect: redirectPath } : undefined,
      })
    }
  }
}

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data } = response
    if (data.code === ERROR_CODES.SUCCESS || data.code === ERROR_CODES.SUCCESS_ALT) {
      return data.data
    }

    const message = getErrorMessage(data.code, data.message)
    ElMessage.error(message)

    if (isTokenInvalidError(data.code)) {
      handleAuthFailure()
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const error = new Error(message) as any
    error.code = data.code
    return Promise.reject(error)
  },
  (error) => {
    // 请求被取消（如用户点击取消上传按钮），不显示错误提示
    if (axios.isCancel(error) || error.code === 'ERR_CANCELED') {
      return Promise.reject(error)
    }

    if (error.response) {
      const { status, data } = error.response
      const code = data?.code
      const message = code
        ? getErrorMessage(code, data?.message)
        : getErrorMessage(status === ERROR_CODES.UNAUTHORIZED ? 'tokenExpired' : ERROR_CODES.UNKNOWN, data?.message)

      ElMessage.error(message)

      if (isAuthError(status) || isAuthError(code)) {
        handleAuthFailure()
      }
    } else {
      ElMessage.error(getErrorMessage(ERROR_CODES.NETWORK_ERROR))
    }

    return Promise.reject(error)
  }
)

export const get = <T>(url: string, config?: AxiosRequestConfig): Promise<T> => {
  return request.get(url, config)
}

export const post = <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> => {
  return request.post(url, data, config)
}

export const put = <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> => {
  return request.put(url, data, config)
}

export const del = <T>(url: string, config?: AxiosRequestConfig): Promise<T> => {
  return request.delete(url, config)
}

export default request
