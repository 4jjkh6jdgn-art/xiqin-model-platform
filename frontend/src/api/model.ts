import api from './index'

export const modelApi = {
  // Categories
  getCategories: () => api.get('/models/categories'),
  createCategory: (data: any) => api.post('/models/categories', data),
  updateCategory: (id: number, data: any) => api.put(`/models/categories/${id}`, data),
  deleteCategory: (id: number) => api.delete(`/models/categories/${id}`),

  // Models
  getModels: (params: any) => api.get('/models', { params }),
  getLibraryStats: () => api.get('/models/library-stats'),
  getModel: (id: number, version?: number) => api.get(`/models/${id}`, {
    params: version ? { version } : undefined
  }),
  createModel: (data: any) => api.post('/models', data),
  updateModel: (id: number, data: any) => api.put(`/models/${id}`, data),
  deleteModel: (id: number) => api.delete(`/models/${id}`),
  linkProject: (id: number, projectId: number) => api.put(`/models/${id}/projects/${projectId}`),
  unlinkProject: (id: number, projectId: number) => api.delete(`/models/${id}/projects/${projectId}`),

  // Upload
  uploadFolder: (formData: FormData, config: any) => api.post('/models/upload-folder', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000, // 5 min for large uploads
    ...config
  }),

  // Download
  downloadModel: (id: number) => api.post(`/models/${id}/download`),
  downloadModelArchive: (id: number, version: number) => api.get(`/models/${id}/versions/${version}/download.zip`, {
    responseType: 'blob',
    timeout: 600000
  }),
  downloadFile: (fileId: number) => api.post(`/models/files/${fileId}/download`),
  renameFile: (fileId: number, fileName: string) => api.put(`/models/files/${fileId}`, { fileName }),
  deleteFile: (fileId: number) => api.delete(`/models/files/${fileId}`),

  // File search
  searchFiles: (keyword: string) => api.get('/models/files/search', { params: { keyword } }),

  // Versions
  getVersions: (id: number) => api.get(`/models/${id}/versions`),
  setDefaultVersion: (id: number, version: number) => api.put(`/models/${id}/default-version`, { version }),
  createVersion: (id: number, changeLog: string) => api.post(`/models/${id}/versions`, { changeLog }),
  uploadVersion: (id: number, formData: FormData, config: any = {}) => api.post(`/models/${id}/versions/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000,
    ...config
  }),
  addVersionFiles: (id: number, version: number, formData: FormData, config: any = {}) => api.post(`/models/${id}/versions/${version}/files`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000,
    ...config
  }),

  // Scene config (camera view + lighting)
  updateSceneConfig: (id: number, data: { cameraView?: string; lighting?: string }) =>
    api.put(`/models/${id}/scene-config`, data),

  // Thumbnail (user-uploaded)
  uploadThumbnail: (id: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post(`/models/${id}/thumbnail`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  selectThumbnail: (id: number, fileId: number) => api.put(`/models/${id}/thumbnail/${fileId}`),
  deleteThumbnail: (id: number, fileId: number) => api.delete(`/models/${id}/thumbnail/${fileId}`),

  // Records
  getProcessingRecords: (params: any) => api.get('/models/records/processing', { params }),
  getUploadRecords: (params: any) => api.get('/models/records/uploads', { params }),
  getDownloadRecords: (params: any) => api.get('/models/records/downloads', { params }),
  getModificationRecords: (id: number, params: any) => api.get(`/models/${id}/records/modifications`, { params }),
}
