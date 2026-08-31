<template>
  <div class="page-container">
    <el-card class="project-list-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-title"><strong>项目列表</strong><small>共 {{ total }} 个项目</small></div>
          <div class="header-actions">
            <el-select v-model="categoryId" placeholder="全部分类" clearable class="category-filter" @change="handleFilterChange">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-input v-model="keyword" placeholder="搜索项目" class="project-search" clearable @clear="handleFilterChange" @keyup.enter="handleFilterChange">
              <template #append><el-button @click="handleFilterChange"><el-icon><Search /></el-icon></el-button></template>
            </el-input>
            <el-popover placement="bottom-end" trigger="click" :width="240" popper-class="project-view-popper">
              <template #reference>
                <button type="button" class="view-settings-trigger" title="项目排列方式与每页数量">
                  <el-icon><Grid v-if="viewMode === 'card'" /><List v-else /></el-icon>
                  <span>{{ size }} / 页</span>
                  <el-icon><ArrowDown /></el-icon>
                </button>
              </template>
              <div class="view-settings-panel">
                <strong>排列方式</strong>
                <div class="project-view-switch" role="group" aria-label="项目排列方式">
                  <button type="button" :class="{ active: viewMode === 'card' }" @click="viewMode = 'card'"><el-icon><Grid /></el-icon>卡片</button>
                  <button type="button" :class="{ active: viewMode === 'table' }" @click="viewMode = 'table'"><el-icon><List /></el-icon>表格</button>
                </div>
                <strong>每页显示</strong>
                <el-radio-group :model-value="size" size="small" class="page-size-options" @change="handlePageSizeChange">
                  <el-radio-button v-for="option in [5, 10, 20, 30]" :key="option" :value="option">{{ option }}</el-radio-button>
                </el-radio-group>
              </div>
            </el-popover>
            <el-button v-permission="'project:create'" type="primary" @click="dialogVisible = true">
              <el-icon><Plus /></el-icon> 新建项目
            </el-button>
          </div>
        </div>
      </template>

      <div class="scope-filter-bar">
        <span class="scope-filter-label">项目范围</span>
        <div class="scope-filter-buttons" role="group" aria-label="项目范围筛选">
          <button
            v-for="option in scopeOptions"
            :key="option.value"
            type="button"
            :class="{ active: scopeFilter === option.value }"
            @click="selectScope(option.value)"
          >
            <el-icon><component :is="option.icon" /></el-icon>
            {{ option.label }}
          </button>
        </div>
      </div>

      <div v-loading="loading" class="project-content">
        <el-empty v-if="!projects.length && !loading" description="暂无符合条件的项目" />

        <el-row v-if="viewMode === 'card' && projects.length" :gutter="16" class="project-grid">
          <el-col v-for="project in projects" :key="project.id" :xs="24" :sm="12" :md="8" :lg="6" class="project-grid-item">
            <el-popover
              placement="right-start"
              trigger="hover"
              :width="286"
              :show-after="180"
              popper-class="project-cover-popper"
              @show="loadProjectCover(project)"
            >
              <template #reference>
                <el-card shadow="hover" class="project-card" @click="$router.push(`/projects/${project.id}`)">
                  <div class="project-card-heading">
                    <h3 :title="project.name">{{ project.name }}</h3>
                    <el-tag :type="statusType(project.status)" size="small">{{ statusText(project.status) }}</el-tag>
                  </div>
                  <div class="project-category"><el-icon><CollectionTag /></el-icon>{{ categoryName(project.categoryId) }}</div>
                  <p :title="project.description">{{ project.description || '暂无项目描述' }}</p>
                  <div class="project-card-footer">
                    <span><el-icon><Calendar /></el-icon>{{ formatDate(project.createdAt) }}</span>
                    <div class="project-card-actions" @click.stop>
                      <el-button size="small" text type="primary" @click="$router.push(`/projects/${project.id}`)">详情</el-button>
                      <el-popconfirm v-permission="'project:delete'" title="确定删除该项目？" @confirm="deleteProject(project.id)">
                        <template #reference><el-button size="small" text type="danger">删除</el-button></template>
                      </el-popconfirm>
                    </div>
                  </div>
                </el-card>
              </template>
              <div class="project-cover-preview">
                <div class="cover-preview-media">
                  <img v-if="coverObjectUrls[project.id]" :src="coverObjectUrls[project.id]" :alt="`${project.name}封面`" />
                  <div v-else-if="coverLoadingIds.has(project.id)" class="cover-placeholder"><el-icon class="is-loading"><Loading /></el-icon><span>正在加载封面</span></div>
                  <div v-else class="cover-placeholder"><el-icon><Picture /></el-icon><span>{{ project.coverUrl ? '封面暂不可用' : '暂未设置封面' }}</span></div>
                </div>
                <div class="cover-preview-footer">
                  <div class="cover-preview-info">
                    <strong>{{ project.name }}</strong>
                    <span>{{ categoryName(project.categoryId) }} · {{ statusText(project.status) }}</span>
                  </div>
                  <el-button
                    v-permission="'project:cover_manage'"
                    size="small"
                    plain
                    type="primary"
                    class="cover-editor-button"
                    title="编辑项目封面"
                    aria-label="编辑项目封面"
                    :loading="updatingCoverId === project.id"
                    @click.stop="chooseProjectCover(project)"
                  >
                    更新封面
                  </el-button>
                </div>
              </div>
            </el-popover>
          </el-col>
        </el-row>

        <el-table v-if="viewMode === 'table'" :data="projects" stripe class="project-table" empty-text="暂无符合条件的项目" @row-click="openProjectRow">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="封面" width="82">
            <template #default="{ row }">
              <div class="table-cover" :class="{ available: row.coverUrl }" @mouseenter="loadProjectCover(row)">
                <img v-if="coverObjectUrls[row.id]" :src="coverObjectUrls[row.id]" :alt="`${row.name}封面`" />
                <el-icon v-else><Picture /></el-icon>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="项目名称" min-width="180">
            <template #default="{ row }"><el-link type="primary" @click.stop="$router.push(`/projects/${row.id}`)">{{ row.name }}</el-link></template>
          </el-table-column>
          <el-table-column label="分类" width="130"><template #default="{ row }">{{ categoryName(row.categoryId) }}</template></el-table-column>
          <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="创建时间" width="130"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="132" fixed="right">
            <template #default="{ row }">
              <div class="table-actions" @click.stop>
                <el-button size="small" text type="primary" @click="$router.push(`/projects/${row.id}`)">详情</el-button>
                <el-popconfirm v-permission="'project:delete'" title="确定删除该项目？" @confirm="deleteProject(row.id)">
                  <template #reference><el-button size="small" text type="danger">删除</el-button></template>
                </el-popconfirm>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="project-pagination">
        <span class="page-current">本页 <strong>{{ projects.length }}</strong> 个 · 共 <strong>{{ total }}</strong> 个 · 第 {{ page }} / {{ projectTotalPages }} 页</span>
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" :pager-count="5" layout="prev, pager, next" @current-change="loadProjects" />
      </div>
    </el-card>

    <!-- Create Project Dialog -->
    <el-dialog v-model="dialogVisible" title="新建项目" width="520px" @closed="resetCreateForm">
      <el-form :model="form" label-width="90px">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.name" placeholder="项目名称" />
        </el-form-item>
        <el-form-item label="项目分类">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="规划中" value="planning" />
            <el-option label="进行中" value="in_progress" />
            <el-option label="已完成" value="completed" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="项目封面">
          <div class="create-cover-picker">
            <button type="button" class="create-cover-button" @click="newCoverInputRef?.click()">
              <el-icon><Picture /></el-icon>
              <span>{{ newCoverFile ? '更换封面图片' : '选择封面图片' }}</span>
            </button>
            <span v-if="newCoverFile" class="selected-cover-name" :title="newCoverFile.name">{{ newCoverFile.name }}</span>
            <small>支持 JPG、PNG、WebP、GIF，最大 8 MB</small>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="'project:create'" type="primary" :loading="creating" @click="createProject">创建</el-button>
      </template>
    </el-dialog>

    <input ref="newCoverInputRef" class="hidden-file-input" type="file" accept="image/jpeg,image/png,image/webp,image/gif" @change="handleNewCoverSelected" />
    <input ref="coverInputRef" class="hidden-file-input" type="file" accept="image/jpeg,image/png,image/webp,image/gif" @change="handleProjectCoverSelected" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { projectApi } from '@/api/project'

const router = useRouter()
const projects = ref<any[]>([])
const categories = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const categoryId = ref<number | null>(null)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const viewMode = ref<'card' | 'table'>('card')
const scopeFilter = ref('')
const dialogVisible = ref(false)
const creating = ref(false)
const form = reactive({ name: '', categoryId: null as number | null, status: 'planning', description: '' })
const newCoverInputRef = ref<HTMLInputElement | null>(null)
const newCoverFile = ref<File | null>(null)
const coverInputRef = ref<HTMLInputElement | null>(null)
const coverTarget = ref<any>(null)
const updatingCoverId = ref<number | null>(null)
const coverObjectUrls = reactive<Record<number, string>>({})
const coverLoadingIds = reactive(new Set<number>())
const coverFailedIds = reactive(new Set<number>())
const scopeOptions = [
  { label: '全部项目', value: '', icon: 'FolderOpened' },
  { label: '我创建的', value: 'created', icon: 'UserFilled' },
  { label: '我参与的', value: 'participated', icon: 'User' },
  { label: '已完成的', value: 'completed', icon: 'CircleCheckFilled' },
]
const projectTotalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const loadProjects = async () => {
  loading.value = true
  try {
    const params: any = { page: page.value - 1, size: size.value }
    if (keyword.value) params.keyword = keyword.value
    if (categoryId.value) params.categoryId = categoryId.value
    if (scopeFilter.value) params.scope = scopeFilter.value
    const res = await projectApi.getProjects(params)
    const nextProjects = res.data.list || []
    const activeProjectIds = new Set(nextProjects.map((project: any) => Number(project.id)))
    Object.keys(coverObjectUrls).forEach(key => {
      const id = Number(key)
      if (!activeProjectIds.has(id)) revokeProjectCoverUrl(id)
    })
    projects.value = nextProjects
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  const res = await projectApi.getCategories()
  categories.value = res.data
}

const handleFilterChange = () => {
  page.value = 1
  loadProjects()
}

const selectScope = (scope: string) => {
  if (scopeFilter.value === scope) return
  scopeFilter.value = scope
  page.value = 1
  loadProjects()
}

const handlePageSizeChange = (value: string | number | boolean | undefined) => {
  const nextSize = Number(value)
  if (!Number.isFinite(nextSize) || nextSize <= 0) return
  size.value = nextSize
  page.value = 1
  loadProjects()
}

const categoryName = (id: number | null | undefined) => categories.value.find(c => c.id === id)?.name || '未分类'
const openProjectRow = (row: any) => router.push(`/projects/${row.id}`)

const validateCoverFile = (file: File) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
  if (!allowedTypes.includes(file.type)) {
    ElMessage.warning('封面仅支持 JPG、PNG、WebP 或 GIF 图片')
    return false
  }
  if (file.size > 8 * 1024 * 1024) {
    ElMessage.warning('封面图片不能超过 8 MB')
    return false
  }
  return true
}

const revokeProjectCoverUrl = (projectId: number) => {
  const objectUrl = coverObjectUrls[projectId]
  if (objectUrl) URL.revokeObjectURL(objectUrl)
  delete coverObjectUrls[projectId]
}

const loadProjectCover = async (project: any, force = false) => {
  const projectId = Number(project.id)
  if (!project.coverUrl) return
  if (force) {
    revokeProjectCoverUrl(projectId)
    coverFailedIds.delete(projectId)
  }
  if (coverObjectUrls[projectId] || coverLoadingIds.has(projectId) || coverFailedIds.has(projectId)) return
  coverLoadingIds.add(projectId)
  try {
    const response = await projectApi.getProjectCover(projectId)
    coverObjectUrls[projectId] = URL.createObjectURL(response.data)
  } catch {
    coverFailedIds.add(projectId)
  } finally {
    coverLoadingIds.delete(projectId)
  }
}

const chooseProjectCover = (project: any) => {
  coverTarget.value = project
  if (!coverInputRef.value) return
  coverInputRef.value.value = ''
  coverInputRef.value.click()
}

const handleProjectCoverSelected = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const project = coverTarget.value
  if (!file || !project || !validateCoverFile(file)) {
    input.value = ''
    coverTarget.value = null
    return
  }
  updatingCoverId.value = Number(project.id)
  try {
    const response = await projectApi.updateProjectCover(project.id, file)
    Object.assign(project, response.data)
    await loadProjectCover(project, true)
    ElMessage.success('项目封面已更新')
  } finally {
    updatingCoverId.value = null
    coverTarget.value = null
    input.value = ''
  }
}

const handleNewCoverSelected = (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !validateCoverFile(file)) return
  newCoverFile.value = file
}

const resetCreateForm = () => {
  form.name = ''
  form.categoryId = null
  form.status = 'planning'
  form.description = ''
  newCoverFile.value = null
  if (newCoverInputRef.value) newCoverInputRef.value.value = ''
}

const createProject = async () => {
  if (!form.name) return ElMessage.warning('请输入项目名称')
  creating.value = true
  try {
    const response = await projectApi.createProject(form)
    if (newCoverFile.value && response.data?.id) {
      try {
        await projectApi.updateProjectCover(Number(response.data.id), newCoverFile.value)
      } catch {
        ElMessage.warning('项目已创建，但封面上传失败，可在项目详情的项目信息中重新设置')
      }
    }
    ElMessage.success('项目创建成功')
    dialogVisible.value = false
    page.value = 1
    await loadProjects()
  } finally {
    creating.value = false
  }
}

const deleteProject = async (id: number) => {
  await projectApi.deleteProject(id)
  ElMessage.success('删除成功')
  loadProjects()
}

const statusText = (s: string) => ({
  planning: '规划中', in_progress: '进行中', completed: '已完成', archived: '已归档'
}[s] || s)
const statusType = (s: string) => ({
  planning: 'info', in_progress: 'primary', completed: 'success', archived: 'warning'
}[s] || 'info')
const formatDate = (t: string) => t ? new Date(t).toLocaleDateString('zh-CN') : ''

onMounted(() => {
  loadProjects()
  loadCategories()
})

onBeforeUnmount(() => {
  Object.values(coverObjectUrls).forEach(objectUrl => URL.revokeObjectURL(objectUrl))
})
</script>

<style scoped>
.page-container { min-width: 0; }
.project-list-card { overflow: hidden; border: 1px solid #dfeae5; border-radius: 14px; box-shadow: 0 12px 34px rgba(31, 76, 61, .06); }
.project-list-card :deep(.el-card__header) { padding: 16px 20px; border-bottom-color: #e8f0ed; }
.project-list-card :deep(.el-card__body) { padding: 18px 20px 14px; }
.card-header { min-width: 0; display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.header-title { min-width: 112px; display: flex; flex-direction: column; gap: 3px; }
.header-title strong { color: #203c32; font-size: 17px; }
.header-title small { color: #8da098; font-size: 11px; font-weight: 400; }
.header-actions { min-width: 0; display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.category-filter { width: 140px; }
.project-search { width: 210px; }
.view-settings-trigger {
  height: 32px; display: inline-flex; flex: none; align-items: center; gap: 6px; padding: 0 10px;
  color: #526b61; background: #f6faf8; border: 1px solid #dbe9e3; border-radius: 8px;
  font-size: 12px; cursor: pointer; transition: all .2s ease;
}
.view-settings-trigger:hover { color: #1f8067; border-color: #9bcfbe; background: #eef8f4; }
:global(.project-view-popper .view-settings-panel) { display: grid; gap: 10px; }
:global(.project-view-popper .view-settings-panel > strong) { color: #29453a; font-size: 12px; }
:global(.project-view-popper .project-view-switch) { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; padding: 3px; border: 1px solid #dfe9e5; border-radius: 9px; background: #f5f9f7; }
:global(.project-view-popper .project-view-switch button) { height: 30px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 0 10px; color: #71847d; background: transparent; border: 0; border-radius: 7px; cursor: pointer; }
:global(.project-view-popper .project-view-switch button.active) { color: #1f8067; background: #fff; box-shadow: 0 2px 7px rgba(15,23,42,.09); }
:global(.project-view-popper .page-size-options) { width: 100%; display: grid !important; grid-template-columns: repeat(4, minmax(0, 1fr)); }
:global(.project-view-popper .page-size-options .el-radio-button),
:global(.project-view-popper .page-size-options .el-radio-button__inner) { width: 100%; }
:global(.project-view-popper .page-size-options .el-radio-button__inner) { padding-right: 0; padding-left: 0; }
.scope-filter-bar { display: flex; align-items: center; gap: 12px; margin: -2px 0 18px; padding: 0 0 14px; border-bottom: 1px solid #edf2f0; }
.scope-filter-label { color: #7a8e86; font-size: 12px; white-space: nowrap; }
.scope-filter-buttons { display: inline-flex; flex-wrap: wrap; gap: 6px; padding: 3px; border: 1px solid #dfe9e5; border-radius: 10px; background: #f6faf8; }
.scope-filter-buttons button { height: 30px; display: inline-flex; align-items: center; gap: 5px; padding: 0 13px; color: #657a72; background: transparent; border: 0; border-radius: 7px; font-size: 12px; cursor: pointer; transition: color .2s ease, background .2s ease, box-shadow .2s ease; }
.scope-filter-buttons button:hover { color: #1f8067; }
.scope-filter-buttons button.active { color: #fff; background: #238b70; box-shadow: 0 4px 10px rgba(35, 139, 112, .22); }
.project-content { min-height: 320px; }
.project-grid { margin-bottom: -16px; }
.project-grid-item { margin-bottom: 16px; }
.project-grid-item :deep(.el-popper) { max-width: min(286px, calc(100vw - 24px)); }
.project-card { min-height: 220px; height: 100%; cursor: pointer; border: 1px solid #e2ebe7; border-radius: 12px; transition: transform .22s ease, border-color .22s ease, box-shadow .22s ease; }
.project-card :deep(.el-card__body) { height: 100%; display: flex; flex-direction: column; padding: 16px; }
.project-card:hover { border-color: #91cdb8; box-shadow: 0 12px 28px rgba(35, 139, 112, .13); transform: translateY(-3px); }
.project-card-heading { min-width: 0; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.project-card h3 { min-width: 0; margin: 4px 0 10px; overflow: hidden; color: #20362e; font-size: 17px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.project-category { display: flex; align-items: center; gap: 5px; color: #6f857c; font-size: 12px; }
.project-card p { min-height: 44px; display: -webkit-box; margin: 18px 0 20px; overflow: hidden; color: #84948e; font-size: 12px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.project-card-footer { min-width: 0; display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: auto; padding-top: 11px; border-top: 1px solid #edf2f0; }
.project-card-footer > span { display: flex; align-items: center; gap: 4px; color: #8da098; font-size: 11px; white-space: nowrap; }
.project-card-actions { display: flex; align-items: center; }
.project-card-actions .el-button, .table-actions .el-button { min-height: 26px; margin-left: 0; padding: 4px 6px; }
.project-table { cursor: pointer; }
.table-cover { width: 52px; height: 34px; display: grid; place-items: center; overflow: hidden; color: #9aacA5; background: #f1f6f4; border: 1px dashed #cfddd7; border-radius: 6px; font-size: 16px; }
.table-cover.available { color: #238b70; border-style: solid; border-color: #afd8ca; }
.table-cover img { width: 100%; height: 100%; display: block; object-fit: cover; }
.create-cover-picker { min-width: 0; width: 100%; display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 7px 10px; }
.create-cover-button { height: 34px; display: inline-flex; align-items: center; gap: 6px; padding: 0 12px; color: #238b70; background: #f1faf7; border: 1px solid #b8deD1; border-radius: 8px; cursor: pointer; }
.create-cover-button:hover { background: #e7f6f1; border-color: #79bea8; }
.selected-cover-name { min-width: 0; overflow: hidden; color: #526b61; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.create-cover-picker small { grid-column: 1 / -1; color: #98a8a2; font-size: 11px; }
.hidden-file-input { display: none; }
.project-pagination { min-height: 50px; display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 14px; padding-top: 10px; border-top: 1px solid #edf2f0; }
.page-current { color: #70837b; font-size: 12px; white-space: nowrap; }
:global(.project-cover-popper) { padding: 10px !important; overflow: hidden; border-color: #dce9e4 !important; border-radius: 12px !important; box-shadow: 0 16px 38px rgba(22, 62, 49, .18) !important; }
:global(.project-cover-popper .project-cover-preview) { display: grid; gap: 10px; }
:global(.project-cover-popper .cover-preview-media) { height: 150px; overflow: hidden; background: linear-gradient(145deg, #eef6f3, #e2ede9); border-radius: 9px; }
:global(.project-cover-popper .cover-preview-media img) { width: 100%; height: 100%; display: block; object-fit: cover; }
:global(.project-cover-popper .cover-placeholder) { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 8px; color: #83978f; font-size: 12px; }
:global(.project-cover-popper .cover-placeholder .el-icon) { color: #4f9d84; font-size: 28px; }
:global(.project-cover-popper .cover-preview-info) { min-width: 0; display: grid; gap: 3px; }
:global(.project-cover-popper .cover-preview-info strong) { overflow: hidden; color: #203c32; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
:global(.project-cover-popper .cover-preview-info span) { color: #84968f; font-size: 11px; }
:global(.project-cover-popper .cover-preview-footer) { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
:global(.project-cover-popper .cover-editor-button) { flex: 0 0 auto; min-width: 76px; height: 30px; margin: 0; padding: 0 12px; }
@media (max-width: 980px) {
  .card-header { align-items: flex-start; flex-direction: column; }
  .header-actions { width: 100%; justify-content: flex-start; flex-wrap: wrap; }
  .project-search { min-width: 220px; flex: 1; }
}
@media (max-width: 620px) {
  .category-filter, .project-search { width: 100%; }
  .project-search { flex-basis: 100%; }
  .scope-filter-bar { align-items: flex-start; flex-direction: column; }
  .scope-filter-buttons { width: 100%; display: grid; grid-template-columns: 1fr 1fr; }
  .scope-filter-buttons button { justify-content: center; }
  .project-pagination { align-items: center; flex-direction: column; }
}
</style>
