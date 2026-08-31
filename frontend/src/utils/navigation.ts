type PermissionChecker = {
  hasPermission: (permission: string) => boolean
}

export const getAccessibleHome = (auth: PermissionChecker) => {
  if (auth.hasPermission('dashboard:view')) return '/dashboard'
  if (auth.hasPermission('model:view')) return '/models'
  if (auth.hasPermission('project:view')) return '/projects'
  if (auth.hasPermission('storage:asset_view') || auth.hasPermission('storage:view')) return '/storage'
  if (
    auth.hasPermission('user:view') ||
    auth.hasPermission('role:view') ||
    auth.hasPermission('registration:view')
  ) return '/organization'
  return '/profile'
}
