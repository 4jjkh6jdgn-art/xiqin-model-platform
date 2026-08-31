import api from './index'

export const userApi = {
  getUsers: (params: any) => api.get('/users', { params }),
  createUser: (data: any) => api.post('/users', data),
  batchCreateUsers: (data: any) => api.post('/users/batch', data),
  getUser: (id: number) => api.get(`/users/${id}`),
  updateUser: (id: number, data: any) => api.put(`/users/${id}`, data),
  updateUserStatus: (id: number, status: number) => api.put(`/users/${id}/status`, { status }),
  deleteUser: (id: number) => api.delete(`/users/${id}`),
  updateProfile: (data: any) => api.put('/users/profile', data),
  getCurrentUser: () => api.get('/users/me'),
}

export const roleApi = {
  getRoles: () => api.get('/roles'),
  getRole: (id: number) => api.get(`/roles/${id}`),
  getPermissions: () => api.get('/roles/permissions'),
  createRole: (data: any) => api.post('/roles', data),
  updateRole: (id: number, data: any) => api.put(`/roles/${id}`, data),
  deleteRole: (id: number) => api.delete(`/roles/${id}`),
  assignPermissions: (id: number, permissionIds: number[]) => api.put(`/roles/${id}/permissions`, { permissionIds }),
}

export const authApi = {
  generateInvitationCode: () => api.post('/auth/invitation/generate'),
  validateInvitationCode: (code: string) => api.get(`/auth/invitation/validate`, { params: { code } }),
  getInvitationCodes: (params: any) => api.get('/auth/invitations', { params }),
  getInvitationSummary: () => api.get('/auth/invitations/summary'),
  revokeInvitationCode: (id: number) => api.post(`/auth/invitations/${id}/revoke`),
  getRegistrationRequests: (params: any) => api.get('/auth/registration-requests', { params }),
  reviewRegistration: (id: number, data: any) => api.post(`/auth/registration-requests/${id}/review`, data),
}

export const notificationApi = {
  getMessages: (params: any = {}) => api.get('/notifications', { params }),
  getUnreadCount: () => api.get('/notifications/unread-count'),
  markRead: (id: number) => api.put(`/notifications/${id}/read`),
  markAllRead: () => api.put('/notifications/read-all'),
  createFeedback: (data: any) => api.post('/notifications/feedback', data),
  createReminder: (data: any) => api.post('/notifications/reminders', data),
}

export const storageLocationApi = {
  list: () => api.get('/storage-locations'),
  create: (data: any) => api.post('/storage-locations', data),
  update: (id: number, data: any) => api.put(`/storage-locations/${id}`, data),
  activate: (id: number) => api.post(`/storage-locations/${id}/activate`),
  test: (id: number) => api.post(`/storage-locations/${id}/test`),
  scan: (id: number) => api.post(`/storage-locations/${id}/scan`),
  delete: (id: number) => api.delete(`/storage-locations/${id}`),
}
