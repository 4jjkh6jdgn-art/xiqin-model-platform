import api from './index'

export const projectApi = {
  getCategories: () => api.get('/projects/categories'),
  createCategory: (data: any) => api.post('/projects/categories', data),
  updateCategory: (id: number, data: any) => api.put(`/projects/categories/${id}`, data),
  deleteCategory: (id: number) => api.delete(`/projects/categories/${id}`),

  getProjects: (params: any) => api.get('/projects', { params }),
  getProject: (id: number) => api.get(`/projects/${id}`),
  createProject: (data: any) => api.post('/projects', data),
  updateProject: (id: number, data: any, changeLog?: string) => api.put(`/projects/${id}`, data, { params: { changeLog } }),
  deleteProject: (id: number) => api.delete(`/projects/${id}`),
  getProjectCover: (id: number) => api.get(`/projects/${id}/cover`, { responseType: 'blob' }),
  updateProjectCover: (id: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post(`/projects/${id}/cover`, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  removeProjectCover: (id: number) => api.delete(`/projects/${id}/cover`),
  getVersions: (projectId: number) => api.get(`/projects/${projectId}/versions`),
  getVersion: (projectId: number, version: number) => api.get(`/projects/${projectId}/versions/${version}`),
  setDefaultVersion: (projectId: number, version: number) => api.put(`/projects/${projectId}/default-version`, { version }),

  // Members
  getMembers: (projectId: number) => api.get(`/projects/${projectId}/members`),
  getMemberCandidates: (projectId: number, keyword?: string) => api.get(`/projects/${projectId}/member-candidates`, { params: { keyword } }),
  addMember: (projectId: number, data: any) => api.post(`/projects/${projectId}/members`, data),
  removeMember: (projectId: number, userId: number) => api.delete(`/projects/${projectId}/members/${userId}`),

  // Tasks
  getTasks: (projectId: number) => api.get(`/projects/${projectId}/tasks`),
  createTask: (projectId: number, data: any) => api.post(`/projects/${projectId}/tasks`, data),
  updateTask: (id: number, data: any) => api.put(`/projects/tasks/${id}`, data),
  deleteTask: (id: number) => api.delete(`/projects/tasks/${id}`),

  // Phases
  getPhases: (projectId: number) => api.get(`/projects/${projectId}/phases`),
  createPhase: (projectId: number, data: any) => api.post(`/projects/${projectId}/phases`, data),

  // Files
  getFiles: (projectId: number) => api.get(`/projects/${projectId}/files`),
  getFolders: (projectId: number) => api.get(`/projects/${projectId}/folders`),
  createFolder: (projectId: number, data: any) => api.post(`/projects/${projectId}/folders`, data),
  uploadFile: (projectId: number, formData: FormData) => api.post(`/projects/${projectId}/files`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  uploadFolder: (projectId: number, formData: FormData) => api.post(`/projects/${projectId}/folders/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }, timeout: 0
  }),
  moveFile: (fileId: number, folderId: number | null) => api.put(`/projects/files/${fileId}/folder`, { folderId }),
  getFileActivities: (fileId: number, keyword?: string) => api.get(`/projects/files/${fileId}/activities`, { params: { keyword } }),
  previewFile: (fileId: number) => api.get(`/projects/files/${fileId}/preview`, { responseType: 'blob' }),
  downloadFile: (fileId: number, record = true) => api.get(`/projects/files/${fileId}/download`, { params: { record }, responseType: 'blob' }),

  // Inline content editing
  getFileContent: (fileId: number) => api.get(`/projects/files/${fileId}/content`),
  saveFileContent: (fileId: number, content: string) => api.put(`/projects/files/${fileId}/content`, { content }),

  // OnlyOffice online editing
  getOfficeConfig: (fileId: number, mode: 'view' | 'edit' = 'edit') =>
    api.get(`/projects/files/${fileId}/office-config`, { params: { mode } }),

  // Assets
  getAssets: (projectId: number, type?: string) => api.get(`/projects/${projectId}/assets`, { params: { type } }),
}
