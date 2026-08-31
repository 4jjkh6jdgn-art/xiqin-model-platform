import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getAccessibleHome } from '@/utils/navigation'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'Register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/components/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '仪表盘', permission: 'dashboard:view' } },
      { path: 'models', name: 'ModelLibrary', component: () => import('@/views/model/ModelLibraryView.vue'), meta: { title: '模型库', breadcrumb: [{ title: '模型管理' }, { title: '模型库', to: '/models' }], permission: 'model:view' } },
      { path: 'models/:id', name: 'ModelDetail', component: () => import('@/views/model/ModelDetailView.vue'), meta: { title: '模型详情', breadcrumb: [{ title: '模型管理' }, { title: '模型库', to: '/models' }, { title: '模型详情' }], permission: 'model:view' } },
      { path: 'models/categories', redirect: '/models/data' },
      { path: 'models/records/uploads', redirect: '/models/data' },
      { path: 'models/records/downloads', redirect: '/models/data' },
      { path: 'models/data', name: 'DataManage', component: () => import('@/views/model/DataManageView.vue'), meta: { title: '数据管理', breadcrumb: [{ title: '模型管理' }, { title: '数据管理' }], permissionsAny: ['model:category_view', 'project:category_view', 'model:process_records_view', 'model:upload_records_view', 'model:download_records_view'] } },
      { path: 'organization', name: 'Organization', component: () => import('@/views/organization/OrganizationView.vue'), meta: { title: '组织管理', permissionsAny: ['user:view', 'role:view', 'registration:view', 'invitation:view', 'invitation:create', 'invitation:revoke'] } },
      { path: 'storage', name: 'StorageLocations', component: () => import('@/views/storage/StorageLocationsView.vue'), meta: { title: '存储空间', permissionsAny: ['storage:asset_view', 'storage:view'] } },
      { path: 'users', redirect: '/organization?tab=users' },
      { path: 'roles', redirect: '/organization?tab=roles' },
      { path: 'registrations', redirect: '/organization?tab=registrations' },
      { path: 'projects', name: 'ProjectList', component: () => import('@/views/project/ProjectListView.vue'), meta: { title: '项目列表', breadcrumb: [{ title: '项目管理' }, { title: '项目列表', to: '/projects' }], permission: 'project:view' } },
      { path: 'projects/:id', name: 'ProjectDetail', component: () => import('@/views/project/ProjectDetailView.vue'), meta: { title: '项目详情', breadcrumb: [{ title: '项目管理' }, { title: '项目列表', to: '/projects' }, { title: '项目详情' }], permission: 'project:view' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人设置' } },
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!authStore.token) {
    next('/login')
    return
  }
  const permission = to.meta.permission as string | undefined
  const permissionsAny = to.meta.permissionsAny as string[] | undefined
  if (permission && !authStore.hasPermission(permission)) {
    const fallback = getAccessibleHome(authStore)
    next(to.path === fallback ? '/profile' : fallback)
    return
  }
  if (permissionsAny?.length && !permissionsAny.some(code => authStore.hasPermission(code))) {
    const fallback = getAccessibleHome(authStore)
    next(to.path === fallback ? '/profile' : fallback)
    return
  }
  next()
})

export default router
