import { del, get, post, put } from '@/api/request'

export interface PageResult<T> {
  records: T[]
  page: number
  size: number
  total: number
  pages: number
}

export interface ProductItem {
  id: number
  name: string
  manufacturer: string
  model: string
  remark: string
  createdAt: string
  createdBy: number
  updatedAt: string
  updatedBy: number
}

export interface ProductPayload {
  name: string
  manufacturer: string
  model: string
  remark?: string
}

export const pageProducts = (params: Record<string, unknown>) => {
  return get<PageResult<ProductItem>>('/admin/products', { params })
}

export const getProductsByIds = (ids: number[]) => {
  if (ids.length > 200) {
    throw new Error('产品 ID 数量不能超过 200 个')
  }
  return get<ProductItem[]>('/admin/products/by-ids', {
    params: { ids: ids.join(',') }
  })
}

export const searchProducts = (keyword: string) => {
  return get<PageResult<ProductItem>>('/admin/products', {
    params: { page: 1, size: 20, keyword }
  })
}

export const getProductById = (id: number) => {
  return get<ProductItem>(`/admin/products/${id}`)
}

export const createProduct = (payload: ProductPayload) => {
  return post<ProductItem>('/admin/products', payload)
}

export const updateProduct = (id: number, payload: ProductPayload) => {
  return put<ProductItem>(`/admin/products/${id}`, payload)
}

export const deleteProduct = (id: number) => {
  return del<void>(`/admin/products/${id}`)
}
