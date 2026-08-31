<template>
  <div class="storage-page" v-loading="loading">
    <div class="storage-navigation">
      <el-button v-if="returnTarget" text @click="returnToSource"><el-icon><ArrowLeft /></el-icon>返回来源</el-button>
      <div class="storage-tabs" role="tablist" aria-label="存储空间视图">
        <button v-if="canAssetOverview" type="button" :class="{ active: activeTab === 'assets' }" @click="switchTab('assets')">资产总览</button>
        <button v-if="canManageLocations" type="button" :class="{ active: activeTab === 'locations' }" @click="switchTab('locations')">存储位置</button>
      </div>
    </div>
    <section class="storage-hero">
      <div>
        <p class="eyebrow">平台基础设施</p>
        <h1>{{ activeTab === 'assets' ? '资产总览' : '存储空间' }}</h1>
        <p class="hero-copy">{{ activeTab === 'assets' ? '集中查看模型资产与项目资料，点击条目可进入对应业务详情，返回时仍停留在当前总览。' : '统一管理平台对象存储、本地服务器目录与局域网存储。连接检测和扫描会在服务器端执行，页面不会暴露存储凭据。' }}</p>
      </div>
      <el-button v-if="activeTab === 'locations'" v-permission="'storage:create'" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>添加存储位置
      </el-button>
    </section>

    <section v-if="activeTab === 'locations'" class="summary-grid">
      <div class="summary-card"><span>存储位置</span><strong>{{ locations.length }}</strong><small>已配置位置</small></div>
      <div class="summary-card"><span>在线位置</span><strong>{{ onlineCount }}</strong><small>最近检测正常</small></div>
      <div class="summary-card accent"><span>已发现资产</span><strong>{{ totalAssets }}</strong><small>最近扫描合计</small></div>
      <div class="summary-card"><span>已统计占用</span><strong>{{ formatBytes(totalBytes) }}</strong><small>扫描可见文件</small></div>
    </section>

    <el-card v-if="activeTab === 'locations'" shadow="never" class="locations-card">
      <template #header>
        <div class="card-header">
          <div><strong>存储位置设置</strong><span>切换当前位置不会移动或删除原有文件</span></div>
          <el-button circle title="刷新" @click="loadLocations"><el-icon><Refresh /></el-icon></el-button>
        </div>
      </template>

      <div class="location-list">
        <article v-for="item in locations" :key="item.id" class="location-row" :class="{ current: item.current }">
          <div class="location-identity">
            <div class="storage-icon" :class="item.type.toLowerCase()"><el-icon><Coin /></el-icon></div>
            <div>
              <div class="location-name">
                <strong>{{ item.name }}</strong>
                <el-tag v-if="item.current" size="small" type="success" effect="plain">当前</el-tag>
                <el-tag v-if="item.protectedLocation" size="small" type="info" effect="plain">平台保护</el-tag>
              </div>
              <span>{{ typeLabel(item.type) }}</span>
            </div>
          </div>
          <div class="location-address"><span>地址</span><strong :title="item.address">{{ item.address }}</strong><small v-if="item.mountPath">挂载：{{ item.mountPath }}</small></div>
          <div class="location-status"><span>状态</span><el-tag :type="statusType(item.status)" size="small" effect="light">{{ statusLabel(item.status) }}</el-tag><small v-if="item.lastError" :title="item.lastError">{{ item.lastError }}</small></div>
          <div class="location-metric"><span>资产 / 占用</span><strong>{{ item.assetCount || 0 }} · {{ formatBytes(item.usedBytes) }}</strong><small>{{ item.lastScanAt ? `扫描于 ${formatTime(item.lastScanAt)}` : '尚未扫描' }}</small></div>
          <div class="location-actions">
            <el-button v-if="!item.current" v-permission="'storage:activate'" size="small" @click="activate(item)">设为当前</el-button>
            <el-button v-permission="'storage:scan'" size="small" type="primary" plain :loading="busyId === item.id && busyAction === 'scan'" @click="scan(item)">扫描</el-button>
            <el-dropdown trigger="click" @command="(command: string | number | object) => handleCommand(String(command), item)">
              <el-button size="small" circle><el-icon><MoreFilled /></el-icon></el-button>
              <template #dropdown><el-dropdown-menu>
                <el-dropdown-item v-if="authStore.hasPermission('storage:edit')" command="test">检测连接</el-dropdown-item>
                <el-dropdown-item v-if="authStore.hasPermission('storage:edit')" command="edit">编辑</el-dropdown-item>
                <el-dropdown-item v-if="authStore.hasPermission('storage:delete') && !item.current && !item.protectedLocation" command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu></template>
            </el-dropdown>
          </div>
        </article>
        <el-empty v-if="!locations.length" description="尚未配置存储位置" />
      </div>
    </el-card>

    <template v-else>
      <section class="summary-grid asset-summary">
        <div v-if="canViewModels" class="summary-card accent"><span>模型资产</span><strong>{{ modelTotal }}</strong><small>平台模型库</small></div>
        <div v-if="canViewProjectFiles" class="summary-card"><span>项目数量</span><strong>{{ projectTotal }}</strong><small>在管项目</small></div>
        <div v-if="canViewModels" class="summary-card"><span>当前页模型占用</span><strong>{{ formatBytes(currentModelBytes) }}</strong><small>{{ models.length }} 个模型</small></div>
        <div v-if="canViewProjectFiles" class="summary-card"><span>所选项目资料</span><strong>{{ projectFiles.length }}</strong><small>{{ selectedProjectName || '请选择项目' }}</small></div>
      </section>

      <div class="asset-grid" :class="{ single: !canViewModels || !canViewProjectFiles }">
        <el-card v-if="canViewModels" shadow="never" class="asset-card">
          <template #header><div class="card-header"><div><strong>模型资产</strong><span>点击模型进入资产详情</span></div><el-button circle title="刷新" @click="loadModels"><el-icon><Refresh /></el-icon></el-button></div></template>
          <el-table :data="models" stripe height="430" @row-click="openModel">
            <el-table-column prop="name" label="模型名称" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="modelStatusType(row.status)">{{ modelStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="版本" width="80"><template #default="{ row }">v{{ row.version || row.currentVersion || 1 }}</template></el-table-column>
            <el-table-column label="大小" width="110"><template #default="{ row }">{{ formatBytes(row.totalSize || row.fileSize) }}</template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="{ row }"><el-button text type="primary" @click.stop="openModel(row)">查看</el-button></template></el-table-column>
          </el-table>
          <el-pagination v-model:current-page="modelPage" :page-size="modelSize" :total="modelTotal" layout="total, prev, pager, next" class="asset-pagination" @current-change="loadModels" />
        </el-card>

        <el-card v-if="canViewProjectFiles" shadow="never" class="asset-card">
          <template #header><div class="card-header"><div><strong>项目资料</strong><span>按项目查看资料并进入对应位置</span></div><el-select v-model="selectedProjectId" filterable placeholder="选择项目" style="width: 210px" @change="loadProjectFiles"><el-option v-for="item in projects" :key="item.id" :label="item.name" :value="item.id" /></el-select></div></template>
          <el-table :data="projectFiles" stripe height="430" @row-click="openProjectFile">
            <el-table-column prop="fileName" label="文件名称" min-width="220" show-overflow-tooltip />
            <el-table-column label="版本" width="80"><template #default="{ row }">v{{ row.version || 1 }}</template></el-table-column>
            <el-table-column label="大小" width="110"><template #default="{ row }">{{ formatBytes(row.fileSize) }}</template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="{ row }"><el-button text type="primary" @click.stop="openProjectFile(row)">定位</el-button></template></el-table-column>
          </el-table>
          <div v-if="!selectedProjectId" class="asset-empty-hint">选择一个项目后查看其中的全部资料</div>
        </el-card>
      </div>
    </template>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑存储位置' : '添加存储位置'" width="620px" destroy-on-close>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="位置名称" required><el-input v-model="form.name" placeholder="例如 西秦模型素材库" /></el-form-item>
          <el-form-item label="存储类型" required>
            <el-select v-model="form.type" style="width:100%"><el-option v-for="option in typeOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select>
          </el-form-item>
        </div>
        <el-form-item :label="addressLabel" required><el-input v-model="form.address" :placeholder="addressPlaceholder" /></el-form-item>
        <el-form-item v-if="isRemote" label="服务器挂载目录（可选）">
          <el-input v-model="form.mountPath" placeholder="例如 /mnt/xiqin-assets；配置后可统计文件数量与占用" />
        </el-form-item>
        <div v-if="isRemote" class="form-grid">
          <el-form-item label="用户名"><el-input v-model="form.username" autocomplete="off" /></el-form-item>
          <el-form-item :label="editingId ? '凭据（留空保持不变）' : '密码或密钥'"><el-input v-model="form.credentialSecret" type="password" show-password autocomplete="new-password" /></el-form-item>
        </div>
        <el-alert :title="typeHelp" type="info" :closable="false" show-icon />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveLocation">保存位置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storageLocationApi } from '@/api/index-modules'
import { modelApi } from '@/api/model'
import { projectApi } from '@/api/project'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const canAssetOverview = computed(() => authStore.hasPermission('storage:asset_view'))
const canManageLocations = computed(() => authStore.hasPermission('storage:view'))
const canViewModels = computed(() => authStore.hasPermission('model:view'))
const canViewProjectFiles = computed(() => authStore.hasPermission('project:view') && authStore.hasPermission('project:file_view'))
const returnTarget = computed(() => typeof route.query.returnTo === 'string' && route.query.returnTo.startsWith('/') ? route.query.returnTo : '')
const requestedTab = route.query.tab === 'locations' ? 'locations' : 'assets'
const activeTab = ref(requestedTab === 'assets' && canAssetOverview.value ? 'assets' : canManageLocations.value ? 'locations' : 'assets')
const locations = ref<any[]>([])
const models = ref<any[]>([])
const projects = ref<any[]>([])
const projectFiles = ref<any[]>([])
const modelPage = ref(1)
const modelSize = ref(20)
const modelTotal = ref(0)
const projectTotal = ref(0)
const selectedProjectId = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const busyId = ref<number | null>(null)
const busyAction = ref('')
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const typeOptions = [
  { value: 'MINIO', label: '平台对象存储（MinIO）' },
  { value: 'LOCAL', label: '本地 / 已挂载目录' },
  { value: 'SMB', label: 'SMB 局域网共享' },
  { value: 'FTP', label: 'FTP 服务器' },
  { value: 'SFTP', label: 'SFTP 服务器' },
]
const form = reactive({ name: '', type: 'LOCAL', address: '', mountPath: '', username: '', credentialSecret: '' })
const isRemote = computed(() => ['SMB', 'FTP', 'SFTP'].includes(form.type))
const onlineCount = computed(() => locations.value.filter(item => item.status === 'online').length)
const totalAssets = computed(() => locations.value.reduce((sum, item) => sum + Number(item.assetCount || 0), 0))
const totalBytes = computed(() => locations.value.reduce((sum, item) => sum + Number(item.usedBytes || 0), 0))
const currentModelBytes = computed(() => models.value.reduce((sum, item) => sum + Number(item.totalSize || item.fileSize || 0), 0))
const selectedProjectName = computed(() => projects.value.find(item => item.id === selectedProjectId.value)?.name || '')
const addressLabel = computed(() => form.type === 'LOCAL' ? '服务器目录' : form.type === 'MINIO' ? '桶或位置说明' : '网络地址')
const addressPlaceholder = computed(() => ({ LOCAL: '/data/models', MINIO: 'models / thumbnails / avatars', SMB: 'smb://192.168.1.84/share', FTP: 'ftp://192.168.1.84/assets', SFTP: 'sftp://192.168.1.84/assets' }[form.type] || ''))
const typeHelp = computed(() => form.type === 'MINIO'
  ? '平台对象存储会扫描模型、缩略图和头像桶。平台默认位置受保护，不能删除。'
  : form.type === 'LOCAL'
    ? '路径必须是后端服务器或容器能访问的绝对目录；扫描会统计其中所有文件。'
    : '系统会检测远程服务端口；若填写服务器挂载目录，还会扫描目录并统计资产与占用。')

const loadLocations = async () => {
  loading.value = true
  try { locations.value = (await storageLocationApi.list()).data || [] } finally { loading.value = false }
}
const loadModels = async () => {
  const res = await modelApi.getModels({ page: modelPage.value - 1, size: modelSize.value, sortField: 'time', sortDirection: 'desc' })
  models.value = res.data?.list || []
  modelTotal.value = Number(res.data?.total || 0)
}
const loadProjects = async () => {
  const res = await projectApi.getProjects({ page: 0, size: 500 })
  projects.value = res.data?.list || []
  projectTotal.value = Number(res.data?.total || projects.value.length)
}
const loadProjectFiles = async () => {
  projectFiles.value = selectedProjectId.value ? ((await projectApi.getFiles(selectedProjectId.value)).data || []) : []
}
const loadAssets = async () => {
  loading.value = true
  try {
    const jobs: Promise<void>[] = []
    if (canViewModels.value) jobs.push(loadModels())
    if (canViewProjectFiles.value) jobs.push(loadProjects())
    await Promise.all(jobs)
  } finally { loading.value = false }
}
const switchTab = (tab: 'assets' | 'locations') => {
  activeTab.value = tab
  router.replace({ path: route.path, query: { ...route.query, tab } })
  if (tab === 'assets') loadAssets(); else loadLocations()
}
const returnToSource = () => router.push(returnTarget.value || '/dashboard')
const openModel = (row: any) => router.push({ path: `/models/${row.id}`, query: { returnTo: route.fullPath } })
const openProjectFile = (row: any) => {
  if (!selectedProjectId.value) return
  router.push({ path: `/projects/${selectedProjectId.value}`, query: { tab: 'files', fileId: row.id, returnTo: route.fullPath } })
}
const resetForm = () => Object.assign(form, { name: '', type: 'LOCAL', address: '', mountPath: '', username: '', credentialSecret: '' })
const openCreate = () => { editingId.value = null; resetForm(); dialogVisible.value = true }
const openEdit = (item: any) => {
  editingId.value = item.id
  Object.assign(form, { name: item.name, type: item.type, address: item.address, mountPath: item.mountPath || '', username: item.username || '', credentialSecret: '' })
  dialogVisible.value = true
}
const saveLocation = async () => {
  if (!form.name.trim() || !form.address.trim()) return ElMessage.warning('请填写名称和地址')
  saving.value = true
  try {
    const payload = { ...form, name: form.name.trim(), address: form.address.trim() }
    if (editingId.value) await storageLocationApi.update(editingId.value, payload)
    else await storageLocationApi.create(payload)
    dialogVisible.value = false
    ElMessage.success('存储位置已保存')
    await loadLocations()
  } finally { saving.value = false }
}
const run = async (item: any, action: 'scan' | 'test', success: string) => {
  busyId.value = item.id; busyAction.value = action
  try {
    const res = action === 'scan' ? await storageLocationApi.scan(item.id) : await storageLocationApi.test(item.id)
    Object.assign(item, res.data)
    if (res.data.status === 'online') ElMessage.success(success)
    else ElMessage.error(res.data.lastError || '连接失败')
  } finally { busyId.value = null; busyAction.value = '' }
}
const scan = (item: any) => run(item, 'scan', '扫描完成，资产统计已更新')
const activate = async (item: any) => {
  await storageLocationApi.activate(item.id)
  ElMessage.success(`${item.name} 已设为当前位置`)
  await loadLocations()
}
const handleCommand = async (command: string, item: any) => {
  if (command === 'edit') openEdit(item)
  if (command === 'test') await run(item, 'test', '连接正常')
  if (command === 'delete') {
    try { await ElMessageBox.confirm(`确定删除“${item.name}”吗？`, '删除存储位置', { type: 'warning', confirmButtonText: '删除' }) } catch { return }
    await storageLocationApi.delete(item.id); ElMessage.success('存储位置已删除'); await loadLocations()
  }
}
const typeLabel = (type: string) => ({ MINIO: '平台对象存储', LOCAL: '本地目录', SMB: 'SMB 共享', FTP: 'FTP', SFTP: 'SFTP' }[type] || type)
const statusLabel = (status: string) => ({ online: '在线', error: '异常', unknown: '未检测' }[status] || status)
const statusType = (status: string) => status === 'online' ? 'success' : status === 'error' ? 'danger' : 'info'
const modelStatusLabel = (status?: string) => ({ available: '可用', ready: '可用', processing: '处理中', pending: '待处理', failed: '处理失败', error: '处理失败', draft: '草稿' } as Record<string, string>)[status || ''] || '未知'
const modelStatusType = (status?: string) => ({ available: 'success', ready: 'success', processing: 'warning', pending: 'warning', failed: 'danger', error: 'danger', draft: 'info' } as Record<string, any>)[status || ''] || 'info'
const formatBytes = (bytes: number) => { if (!bytes) return '0 B'; const units = ['B','KB','MB','GB','TB']; let value = bytes, i = 0; while (value >= 1024 && i < units.length - 1) { value /= 1024; i++ } return `${value.toFixed(i > 2 ? 1 : 0)} ${units[i]}` }
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
onMounted(() => activeTab.value === 'assets' ? loadAssets() : loadLocations())
</script>

<style scoped>
.storage-page { max-width: 1500px; margin: 0 auto; color: #1e293b; }
.storage-navigation { min-height: 36px; display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.storage-tabs { display: inline-flex; gap: 4px; padding: 3px; border: 1px solid #dfeae5; border-radius: 11px; background: #fff; }
.storage-tabs button { padding: 7px 14px; border: 0; border-radius: 8px; background: transparent; color: #60766e; cursor: pointer; }
.storage-tabs button.active { color: #fff; background: #238b70; box-shadow: 0 4px 12px rgba(35,139,112,.17); }
.storage-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 20px; padding: 8px 2px; }
.eyebrow { margin: 0 0 6px; color: #2d9577; font-size: 11px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
h1 { margin: 0; font-size: 26px; letter-spacing: -.02em; }
.hero-copy { max-width: 760px; margin: 8px 0 0; color: #64748b; font-size: 13px; line-height: 1.65; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.summary-card { padding: 17px 18px; background: rgba(255,255,255,.92); border: 1px solid #e5eaf1; border-radius: 14px; box-shadow: 0 10px 26px rgba(15,23,42,.04); }
.summary-card span, .summary-card small { display: block; color: #94a3b8; font-size: 11px; }
.summary-card strong { display: block; margin: 7px 0 3px; color: #1e293b; font-size: 23px; }
.summary-card.accent { background: #eff9f5; border-color: #b9dfd2; }
.summary-card.accent strong { color: #1f8067; }
.locations-card { border: 1px solid #e5eaf1; border-radius: 16px; }
.locations-card :deep(.el-card__header) { padding: 17px 20px; }
.locations-card :deep(.el-card__body) { padding: 0; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-header > div { display: flex; flex-direction: column; gap: 3px; }
.card-header span { color: #94a3b8; font-size: 11px; font-weight: 400; }
.location-row { display: grid; grid-template-columns: minmax(210px,1.15fr) minmax(220px,1.4fr) minmax(140px,.8fr) minmax(165px,.85fr) auto; align-items: center; gap: 18px; min-height: 92px; padding: 14px 20px; border-bottom: 1px solid #edf2f7; }
.location-row:last-child { border-bottom: 0; }
.location-row.current { background: #f2faf6; box-shadow: inset 3px 0 #2d9577; }
.location-identity { display: flex; align-items: center; gap: 12px; min-width: 0; }
.storage-icon { width: 38px; height: 38px; display: grid; flex: 0 0 38px; place-items: center; color: #1f8067; background: #eff9f5; border: 1px solid #dff2eb; border-radius: 11px; }
.storage-icon.smb, .storage-icon.sftp, .storage-icon.ftp { color: #7c3aed; background: #f5f3ff; border-color: #ede9fe; }
.location-name { display: flex; align-items: center; flex-wrap: wrap; gap: 5px; }
.location-name strong { font-size: 14px; }
.location-identity > div > span { color: #94a3b8; font-size: 11px; }
.location-address, .location-status, .location-metric { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.location-address > span, .location-status > span, .location-metric > span { color: #94a3b8; font-size: 10px; }
.location-address strong, .location-metric strong { overflow: hidden; color: #475569; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.location-address small, .location-status small, .location-metric small { overflow: hidden; color: #94a3b8; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.location-status .el-tag { width: max-content; }
.location-actions { display: flex; align-items: center; justify-content: flex-end; gap: 7px; }
.location-actions .el-button { margin-left: 0; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
.asset-grid { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(0, .95fr); gap: 16px; }
.asset-grid.single { grid-template-columns: minmax(0, 1fr); }
.asset-card { border: 1px solid #e5eaf1; border-radius: 16px; }
.asset-card :deep(.el-table__row) { cursor: pointer; }
.asset-pagination { margin-top: 14px; justify-content: flex-end; }
.asset-empty-hint { padding: 12px 0 0; color: #94a3b8; font-size: 12px; text-align: center; }
@media (max-width: 1120px) { .location-row { grid-template-columns: 1.2fr 1.3fr .8fr auto; } .location-metric { display: none; } }
@media (max-width: 1000px) { .asset-grid { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .storage-hero { align-items: flex-start; flex-direction: column; } .summary-grid { grid-template-columns: 1fr 1fr; } .location-row { grid-template-columns: 1fr auto; gap: 12px; } .location-address, .location-status { grid-column: 1 / -1; } .location-actions { grid-column: 2; grid-row: 1; } .form-grid { grid-template-columns: 1fr; } }
</style>
