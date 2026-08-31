<template>
  <div class="page-container dashboard-page">
    <el-card class="welcome-banner" shadow="never">
      <div class="welcome-content">
        <div>
          <div class="eyebrow">工作台概览</div>
          <div class="welcome-title">
            你好，{{ authStore.user?.username || '用户' }}
            <el-tag size="small" effect="plain" class="role-tag">{{ authStore.user?.roleName || '平台成员' }}</el-tag>
          </div>
          <div class="welcome-sub">模型资产与项目进展集中呈现，关键变化和系统状态随时可见。</div>
        </div>
        <div class="welcome-meta">
          <div class="health-pill" :class="{ warning: failedLoads > 0 }">
            <span class="health-dot"></span>
            {{ failedLoads > 0 ? '部分数据待刷新' : '数据服务正常' }}
          </div>
          <span>更新于 {{ lastRefreshed || '--:--' }}</span>
          <el-button circle :loading="refreshing" aria-label="刷新仪表盘" @click="loadDashboard(true)">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </div>
    </el-card>

    <section class="metrics-grid" aria-label="关键指标">
      <el-card v-for="stat in visibleStats" :key="stat.title" class="stat-card" shadow="never">
        <div class="stat-content">
          <div>
            <div class="stat-title">{{ stat.title }}</div>
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-hint">{{ stat.hint }}</div>
          </div>
          <div class="stat-icon" :style="{ background: stat.bg, color: stat.color }">
            <el-icon :size="23"><component :is="stat.icon" /></el-icon>
          </div>
        </div>
      </el-card>
    </section>

    <el-card class="dashboard-card system-overview" shadow="never">
      <template #header>
        <div class="section-heading">
          <div><strong>系统运行概览</strong><span>关键资产指标与管理入口</span></div>
          <span class="system-time">最后同步 {{ lastRefreshed || '--:--' }}</span>
        </div>
      </template>
      <div class="system-grid">
        <div class="system-item"><span class="system-icon healthy"><el-icon><Connection /></el-icon></span><div><small>数据服务</small><strong>{{ failedLoads > 0 ? '部分可用' : '运行正常' }}</strong></div><i class="state-dot" :class="{ warning: failedLoads > 0 }"></i></div>
        <button v-if="canAssetOverview" class="system-item system-link" type="button" @click="openSystemPanel('storage')"><span class="system-icon storage"><el-icon><Coin /></el-icon></span><div><small>资产存储</small><strong>{{ formatSize(storageBytes) }}</strong></div><span class="system-note">{{ modelTotal }} 个模型</span></button>
        <button v-if="canProcessingRecords" class="system-item system-link" type="button" @click="openSystemPanel('processing')"><span class="system-icon process"><el-icon><Cpu /></el-icon></span><div><small>处理队列</small><strong>{{ processingModels }} 个处理中</strong></div><span class="system-note">失败 {{ failedModels }}</span></button>
        <button v-if="canUsers" class="system-item system-link" type="button" @click="openSystemPanel('users')"><span class="system-icon team"><el-icon><User /></el-icon></span><div><small>协作规模</small><strong>{{ memberTotal }} 位成员</strong></div><span class="system-note">{{ projectTotal }} 个项目</span></button>
        <button v-if="canDownloadRecords" class="system-item system-link" type="button" @click="openSystemPanel('downloads')"><span class="system-icon downloads"><el-icon><Download /></el-icon></span><div><small>资产下载</small><strong>{{ downloadTotal }} 次</strong></div><span class="system-note">查看记录</span></button>
      </div>
    </el-card>

    <section v-if="canProjects || canModels" class="dashboard-grid chart-grid">
      <el-card v-if="canProjects" class="dashboard-card project-status-card" shadow="never">
        <template #header>
          <div class="section-heading">
            <div><strong>项目状态分布</strong><span>当前在管项目构成</span></div>
            <el-link type="primary" @click="$router.push('/projects')">查看项目</el-link>
          </div>
        </template>
        <div v-loading="loading" class="project-status-body">
          <div class="donut-wrap" aria-label="项目状态饼图">
            <div class="donut" :style="{ background: projectPieGradient }">
              <div class="donut-core"><strong>{{ projectTotal }}</strong><span>项目总数</span></div>
            </div>
          </div>
          <div class="legend-list">
            <button v-for="item in projectStatusDist" :key="item.key" class="legend-row" type="button" @click="$router.push({ path: '/projects', query: { status: item.key } })">
              <span class="legend-dot" :style="{ background: item.color }"></span>
              <span class="legend-name">{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
              <span class="legend-percent">{{ item.percent }}%</span>
            </button>
          </div>
        </div>
      </el-card>

      <el-card v-if="canModels" class="dashboard-card model-trend-card" shadow="never">
        <template #header>
          <div class="section-heading">
            <div><strong>模型入库趋势</strong><span>近 7 天新增模型数量</span></div>
            <div class="chart-total"><span></span>本周新增 {{ weeklyModelTotal }}</div>
          </div>
        </template>
        <div v-loading="loading" class="trend-chart">
          <svg viewBox="0 0 680 216" preserveAspectRatio="none" role="img" aria-label="近七天模型入库折线图">
            <defs>
              <linearGradient id="trendArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#36a27a" stop-opacity="0.24" />
                <stop offset="100%" stop-color="#36a27a" stop-opacity="0" />
              </linearGradient>
            </defs>
            <line v-for="y in [32, 76, 120, 164]" :key="y" x1="34" x2="658" :y1="y" :y2="y" class="chart-grid-line" />
            <polygon :points="trendAreaPoints" fill="url(#trendArea)" />
            <polyline :points="trendLinePoints" class="trend-line" />
            <g v-for="point in trendPoints" :key="point.date">
              <circle :cx="point.x" :cy="point.y" r="5" class="trend-point" />
              <text :x="point.x" y="202" text-anchor="middle" class="axis-label">{{ point.label }}</text>
              <text v-if="point.count > 0" :x="point.x" :y="point.y - 12" text-anchor="middle" class="point-value">{{ point.count }}</text>
            </g>
          </svg>
          <el-empty v-if="!loading && models.length === 0" description="暂无模型趋势数据" :image-size="54" />
        </div>
      </el-card>
    </section>

    <section v-if="canModels || canProjects" class="dashboard-grid distribution-grid">
      <el-card v-if="canModels" class="dashboard-card" shadow="never">
        <template #header>
          <div class="section-heading">
            <div><strong>模型分类分布</strong><span>资产类型与数量</span></div>
            <el-link v-if="canCategory" type="primary" @click="$router.push('/models/data')">管理分类</el-link>
          </div>
        </template>
        <div v-loading="loading" class="distribution-list">
          <button v-for="item in modelCategoryDist" :key="item.name" type="button" class="distribution-row" @click="$router.push('/models')">
            <span class="distribution-label"><i :style="{ background: item.color }"></i>{{ item.name }}</span>
            <span class="distribution-track"><i :style="{ width: `${item.percent}%`, background: item.color }"></i></span>
            <strong>{{ item.count }}</strong>
          </button>
          <el-empty v-if="!loading && modelCategoryDist.length === 0" description="暂无模型分类数据" :image-size="54" />
        </div>
      </el-card>

      <el-card v-if="canProjects" class="dashboard-card" shadow="never">
        <template #header>
          <div class="section-heading">
            <div><strong>项目分类分布</strong><span>项目方向与占比</span></div>
            <el-link type="primary" @click="$router.push('/projects')">全部项目</el-link>
          </div>
        </template>
        <div v-loading="loading" class="distribution-list">
          <button v-for="item in projectCategoryDist" :key="item.name" type="button" class="distribution-row" @click="$router.push('/projects')">
            <span class="distribution-label"><i :style="{ background: item.color }"></i>{{ item.name }}</span>
            <span class="distribution-track"><i :style="{ width: `${item.percent}%`, background: item.color }"></i></span>
            <strong>{{ item.count }}</strong>
          </button>
          <el-empty v-if="!loading && projectCategoryDist.length === 0" description="暂无项目分类数据" :image-size="54" />
        </div>
      </el-card>
    </section>

    <section v-if="canModels || canProjects" class="dashboard-grid activity-grid">
      <el-card v-if="canModels" class="dashboard-card activity-card" shadow="never">
        <template #header>
          <div class="section-heading">
            <div><strong>最近模型入库</strong><span>模型资产最新变化</span></div>
            <el-link type="primary" @click="$router.push('/models')">查看全部</el-link>
          </div>
        </template>
        <div v-loading="loading" class="activity-list">
          <button v-for="model in recentModels" :key="model.id" class="activity-item" type="button" @click="$router.push(`/models/${model.id}`)">
            <span class="activity-icon model-icon"><el-icon><Box /></el-icon></span>
            <span class="activity-main"><strong>{{ model.name || `模型 #${model.id}` }}</strong><span>{{ modelCategoryName(model) }} · {{ formatSize(model.totalSize || model.fileSize) }}</span></span>
            <span class="activity-side"><el-tag size="small" :type="modelStatusType(model.status)" effect="light">{{ modelStatusLabel(model.status) }}</el-tag><small>{{ formatRelativeTime(model.updatedAt || model.createdAt) }}</small></span>
          </button>
          <el-empty v-if="!loading && recentModels.length === 0" description="暂无模型记录" :image-size="54" />
        </div>
      </el-card>

      <el-card v-if="canProjects" class="dashboard-card activity-card" shadow="never">
        <template #header>
          <div class="section-heading">
            <div><strong>最近项目更新</strong><span>项目列表最新进展</span></div>
            <el-link type="primary" @click="$router.push('/projects')">查看全部</el-link>
          </div>
        </template>
        <div v-loading="loading" class="activity-list">
          <button v-for="project in recentProjects" :key="project.id" class="activity-item" type="button" @click="$router.push(`/projects/${project.id}`)">
            <span class="activity-icon project-icon"><el-icon><Folder /></el-icon></span>
            <span class="activity-main"><strong>{{ project.name || `项目 #${project.id}` }}</strong><span>{{ projectCategoryName(project) }} · v{{ project.currentVersion || 1 }}</span></span>
            <span class="activity-side"><el-tag size="small" :type="projectStatusType(project.status)" effect="light">{{ projectStatusLabel(project.status) }}</el-tag><small>{{ formatRelativeTime(project.updatedAt || project.createdAt) }}</small></span>
          </button>
          <el-empty v-if="!loading && recentProjects.length === 0" description="暂无项目记录" :image-size="54" />
        </div>
      </el-card>
    </section>

  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api'
import { userApi } from '@/api/index-modules'
import { modelApi } from '@/api/model'
import { projectApi } from '@/api/project'

type DistributionItem = { name: string; count: number; percent: number; color: string }
const authStore = useAuthStore()
const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const failedLoads = ref(0)
const lastRefreshed = ref('')
const models = ref<any[]>([])
const projects = ref<any[]>([])
const modelCategories = ref<any[]>([])
const projectCategories = ref<any[]>([])
const libraryStats = ref<any>({})
const userTotal = ref(0)
const pendingTotal = ref(0)

const canModels = computed(() => authStore.hasPermission('model:view'))
const canProjects = computed(() => authStore.hasPermission('project:view'))
const canUsers = computed(() => authStore.hasPermission('user:view'))
const canRegistration = computed(() => authStore.hasPermission('registration:view'))
const canCategory = computed(() => authStore.hasPermission('model:category_view'))
const canProcessingRecords = computed(() => authStore.hasPermission('model:process_records_view'))
const canDownloadRecords = computed(() => authStore.hasPermission('model:download_records_view'))
const canAssetOverview = computed(() => authStore.hasPermission('storage:asset_view'))

const openSystemPanel = (target: 'storage' | 'processing' | 'users' | 'downloads') => {
  const routes = {
    storage: { path: '/storage', query: { tab: 'assets', returnTo: '/dashboard' } },
    processing: { path: '/models/data', query: { tab: 'processing', returnTo: '/dashboard' } },
    users: { path: '/organization', query: { tab: 'users', returnTo: '/dashboard' } },
    downloads: { path: '/models/data', query: { tab: 'downloads', returnTo: '/dashboard' } },
  }
  router.push(routes[target])
}

const MODEL_COLORS = ['#238b70', '#4aa56e', '#83b94e', '#d4a047', '#778b84', '#4f9f9b', '#6b82b5', '#b07a66']
const PROJECT_COLORS = ['#2a8f73', '#61a85e', '#9abb52', '#d2a44b', '#768b84', '#7b89b4']
const STATUS_META: Record<string, { label: string; color: string }> = {
  planning: { label: '规划中', color: '#d2a44b' },
  in_progress: { label: '进行中', color: '#2a8f73' },
  completed: { label: '已完成', color: '#69aa5d' },
  archived: { label: '已归档', color: '#89958f' },
}

const modelTotal = computed(() => Number(libraryStats.value?.totalModels ?? libraryStats.value?.modelCount ?? models.value.length ?? 0))
const projectTotal = computed(() => projects.value.length)
const activeProjects = computed(() => projects.value.filter(item => item.status === 'in_progress').length)
const memberTotal = computed(() => Number(libraryStats.value?.memberCount ?? userTotal.value ?? 0))
const storageBytes = computed(() => Number(libraryStats.value?.totalStorageBytes ?? libraryStats.value?.storageBytes ?? 0))
const downloadTotal = computed(() => Number(libraryStats.value?.downloadCount ?? 0))
const processingModels = computed(() => models.value.filter(item => ['processing', 'pending'].includes(item.status)).length)
const failedModels = computed(() => models.value.filter(item => ['failed', 'error'].includes(item.status)).length)

const visibleStats = computed(() => {
  const list: any[] = []
  if (canModels.value) list.push({ title: '模型资产', value: modelTotal.value, icon: 'Box', color: '#238b70', bg: '#e6f5ef', hint: `存储 ${formatSize(storageBytes.value)}` })
  if (canProjects.value) list.push({ title: '项目总数', value: projectTotal.value, icon: 'Folder', color: '#67a743', bg: '#edf7e7', hint: '全部在管项目' })
  if (canProjects.value) list.push({ title: '进行中项目', value: activeProjects.value, icon: 'TrendCharts', color: '#c18b32', bg: '#fff5df', hint: `${projectTotal.value - activeProjects.value} 个其他状态` })
  if (canUsers.value || memberTotal.value) list.push({ title: '平台成员', value: memberTotal.value, icon: 'User', color: '#4c8ba6', bg: '#e9f4f8', hint: '当前协作规模' })
  if (canRegistration.value) list.push({ title: '待处理提醒', value: pendingTotal.value, icon: 'Bell', color: '#d36d64', bg: '#fff0ee', hint: '待审批注册申请' })
  return list
})

const categoryMap = computed(() => new Map<number, string>(modelCategories.value.map(item => [Number(item.id), item.name])))
const projectCategoryMap = computed(() => new Map<number, string>(projectCategories.value.map(item => [Number(item.id), item.name])))
const modelCategoryName = (model: any) => {
  if (Array.isArray(model.categoryNames) && model.categoryNames.length) return model.categoryNames.join('、')
  if (model.categoryName) return model.categoryName
  if (Array.isArray(model.categoryIds) && model.categoryIds.length) return model.categoryIds.map((id: number) => categoryMap.value.get(Number(id))).filter(Boolean).join('、') || '未分类'
  return categoryMap.value.get(Number(model.categoryId)) || '未分类'
}
const projectCategoryName = (project: any) => project.categoryName || projectCategoryMap.value.get(Number(project.categoryId)) || '未分类'

const buildDistribution = (list: any[], resolver: (item: any) => string, colors: string[]): DistributionItem[] => {
  const counts = new Map<string, number>()
  list.forEach(item => {
    const names = resolver(item).split('、').filter(Boolean)
    ;(names.length ? names : ['未分类']).forEach(name => counts.set(name, (counts.get(name) || 0) + 1))
  })
  const total = Array.from(counts.values()).reduce((sum, value) => sum + value, 0) || 1
  return Array.from(counts.entries()).sort((a, b) => b[1] - a[1]).slice(0, 7).map(([name, count], index) => ({ name, count, percent: Math.max(4, Math.round((count / total) * 100)), color: colors[index % colors.length] }))
}
const modelCategoryDist = computed(() => buildDistribution(models.value, modelCategoryName, MODEL_COLORS))
const projectCategoryDist = computed(() => buildDistribution(projects.value, projectCategoryName, PROJECT_COLORS))

const projectStatusDist = computed(() => {
  const total = projectTotal.value || 1
  return Object.entries(STATUS_META).map(([key, meta]) => {
    const count = projects.value.filter(item => (item.status || 'planning') === key).length
    return { key, ...meta, count, percent: Math.round((count / total) * 100) }
  })
})
const projectPieGradient = computed(() => {
  if (!projectTotal.value) return '#edf2ef'
  let cursor = 0
  const stops = projectStatusDist.value.filter(item => item.count > 0).map(item => {
    const start = cursor
    cursor += item.percent
    return `${item.color} ${start}% ${cursor}%`
  })
  return `conic-gradient(${stops.join(', ')})`
})

const trendDays = computed(() => {
  const days: { date: string; label: string; count: number }[] = []
  const today = new Date()
  for (let offset = 6; offset >= 0; offset--) {
    const date = new Date(today)
    date.setHours(0, 0, 0, 0)
    date.setDate(date.getDate() - offset)
    const key = `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`
    const count = models.value.filter(model => {
      const created = new Date(model.createdAt || model.updatedAt)
      return !Number.isNaN(created.getTime()) && `${created.getFullYear()}-${created.getMonth() + 1}-${created.getDate()}` === key
    }).length
    days.push({ date: key, label: `${date.getMonth() + 1}/${date.getDate()}`, count })
  }
  return days
})
const trendPoints = computed(() => {
  const max = Math.max(...trendDays.value.map(item => item.count), 1)
  return trendDays.value.map((item, index) => ({ ...item, x: 48 + index * 100, y: 164 - (item.count / max) * 122 }))
})
const trendLinePoints = computed(() => trendPoints.value.map(point => `${point.x},${point.y}`).join(' '))
const trendAreaPoints = computed(() => `48,164 ${trendLinePoints.value} 648,164`)
const weeklyModelTotal = computed(() => trendDays.value.reduce((sum, item) => sum + item.count, 0))
const recentModels = computed(() => [...models.value].sort(sortByRecent).slice(0, 6))
const recentProjects = computed(() => [...projects.value].sort(sortByRecent).slice(0, 6))

async function safeLoad<T>(loader: () => Promise<any>, fallback: T): Promise<T> {
  try { const response = await loader(); return (response?.data ?? fallback) as T }
  catch { failedLoads.value += 1; return fallback }
}

async function loadDashboard(manual = false) {
  if (manual) refreshing.value = true
  else loading.value = true
  failedLoads.value = 0
  const jobs: Promise<void>[] = []
  if (canModels.value) {
    jobs.push(safeLoad(() => modelApi.getModels({ page: 0, size: 500, sortField: 'time', sortDirection: 'desc' }), { list: [] }).then(data => { models.value = (data as any)?.list || [] }))
    jobs.push(safeLoad(() => modelApi.getLibraryStats(), {}).then(data => { libraryStats.value = data || {} }))
    jobs.push(safeLoad(() => modelApi.getCategories(), []).then(data => { modelCategories.value = Array.isArray(data) ? data : [] }))
  }
  if (canProjects.value) {
    jobs.push(safeLoad(() => projectApi.getProjects({ page: 0, size: 500 }), { list: [] }).then(data => { projects.value = (data as any)?.list || [] }))
    jobs.push(safeLoad(() => projectApi.getCategories(), []).then(data => { projectCategories.value = Array.isArray(data) ? data : [] }))
  }
  if (canUsers.value) jobs.push(safeLoad(() => userApi.getUsers({ page: 0, size: 1 }), { total: 0 }).then(data => { userTotal.value = Number((data as any)?.total ?? 0) }))
  if (canRegistration.value) jobs.push(safeLoad(() => api.get('/auth/registration-requests', { params: { status: 0, page: 0, size: 1 } }), { total: 0 }).then(data => { pendingTotal.value = Number((data as any)?.totalElements ?? (data as any)?.total ?? 0) }))
  await Promise.all(jobs)
  lastRefreshed.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  loading.value = false
  refreshing.value = false
}

function sortByRecent(a: any, b: any) { return new Date(b.updatedAt || b.createdAt || 0).getTime() - new Date(a.updatedAt || a.createdAt || 0).getTime() }
const projectStatusLabel = (status?: string) => STATUS_META[status || 'planning']?.label || '未设置'
const projectStatusType = (status?: string) => ({ in_progress: 'success', completed: 'success', planning: 'warning', archived: 'info' } as Record<string, any>)[status || 'planning'] || 'info'
const modelStatusLabel = (status?: string) => ({ available: '可用', ready: '可用', processing: '处理中', pending: '待处理', failed: '处理失败', error: '处理失败', draft: '草稿' } as Record<string, string>)[status || ''] || '可用'
const modelStatusType = (status?: string) => ({ available: 'success', ready: 'success', processing: 'warning', pending: 'warning', failed: 'danger', error: 'danger', draft: 'info' } as Record<string, any>)[status || ''] || 'success'

function formatSize(bytes: number) {
  const value = Number(bytes || 0)
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let index = 0; let size = value
  while (size >= 1024 && index < units.length - 1) { size /= 1024; index += 1 }
  return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}
function formatRelativeTime(value?: string) {
  if (!value) return '暂无时间'
  const date = new Date(value); const diff = Date.now() - date.getTime()
  if (Number.isNaN(diff)) return value
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.max(1, Math.floor(diff / 60_000))} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (diff < 604_800_000) return `${Math.floor(diff / 86_400_000)} 天前`
  return date.toLocaleDateString('zh-CN')
}
onMounted(() => loadDashboard())
</script>

<style scoped>
.dashboard-page { --dash-green: #238b70; --dash-ink: #18322a; padding-bottom: 28px; }
.welcome-banner { border: 1px solid #d9ebe4 !important; background: linear-gradient(112deg, #f8fcfa 0%, #eaf7f2 58%, #f7fbf6 100%) !important; overflow: hidden; }
.welcome-banner :deep(.el-card__body) { padding: 22px 26px; }
.welcome-content { display: flex; align-items: center; justify-content: space-between; gap: 22px; }
.eyebrow { color: var(--dash-green); font-size: 12px; font-weight: 700; letter-spacing: 1.6px; margin-bottom: 7px; }
.welcome-title { display: flex; align-items: center; gap: 10px; color: var(--dash-ink); font-size: 24px; font-weight: 720; }
.welcome-sub { margin-top: 7px; color: #6f877e; font-size: 13px; }
.role-tag { border-color: #bedfd2; background: rgba(255,255,255,.68); color: #2b8269; }
.welcome-meta { display: flex; align-items: center; gap: 12px; color: #789087; font-size: 12px; white-space: nowrap; }
.health-pill { display: inline-flex; align-items: center; gap: 7px; padding: 7px 11px; border: 1px solid #cfe8dd; border-radius: 999px; background: rgba(255,255,255,.72); color: #2f7c65; font-weight: 600; }
.health-pill.warning { border-color: #f1dfb8; color: #b17d28; }
.health-dot, .state-dot { width: 7px; height: 7px; border-radius: 50%; background: #35a576; box-shadow: 0 0 0 4px rgba(53,165,118,.11); }
.health-pill.warning .health-dot, .state-dot.warning { background: #d6a43b; box-shadow: 0 0 0 4px rgba(214,164,59,.12); }
.metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; margin-top: 16px; }
.stat-card { min-height: 112px; border-color: #e3eee9 !important; transition: transform .18s ease, box-shadow .18s ease; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 13px 30px rgba(40,92,74,.08) !important; }
.stat-card :deep(.el-card__body) { height: 100%; box-sizing: border-box; padding: 20px; }
.stat-content { display: flex; align-items: center; justify-content: space-between; gap: 12px; height: 100%; }
.stat-title { color: #7b8f87; font-size: 13px; }
.stat-value { margin: 4px 0 2px; color: var(--dash-ink); font-size: 30px; line-height: 1.1; font-weight: 750; letter-spacing: -.5px; }
.stat-hint { color: #9aaba5; font-size: 11px; }
.stat-icon { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 13px; flex: 0 0 auto; }
.dashboard-grid { display: grid; gap: 16px; margin-top: 16px; }
.chart-grid { grid-template-columns: minmax(340px, .82fr) minmax(520px, 1.45fr); }
.distribution-grid, .activity-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.dashboard-card { border-color: #e1ece7 !important; }
.dashboard-card :deep(.el-card__header) { padding: 17px 20px; border-bottom-color: #ebf1ee; }
.dashboard-card :deep(.el-card__body) { padding: 19px 20px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-heading > div:first-child { display: flex; flex-direction: column; gap: 3px; }
.section-heading strong { color: var(--dash-ink); font-size: 15px; font-weight: 680; }
.section-heading span { color: #92a49d; font-size: 11px; }
.project-status-body { min-height: 218px; display: grid; grid-template-columns: 190px 1fr; align-items: center; gap: 18px; }
.donut-wrap { display: grid; place-items: center; }
.donut { display: grid; place-items: center; width: 154px; height: 154px; border-radius: 50%; box-shadow: inset 0 0 0 1px rgba(33,88,70,.04); }
.donut-core { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 96px; height: 96px; border-radius: 50%; background: #fff; box-shadow: 0 5px 24px rgba(37,92,74,.08); }
.donut-core strong { color: var(--dash-ink); font-size: 29px; }
.donut-core span { margin-top: 1px; color: #93a49e; font-size: 11px; }
.legend-list { display: flex; flex-direction: column; gap: 6px; }
.legend-row { display: grid; grid-template-columns: 9px 1fr 28px 40px; align-items: center; gap: 8px; width: 100%; padding: 8px 7px; border: 0; border-radius: 9px; background: transparent; color: #536860; cursor: pointer; text-align: left; }
.legend-row:hover { background: #f3f8f5; }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; }
.legend-name { font-size: 12px; }
.legend-row strong { color: var(--dash-ink); font-size: 13px; text-align: right; }
.legend-percent { color: #9aaca5; font-size: 11px; text-align: right; }
.chart-total { display: flex !important; flex-direction: row !important; align-items: center; gap: 7px !important; color: #668178 !important; font-size: 12px !important; }
.chart-total > span { width: 8px; height: 8px; border-radius: 50%; background: var(--dash-green); }
.trend-chart { position: relative; min-height: 218px; }
.trend-chart svg { display: block; width: 100%; height: 218px; overflow: visible; }
.chart-grid-line { stroke: #e7eeeb; stroke-width: 1; stroke-dasharray: 4 7; }
.trend-line { fill: none; stroke: #238b70; stroke-width: 3.2; stroke-linecap: round; stroke-linejoin: round; }
.trend-point { fill: #fff; stroke: #238b70; stroke-width: 3; }
.axis-label { fill: #91a39c; font-size: 11px; }
.point-value { fill: #2d7561; font-size: 11px; font-weight: 700; }
.distribution-list { min-height: 210px; display: flex; flex-direction: column; gap: 12px; }
.distribution-row { display: grid; grid-template-columns: 120px 1fr 30px; align-items: center; gap: 12px; width: 100%; padding: 4px 0; border: 0; background: transparent; cursor: pointer; }
.distribution-row:hover .distribution-label { color: var(--dash-green); }
.distribution-label { display: flex; align-items: center; gap: 8px; min-width: 0; color: #536860; font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-align: left; }
.distribution-label i { width: 7px; height: 7px; border-radius: 50%; flex: 0 0 auto; }
.distribution-track { height: 8px; overflow: hidden; border-radius: 999px; background: #edf2f0; }
.distribution-track i { display: block; height: 100%; border-radius: inherit; transition: width .45s ease; }
.distribution-row strong { color: #50645c; font-size: 12px; text-align: right; }
.activity-list { min-height: 276px; display: flex; flex-direction: column; }
.activity-item { display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; align-items: center; gap: 12px; width: 100%; padding: 11px 5px; border: 0; border-bottom: 1px solid #edf2ef; background: transparent; cursor: pointer; text-align: left; }
.activity-item:last-child { border-bottom: 0; }
.activity-item:hover { background: #f7faf8; }
.activity-icon { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 11px; font-size: 18px; }
.model-icon { color: #238b70; background: #e8f6f0; }
.project-icon { color: #779f3f; background: #eff6e8; }
.activity-main { display: flex; flex-direction: column; min-width: 0; gap: 4px; }
.activity-main strong { color: #213b32; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.activity-main span { color: #91a39c; font-size: 11px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.activity-side { display: flex; flex-direction: column; align-items: flex-end; gap: 5px; }
.activity-side small { color: #9caca6; font-size: 10px; }
.system-overview { margin-top: 16px; }
.system-time { color: #92a49d !important; font-size: 11px !important; }
.system-grid { display: grid; grid-template-columns: repeat(5, minmax(160px, 1fr)); gap: 11px; }
.system-item { position: relative; display: grid; grid-template-columns: 38px 1fr auto; align-items: center; gap: 10px; min-height: 72px; padding: 12px; border: 1px solid #e6eeea; border-radius: 12px; background: #fbfdfc; }
.system-link { width: 100%; color: inherit; font: inherit; text-align: left; cursor: pointer; transition: border-color .18s ease, background .18s ease, transform .18s ease, box-shadow .18s ease; }
.system-link:hover { border-color: #b8dace; background: #f6fbf8; transform: translateY(-1px); box-shadow: 0 8px 20px rgba(40,92,74,.07); }
.system-link:focus-visible { outline: 2px solid #69b89b; outline-offset: 2px; }
.system-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 10px; }
.system-icon.healthy { color: #238b70; background: #e8f6f0; }.system-icon.storage { color: #568d45; background: #edf6e9; }.system-icon.process { color: #bd8730; background: #fff4dd; }.system-icon.team { color: #4b88a2; background: #eaf4f7; }.system-icon.downloads { color: #8175ab; background: #f1eef8; }
.system-item div { display: flex; flex-direction: column; gap: 3px; min-width: 0; }.system-item small { color: #91a39d; font-size: 10px; }.system-item strong { color: #29443b; font-size: 13px; white-space: nowrap; }.system-note { align-self: end; color: #9aaca5; font-size: 10px; white-space: nowrap; }.state-dot { justify-self: end; }
@media (max-width: 1200px) { .chart-grid { grid-template-columns: 1fr; }.system-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 860px) { .welcome-content { align-items: flex-start; flex-direction: column; }.welcome-meta { width: 100%; flex-wrap: wrap; }.distribution-grid, .activity-grid { grid-template-columns: 1fr; }.system-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 560px) { .welcome-banner :deep(.el-card__body) { padding: 18px; }.welcome-title { font-size: 20px; flex-wrap: wrap; }.project-status-body { grid-template-columns: 1fr; }.legend-list { width: 100%; }.distribution-row { grid-template-columns: 92px 1fr 24px; }.system-grid { grid-template-columns: 1fr; }.activity-item { grid-template-columns: 38px minmax(0, 1fr); }.activity-side { grid-column: 2; flex-direction: row; align-items: center; justify-content: space-between; } }
</style>
