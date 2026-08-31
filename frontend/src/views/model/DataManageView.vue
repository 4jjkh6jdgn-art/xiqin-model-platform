<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div><span class="page-title">数据管理</span><span class="page-sub">分类维护 · 处理队列 · 上传记录 · 下载记录</span></div>
          <el-button v-if="returnTarget" plain @click="returnToSource">返回来源</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- ============ 分类管理 ============ -->
        <el-tab-pane v-if="canViewCategories" label="分类管理" name="categories">
          <el-row :gutter="20">
            <el-col v-if="canModelCategories" :span="canProjectCategories ? 12 : 24">
              <el-card shadow="never" class="inner-card">
                <template #header>
                  <div class="card-header">
                    <span>模型分类</span>
                    <el-button v-permission="'model:category_create'" type="primary" size="small" @click="openCreate('model')">
                      <el-icon><Plus /></el-icon> 新建
                    </el-button>
                  </div>
                </template>
                <el-table :data="modelCategories" v-loading="loading" stripe size="small">
                  <el-table-column prop="id" label="ID" width="60" />
                  <el-table-column prop="name" label="分类名称" />
                  <el-table-column prop="code" label="编码" width="120" />
                  <el-table-column prop="description" label="描述" show-overflow-tooltip />
                  <el-table-column v-if="canModelCategoryActions" label="操作" width="130">
                    <template #default="{ row }">
                      <el-button v-permission="'model:category_edit'" size="small" text type="primary" @click="openEdit('model', row)">编辑</el-button>
                      <el-popconfirm v-permission="'model:category_delete'" title="确定删除？" @confirm="deleteCategory('model', row.id)">
                        <template #reference>
                          <el-button size="small" text type="danger">删除</el-button>
                        </template>
                      </el-popconfirm>
                    </template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>

            <el-col v-if="canProjectCategories" :span="canModelCategories ? 12 : 24">
              <el-card shadow="never" class="inner-card">
                <template #header>
                  <div class="card-header">
                    <span>项目分类</span>
                    <el-button v-permission="'project:category_create'" type="primary" size="small" @click="openCreate('project')">
                      <el-icon><Plus /></el-icon> 新建
                    </el-button>
                  </div>
                </template>
                <el-table :data="projectCategories" v-loading="loading" stripe size="small">
                  <el-table-column prop="id" label="ID" width="60" />
                  <el-table-column prop="name" label="分类名称" />
                  <el-table-column prop="code" label="编码" width="120" />
                  <el-table-column prop="description" label="描述" show-overflow-tooltip />
                  <el-table-column v-if="canProjectCategoryActions" label="操作" width="130">
                    <template #default="{ row }">
                      <el-button v-permission="'project:category_edit'" size="small" text type="primary" @click="openEdit('project', row)">编辑</el-button>
                      <el-popconfirm v-permission="'project:category_delete'" title="确定删除？" @confirm="deleteCategory('project', row.id)">
                        <template #reference>
                          <el-button size="small" text type="danger">删除</el-button>
                        </template>
                      </el-popconfirm>
                    </template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- ============ 处理队列 ============ -->
        <el-tab-pane v-if="canProcessingRecords" label="处理队列" name="processing">
          <div class="tab-toolbar record-toolbar">
            <el-select v-model="processingStatus" placeholder="全部处理状态" clearable style="width: 180px" @change="resetProcessingPage">
              <el-option label="全部状态" value="" />
              <el-option label="待处理" value="pending" />
              <el-option label="处理中" value="processing" />
              <el-option label="处理失败" value="failed" />
              <el-option label="可用" value="available" />
              <el-option label="草稿" value="draft" />
            </el-select>
            <el-button :loading="processingLoading" @click="loadProcessingRecords">刷新</el-button>
          </div>
          <el-table :data="processingRecords" v-loading="processingLoading" stripe>
            <el-table-column prop="name" label="模型" min-width="220" show-overflow-tooltip />
            <el-table-column label="版本" width="90"><template #default="{ row }">v{{ row.version || row.currentVersion || 1 }}</template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="modelStatusType(row.status)" size="small">{{ modelStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="文件" width="110"><template #default="{ row }">{{ row.fileCount || 0 }} 个</template></el-table-column>
            <el-table-column label="资产大小" width="120"><template #default="{ row }">{{ formatSize(row.totalSize || row.fileSize) }}</template></el-table-column>
            <el-table-column label="最近更新" width="180"><template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template></el-table-column>
            <el-table-column v-if="canModelView" label="操作" width="100"><template #default="{ row }"><el-button text type="primary" @click="openModelDetail(row.id)">查看详情</el-button></template></el-table-column>
          </el-table>
          <el-pagination v-model:current-page="processingPage" :page-size="processingSize" :total="processingTotal" layout="total, prev, pager, next" class="record-pagination" @current-change="loadProcessingRecords" />
        </el-tab-pane>

        <!-- ============ 上传记录 ============ -->
        <el-tab-pane v-if="canUploadRecords" label="上传记录" name="uploads">
          <div class="tab-toolbar">
            <el-select v-model="uploadUserId" placeholder="全部用户" clearable style="width: 180px" @change="loadUploadRecords">
              <el-option label="全部用户" :value="undefined" />
            </el-select>
          </div>
          <el-table :data="uploadRecords" v-loading="uploadLoading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="userId" label="用户ID" width="90" />
            <el-table-column prop="modelId" label="模型ID" width="90">
              <template #default="{ row }">
                <el-link v-if="row.modelId && canModelView" type="primary" @click="openModelDetail(row.modelId)">
                  {{ row.modelId }}
                </el-link>
                <span v-else-if="row.modelId">{{ row.modelId }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="fileCount" label="文件数" width="90" />
            <el-table-column label="总大小" width="110">
              <template #default="{ row }">{{ formatSize(row.totalSize) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="uploadPage"
            :page-size="uploadSize"
            :total="uploadTotal"
            layout="total, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end"
            @current-change="loadUploadRecords"
          />
        </el-tab-pane>

        <!-- ============ 下载记录 ============ -->
        <el-tab-pane v-if="canDownloadRecords" label="下载记录" name="downloads">
          <el-table :data="downloadRecords" v-loading="downloadLoading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="userId" label="用户ID" width="90" />
            <el-table-column prop="modelId" label="模型ID" width="90">
              <template #default="{ row }">
                <el-link v-if="canModelView" type="primary" @click="openModelDetail(row.modelId)">{{ row.modelId }}</el-link>
                <span v-else>{{ row.modelId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column label="下载时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="downloadPage"
            :page-size="downloadSize"
            :total="downloadTotal"
            layout="total, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end"
            @current-change="loadDownloadRecords"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- Category Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新建分类'" width="450px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="分类名称">
          <el-input v-model="form.name" placeholder="分类名称" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.code" placeholder="英文编码（如 character）" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-permission="currentType === 'model'
            ? (editingId ? 'model:category_edit' : 'model:category_create')
            : (editingId ? 'project:category_edit' : 'project:category_create')"
          type="primary"
          @click="saveCategory"
        >保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { modelApi } from '@/api/model'
import { projectApi } from '@/api/project'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const canModelCategories = computed(() => authStore.hasPermission('model:category_view'))
const canProjectCategories = computed(() => authStore.hasPermission('project:category_view'))
const canViewCategories = computed(() => canModelCategories.value || canProjectCategories.value)
const canUploadRecords = computed(() => authStore.hasPermission('model:upload_records_view'))
const canDownloadRecords = computed(() => authStore.hasPermission('model:download_records_view'))
const canProcessingRecords = computed(() => authStore.hasPermission('model:process_records_view'))
const canModelView = computed(() => authStore.hasPermission('model:view'))
const canModelCategoryActions = computed(() => ['model:category_edit', 'model:category_delete'].some(authStore.hasPermission))
const canProjectCategoryActions = computed(() => ['project:category_edit', 'project:category_delete'].some(authStore.hasPermission))

const activeTab = ref('categories')
const returnTarget = computed(() => typeof route.query.returnTo === 'string' && route.query.returnTo.startsWith('/') ? route.query.returnTo : '')
const availableTabs = computed(() => [
  canViewCategories.value && 'categories',
  canProcessingRecords.value && 'processing',
  canUploadRecords.value && 'uploads',
  canDownloadRecords.value && 'downloads',
].filter(Boolean) as string[])

const returnToSource = () => router.push(returnTarget.value || '/dashboard')
const openModelDetail = (id: number) => router.push({ path: `/models/${id}`, query: { returnTo: route.fullPath } })

// ---------- 分类管理 ----------
const modelCategories = ref<any[]>([])
const projectCategories = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const currentType = ref<'model' | 'project'>('model')
const form = reactive({ name: '', code: '', description: '' })

const loadCategories = async () => {
  loading.value = true
  try {
    const requests: Promise<void>[] = []
    if (canModelCategories.value) requests.push(modelApi.getCategories().then(res => { modelCategories.value = res.data }))
    if (canProjectCategories.value) requests.push(projectApi.getCategories().then(res => { projectCategories.value = res.data }))
    await Promise.all(requests)
  } finally {
    loading.value = false
  }
}

const openCreate = (type: 'model' | 'project') => {
  currentType.value = type
  editingId.value = null
  form.name = ''
  form.code = ''
  form.description = ''
  dialogVisible.value = true
}

const openEdit = (type: 'model' | 'project', row: any) => {
  currentType.value = type
  editingId.value = row.id
  form.name = row.name
  form.code = row.code
  form.description = row.description
  dialogVisible.value = true
}

const saveCategory = async () => {
  if (!form.name?.trim()) return ElMessage.warning('请输入分类名称')
  form.name = form.name.trim()
  if (currentType.value === 'model') {
    if (editingId.value) await modelApi.updateCategory(editingId.value, form)
    else await modelApi.createCategory(form)
  } else {
    if (editingId.value) await projectApi.updateCategory(editingId.value, form)
    else await projectApi.createCategory(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadCategories()
}

const deleteCategory = async (type: 'model' | 'project', id: number) => {
  if (type === 'model') await modelApi.deleteCategory(id)
  else await projectApi.deleteCategory(id)
  ElMessage.success('删除成功')
  loadCategories()
}

// ---------- 上传记录 ----------
const uploadRecords = ref<any[]>([])
const uploadLoading = ref(false)
const uploadPage = ref(1)
const uploadSize = ref(20)
const uploadTotal = ref(0)
const uploadUserId = ref<number | undefined>()

const loadUploadRecords = async () => {
  uploadLoading.value = true
  try {
    const res = await modelApi.getUploadRecords({
      userId: uploadUserId.value, page: uploadPage.value - 1, size: uploadSize.value
    })
    uploadRecords.value = res.data.list
    uploadTotal.value = res.data.total
  } finally {
    uploadLoading.value = false
  }
}

// ---------- 下载记录 ----------
const downloadRecords = ref<any[]>([])
const downloadLoading = ref(false)
const downloadPage = ref(1)
const downloadSize = ref(20)
const downloadTotal = ref(0)

const loadDownloadRecords = async () => {
  downloadLoading.value = true
  try {
    const res = await modelApi.getDownloadRecords({ page: downloadPage.value - 1, size: downloadSize.value })
    downloadRecords.value = res.data.list
    downloadTotal.value = res.data.total
  } finally {
    downloadLoading.value = false
  }
}

// ---------- 处理队列 ----------
const processingRecords = ref<any[]>([])
const processingLoading = ref(false)
const processingPage = ref(1)
const processingSize = ref(20)
const processingTotal = ref(0)
const processingStatus = ref('')

const loadProcessingRecords = async () => {
  if (!canProcessingRecords.value) return
  processingLoading.value = true
  try {
    const params: Record<string, any> = { page: processingPage.value - 1, size: processingSize.value }
    if (processingStatus.value) params.status = processingStatus.value
    const res = await modelApi.getProcessingRecords(params)
    processingRecords.value = res.data?.list || []
    processingTotal.value = Number(res.data?.total || 0)
  } finally {
    processingLoading.value = false
  }
}
const resetProcessingPage = () => { processingPage.value = 1; loadProcessingRecords() }

const formatSize = (bytes: number) => {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, s = bytes
  while (s >= 1024 && i < units.length - 1) { s /= 1024; i++ }
  return `${s.toFixed(1)} ${units[i]}`
}

const formatTime = (t: string) => t ? new Date(t).toLocaleString('zh-CN') : '-'
const modelStatusLabel = (status?: string) => ({ available: '可用', ready: '可用', processing: '处理中', pending: '待处理', failed: '处理失败', error: '处理失败', draft: '草稿' } as Record<string, string>)[status || ''] || '未知'
const modelStatusType = (status?: string) => ({ available: 'success', ready: 'success', processing: 'warning', pending: 'warning', failed: 'danger', error: 'danger', draft: 'info' } as Record<string, any>)[status || ''] || 'info'

watch(activeTab, tab => {
  if (!tab || String(route.query.tab || '') === tab) return
  router.replace({ path: route.path, query: { ...route.query, tab } })
})

onMounted(() => {
  const requestedTab = typeof route.query.tab === 'string' ? route.query.tab : ''
  activeTab.value = availableTabs.value.includes(requestedTab) ? requestedTab : (availableTabs.value[0] || '')
  if (canViewCategories.value) loadCategories()
  if (canProcessingRecords.value) loadProcessingRecords()
  if (canUploadRecords.value) loadUploadRecords()
  if (canDownloadRecords.value) loadDownloadRecords()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.card-header > div { display: flex; flex-direction: column; gap: 4px; }
.page-title { font-size: 15px; font-weight: 600; }
.page-sub { font-size: 12px; color: #909399; }
.inner-card { border: none; box-shadow: none; }
.tab-toolbar { margin-bottom: 14px; }
.record-toolbar { display: flex; align-items: center; gap: 10px; }
.record-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
