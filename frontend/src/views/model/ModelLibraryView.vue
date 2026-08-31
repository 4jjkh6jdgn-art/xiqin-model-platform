<template>
  <div class="model-library">
    <!-- Search Results (files) -->
    <el-card v-if="fileResults.length > 0" class="file-result-card">
      <template #header>
        <div class="card-header">
          <span>文件搜索结果（{{ fileResults.length }}）</span>
          <el-button size="small" text @click="fileResults = []">关闭</el-button>
        </div>
      </template>
      <el-table :data="fileResults" stripe size="small">
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="modelName" label="所属模型" width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/models/${row.modelId}`)">{{ row.modelName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.fileType === 'display' ? 'success' : row.fileType === 'texture' ? 'warning' : 'info'" size="small">
              {{ row.fileType === 'display' ? '模型' : row.fileType === 'texture' ? '贴图' : '其他' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileFormat" label="格式" width="80" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="20" class="library-workspace">
      <!-- Category Sidebar -->
      <el-col v-if="!isSmallScreen" :span="categorySpan" class="category-column">
        <el-card class="category-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>分类浏览</span>
            </div>
          </template>
          <el-radio-group v-model="categoryMode" size="small" class="mode-switch" @change="handleModeChange">
            <el-radio-button value="model">模型分类</el-radio-button>
            <el-radio-button value="projectCategory">项目分类</el-radio-button>
            <el-radio-button value="project">关联项目</el-radio-button>
          </el-radio-group>
          <el-input v-model="categorySearch" size="small" :placeholder="categoryMode === 'project' ? '搜索项目...' : '搜索分类...'" clearable class="category-search" @input="handleCategorySearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <div class="sort-controls" role="group" aria-label="模型排序">
            <button type="button" :class="{ active: sortField === 'time' }" @click="toggleSort('time')">
              <span class="sort-arrow">{{ sortField === 'time' ? (sortDirection === 'desc' ? '↓' : '↑') : '↕' }}</span>时间
            </button>
            <button type="button" :class="{ active: sortField === 'name' }" @click="toggleSort('name')">
              <span class="sort-arrow">{{ sortField === 'name' ? (sortDirection === 'desc' ? '↓' : '↑') : '↕' }}</span>名称
            </button>
          </div>
          <div class="category-list-scroll">
            <el-menu :default-active="String(selectedCategoryId || 'all')" @select="handleCategorySelect" class="category-menu">
              <el-menu-item index="all">
                <el-icon><Files /></el-icon>
                <span class="category-label">全部模型</span>
                <span class="category-count">{{ libraryStats.totalModels || 0 }}</span>
              </el-menu-item>
              <el-menu-item v-for="cat in visibleCategories" :key="cat.id" :index="String(cat.id)">
                <el-icon><Folder /></el-icon>
                <span class="category-label">{{ cat.name }}</span>
                <span class="category-count">{{ categoryCount(cat.id) }}</span>
              </el-menu-item>
            </el-menu>
          </div>
          <div class="category-pagination">
            <el-button size="small" :disabled="categoryPage <= 1" @click="categoryPage--">上一页</el-button>
            <span>{{ categoryPage }} / {{ categoryTotalPages }}</span>
            <el-button size="small" :disabled="categoryPage >= categoryTotalPages" @click="categoryPage++">下一页</el-button>
          </div>
        </el-card>
      </el-col>

      <!-- Model Grid -->
      <el-col :span="modelsSpan" class="models-column">
        <el-card shadow="never" class="library-panel">
          <template #header>
            <div class="toolbar">
              <div class="toolbar-left">
                <el-button v-if="isSmallScreen" size="small" @click="showCategoryDrawer = true">
                  <el-icon><Menu /></el-icon>&nbsp;分类
                </el-button>
                <template v-if="!selectionMode">
                  <el-radio-group v-model="statusFilter" size="small" @change="handleStatusChange">
                    <el-radio-button value="">全部</el-radio-button>
                    <el-radio-button value="ready">可用</el-radio-button>
                    <el-radio-button value="processing">处理中</el-radio-button>
                    <el-radio-button value="error">失败</el-radio-button>
                    <el-radio-button value="draft">草稿</el-radio-button>
                  </el-radio-group>
                  <el-button class="status-refresh" size="small" title="刷新模型列表" @click="loadModels" :loading="loading">
                    <el-icon><Refresh /></el-icon><span>刷新</span>
                  </el-button>
                  <el-button v-permission="'model:delete'" size="small" @click="toggleSelectionMode">
                    <el-icon><Checked /></el-icon>&nbsp;批量选择
                  </el-button>
                </template>
                <template v-else>
                  <div class="batch-toolbar">
                    <el-button size="small" @click="selectAllCurrent">
                      {{ allCurrentSelected ? '取消全选' : '全选当前页' }}
                    </el-button>
                    <el-button size="small" @click="clearSelection" :disabled="selectedCount === 0">清空选择</el-button>
                    <span class="batch-count">已选 <strong>{{ selectedCount }}</strong> 项</span>
                    <el-button v-permission="'model:delete'" type="danger" size="small" :disabled="selectedCount === 0" :loading="batchDeleting" @click="batchDelete">
                      <el-icon><Delete /></el-icon>&nbsp;批量删除
                    </el-button>
                    <el-button size="small" @click="toggleSelectionMode">退出选择</el-button>
                  </div>
                </template>
              </div>
              <div class="toolbar-search">
                <el-input
                  v-model="searchKeyword"
                  placeholder="搜索模型、文件名（支持 .fbx / .obj / .gltf）"
                  clearable
                  @keyup.enter="handleSearch"
                  @clear="handleSearchClear"
                >
                  <template #prefix><el-icon><Search /></el-icon></template>
                  <template #append><el-button @click="handleSearch">搜索</el-button></template>
                </el-input>
              </div>
              <div class="toolbar-right" v-if="!selectionMode">
                <el-popover placement="bottom-end" trigger="click" :width="240" popper-class="library-view-popper">
                  <template #reference>
                    <button type="button" class="view-settings-trigger" title="模型排列方式与每页数量">
                      <el-icon><Grid v-if="viewMode === 'card'" /><List v-else /></el-icon>
                      <span>{{ size }} / 页</span>
                      <el-icon><ArrowDown /></el-icon>
                    </button>
                  </template>
                  <div class="view-settings-panel">
                    <strong>排列方式</strong>
                    <div class="library-view-switch" role="group" aria-label="模型排列方式">
                      <button type="button" :class="{ active: viewMode === 'card' }" @click="viewMode = 'card'"><el-icon><Grid /></el-icon>卡片</button>
                      <button type="button" :class="{ active: viewMode === 'table' }" @click="viewMode = 'table'"><el-icon><List /></el-icon>表格</button>
                    </div>
                    <strong>每页显示</strong>
                    <el-radio-group :model-value="size" size="small" class="page-size-options" @change="handlePageSizeChange">
                      <el-radio-button v-for="option in [5, 10, 20, 30]" :key="option" :value="option">{{ option }}</el-radio-button>
                    </el-radio-group>
                  </div>
                </el-popover>
                <el-dropdown v-permission="'model:upload'" trigger="click" @command="handleNewCommand">
                  <el-button type="primary" size="small">
                    <el-icon><Plus /></el-icon>&nbsp;新增<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="single">
                        <el-icon><Upload /></el-icon>&nbsp;单个上传
                      </el-dropdown-item>
                      <el-dropdown-item command="batch">
                        <el-icon><FolderOpened /></el-icon>&nbsp;批量上传
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </template>

          <div v-loading="loading" class="grid-wrap">
            <el-empty v-if="models.length === 0 && !loading" description="暂无符合条件的模型">
              <el-button v-permission="'model:upload'" type="primary" @click="showUpload = true">去上传第一个模型</el-button>
            </el-empty>
            <el-row v-if="viewMode === 'card'" :gutter="16">
              <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="model in models" :key="model.id" class="model-grid-item">
                <el-card
                  shadow="hover"
                  class="model-card"
                  :class="{ 'model-card-selected': selectionMode && selectedIds.has(model.id), 'model-card-selectable': selectionMode }"
                  @click="selectionMode ? toggleSelect(model.id) : $router.push(`/models/${model.id}`)"
                >
                  <div class="model-thumb">
                    <img v-if="model.thumbnailUrl" :src="model.thumbnailUrl" alt="" />
                    <div v-else class="model-placeholder">
                      <el-icon :size="40"><Box /></el-icon>
                    </div>
                    <div class="format-tags" v-if="!selectionMode">
                      <span v-if="model.categoryName" class="format-tag category">{{ model.categoryName }}</span>
                    </div>
                    <span class="file-count" v-if="!selectionMode" title="资源文件数量"><el-icon><Document /></el-icon>{{ model.fileCount || 0 }}</span>
                    <div class="thumb-metrics" v-if="!selectionMode">
                      <span>{{ formatSize(model.fileSize) }}</span>
                      <span title="下载次数"><el-icon><Download /></el-icon>{{ model.downloadCount || 0 }}</span>
                    </div>
                    <!-- 左上角选择框 -->
                    <div v-if="selectionMode" class="card-checkbox" :class="{ checked: selectedIds.has(model.id) }" @click.stop="toggleSelect(model.id)">
                      <el-icon v-if="selectedIds.has(model.id)"><Check /></el-icon>
                    </div>
                    <div class="card-actions" @click.stop v-if="!selectionMode">
                      <el-button v-permission="'model:edit'" size="small" circle title="编辑" @click="openEdit(model)">
                        <el-icon><Edit /></el-icon>
                      </el-button>
                      <el-button v-permission="'model:delete'" size="small" circle type="danger" title="删除" @click="removeModel(model)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                  </div>
                  <div class="model-info">
                    <div class="model-title-row">
                      <div class="model-name" :title="model.name">{{ model.name }}</div>
                      <span class="model-version">v{{ model.version }}</span>
                    </div>
                    <div class="model-project">
                      <el-icon><FolderOpened /></el-icon>{{ model.projectName || '未关联项目' }}
                    </div>
                    <div class="model-meta">
                      <span class="creator"><el-icon><User /></el-icon>{{ model.createdByName || '系统导入' }}</span>
                      <span>{{ relativeTime(model.updatedAt || model.createdAt) }}</span>
                    </div>
                    <span class="status-line" :class="`status-${model.status}`"><i></i>{{ statusText(model.status) }}</span>
                  </div>
                </el-card>
              </el-col>
            </el-row>

            <el-table v-else :data="models" class="model-table" row-key="id" empty-text="暂无符合条件的模型" @row-click="openModelRow">
              <el-table-column v-if="selectionMode" width="50" align="center">
                <template #header>
                  <el-checkbox :model-value="allCurrentSelected" :indeterminate="selectedCount > 0 && !allCurrentSelected" @change="selectAllCurrent" />
                </template>
                <template #default="{ row }">
                  <el-checkbox :model-value="selectedIds.has(row.id)" @click.stop @change="toggleSelect(row.id)" />
                </template>
              </el-table-column>
              <el-table-column label="预览" width="116"><template #default="{ row }"><div class="table-cover"><img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt=""/><el-icon v-else><Box /></el-icon></div></template></el-table-column>
              <el-table-column prop="name" label="模型名称" min-width="180" show-overflow-tooltip><template #default="{ row }"><div class="table-model-name"><strong>{{ row.name }}</strong><small>v{{ row.version || 1 }}</small></div></template></el-table-column>
              <el-table-column label="模型分类" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ row.categoryName || '未分类' }}</template></el-table-column>
              <el-table-column label="关联项目" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.projectName || '未关联项目' }}</template></el-table-column>
              <el-table-column label="文件" width="115"><template #default="{ row }"><div class="table-file-stat"><strong>{{ row.fileCount || 0 }} 个</strong><small>{{ formatSize(row.fileSize) }}</small></div></template></el-table-column>
              <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
              <el-table-column label="最近更新" width="145"><template #default="{ row }">{{ relativeTime(row.updatedAt || row.createdAt) }}</template></el-table-column>
              <el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><div class="table-actions" @click.stop><el-button v-permission="'model:edit'" link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-permission="'model:delete'" link type="danger" @click="removeModel(row)">删除</el-button></div></template></el-table-column>
            </el-table>

          </div>
          <div class="library-pagination">
            <span class="page-current">
              本页 <strong>{{ models.length }}</strong> 个 · 共 <strong>{{ total }}</strong> 个 · 第 {{ page }} / {{ modelTotalPages }} 页
            </span>
            <el-pagination
              v-model:current-page="page"
              :page-size="size"
              :total="total"
              :pager-count="5"
              layout="prev, pager, next"
              @current-change="loadModels"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Mobile Category Drawer -->
    <el-drawer v-model="showCategoryDrawer" title="分类浏览" direction="ltr" size="280px" class="category-drawer">
      <el-radio-group v-model="categoryMode" size="small" class="mode-switch" @change="handleModeChange">
        <el-radio-button value="model">模型分类</el-radio-button>
        <el-radio-button value="projectCategory">项目分类</el-radio-button>
        <el-radio-button value="project">关联项目</el-radio-button>
      </el-radio-group>
      <el-input v-model="categorySearch" size="small" :placeholder="categoryMode === 'project' ? '搜索项目...' : '搜索分类...'" clearable class="category-search" @input="handleCategorySearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="sort-controls" role="group" aria-label="模型排序">
        <button type="button" :class="{ active: sortField === 'time' }" @click="toggleSort('time')">
          <span class="sort-arrow">{{ sortField === 'time' ? (sortDirection === 'desc' ? '↓' : '↑') : '↕' }}</span>时间
        </button>
        <button type="button" :class="{ active: sortField === 'name' }" @click="toggleSort('name')">
          <span class="sort-arrow">{{ sortField === 'name' ? (sortDirection === 'desc' ? '↓' : '↑') : '↕' }}</span>名称
        </button>
      </div>
      <div class="category-list-scroll">
        <el-menu :default-active="String(selectedCategoryId || 'all')" @select="handleCategorySelect" class="category-menu">
          <el-menu-item index="all">
            <el-icon><Files /></el-icon>
            <span class="category-label">全部模型</span>
            <span class="category-count">{{ libraryStats.totalModels || 0 }}</span>
          </el-menu-item>
          <el-menu-item v-for="cat in visibleCategories" :key="cat.id" :index="String(cat.id)">
            <el-icon><Folder /></el-icon>
            <span class="category-label">{{ cat.name }}</span>
            <span class="category-count">{{ categoryCount(cat.id) }}</span>
          </el-menu-item>
        </el-menu>
      </div>
      <div class="category-pagination">
        <el-button size="small" :disabled="categoryPage <= 1" @click="categoryPage--">上一页</el-button>
        <span>{{ categoryPage }} / {{ categoryTotalPages }}</span>
        <el-button size="small" :disabled="categoryPage >= categoryTotalPages" @click="categoryPage++">下一页</el-button>
      </div>
    </el-drawer>

    <!-- Quick Upload Dialog -->
    <QuickUploadDialog v-model="showUpload" :initial-mode="uploadMode" @success="handleUploadSuccess" />

    <!-- Edit Model Dialog -->
    <el-dialog v-model="showEdit" title="编辑模型" width="460px">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="模型分类">
          <el-select v-model="editForm.categoryIds" multiple collapse-tags collapse-tags-tooltip clearable placeholder="可选择多个分类" style="width: 100%">
            <el-option v-for="c in modelCategories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目">
          <el-select v-model="editForm.projectIds" multiple collapse-tags collapse-tags-tooltip clearable placeholder="可关联多个项目" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="草稿" value="draft" />
            <el-option label="处理中" value="processing" />
            <el-option label="可用" value="ready" />
            <el-option label="已归档" value="archived" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button v-permission="'model:edit'" type="primary" :loading="editLoading" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '@/api/model'
import { projectApi } from '@/api/project'
import QuickUploadDialog from '@/components/upload/QuickUploadDialog.vue'

const router = useRouter()
const windowWidth = ref(window.innerWidth)
const showCategoryDrawer = ref(false)
const searchKeyword = ref('')
const fileResults = ref<any[]>([])
const categoryMode = ref<'model' | 'projectCategory' | 'project'>('model')
const selectedCategoryId = ref<number | null>(null)
const categorySearch = ref('')
const statusFilter = ref('')
const viewMode = ref<'card' | 'table'>('card')
const sortField = ref<'time' | 'name'>('time')
const sortDirection = ref<'asc' | 'desc'>('desc')
const modelCategories = ref<any[]>([])
const projectCategories = ref<any[]>([])
const projects = ref<any[]>([])
const projectCounts = ref<Record<string, number>>({})
const libraryStats = ref<any>({
  totalModels: 0,
  modelCategoryCounts: {},
  projectCategoryCounts: {},
  totalStorageBytes: 0,
  memberCount: 0,
  downloadCount: 0,
})
const models = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const categoryPage = ref(1)
const categoryPageSize = 6
const showUpload = ref(false)
const uploadMode = ref<'single' | 'batch'>('single')
const showEdit = ref(false)
const editLoading = ref(false)
const editForm = reactive({ id: 0, name: '', categoryIds: [] as number[], projectIds: [] as number[], status: 'draft', description: '' })

// 批量选择
const selectionMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const batchDeleting = ref(false)

const selectedCount = computed(() => selectedIds.value.size)
const allCurrentSelected = computed(() => models.value.length > 0 && models.value.every(m => selectedIds.value.has(m.id)))

const toggleSelectionMode = () => {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) selectedIds.value.clear()
}

const toggleSelect = (id: number) => {
  if (selectedIds.value.has(id)) selectedIds.value.delete(id)
  else selectedIds.value.add(id)
  selectedIds.value = new Set(selectedIds.value)
}

const selectAllCurrent = () => {
  if (allCurrentSelected.value) {
    models.value.forEach(m => selectedIds.value.delete(m.id))
  } else {
    models.value.forEach(m => selectedIds.value.add(m.id))
  }
  selectedIds.value = new Set(selectedIds.value)
}

const clearSelection = () => {
  selectedIds.value.clear()
  selectedIds.value = new Set()
}

const batchDelete = async () => {
  if (selectedIds.value.size === 0) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedIds.value.size} 个模型吗？该操作不可恢复。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '全部删除', cancelButtonText: '取消' }
    )
  } catch { return }
  batchDeleting.value = true
  let success = 0, failed = 0
  const ids = Array.from(selectedIds.value)
  for (const id of ids) {
    try {
      await modelApi.deleteModel(id)
      success++
    } catch (e) {
      failed++
    }
  }
  batchDeleting.value = false
  if (failed === 0) ElMessage.success(`已删除 ${success} 个模型`)
  else ElMessage.warning(`删除完成：成功 ${success} 个，失败 ${failed} 个`)
  selectedIds.value.clear()
  selectionMode.value = false
  await Promise.all([loadModels(), refreshProjectCounts()])
}

const isSmallScreen = computed(() => windowWidth.value < 900)
const isMediumScreen = computed(() => windowWidth.value >= 900 && windowWidth.value < 1280)

const categorySpan = computed(() => {
  if (isSmallScreen.value) return 0
  if (isMediumScreen.value) return 7
  return 5
})

const modelsSpan = computed(() => {
  if (isSmallScreen.value) return 24
  if (isMediumScreen.value) return 17
  return 19
})

const currentCategories = computed(() => {
  if (categoryMode.value === 'model') return modelCategories.value
  if (categoryMode.value === 'projectCategory') return projectCategories.value
  return projects.value
})

const filteredCategories = computed(() => {
  if (!categorySearch.value.trim()) return currentCategories.value
  const kw = categorySearch.value.trim().toLowerCase()
  return currentCategories.value.filter((c: any) => c.name.toLowerCase().includes(kw))
})

const categoryTotalPages = computed(() => Math.max(1, Math.ceil(filteredCategories.value.length / categoryPageSize)))

const visibleCategories = computed(() => {
  const safePage = Math.min(categoryPage.value, categoryTotalPages.value)
  const start = (safePage - 1) * categoryPageSize
  return filteredCategories.value.slice(start, start + categoryPageSize)
})

const modelTotalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const handleCategorySearch = () => {
  categoryPage.value = 1
  // 搜索时如果当前选中的分类被过滤掉了，重置为全部
  if (selectedCategoryId.value && !filteredCategories.value.find((c: any) => c.id === selectedCategoryId.value)) {
    selectedCategoryId.value = null
    page.value = 1
    loadModels()
  }
}

const handleNewCommand = (command: string) => {
  uploadMode.value = command === 'batch' ? 'batch' : 'single'
  showUpload.value = true
}

const handlePageSizeChange = (value: string | number | boolean | undefined) => {
  const nextSize = Number(value)
  if (![5, 10, 20, 30].includes(nextSize)) return
  size.value = nextSize
  page.value = 1
  loadModels()
}

const categoryCount = (id: number) => {
  const countMap = categoryMode.value === 'model'
    ? libraryStats.value.modelCategoryCounts
    : categoryMode.value === 'projectCategory'
      ? libraryStats.value.projectCategoryCounts
      : projectCounts.value
  return Number(countMap?.[String(id)] || 0)
}

const toggleSort = (field: 'time' | 'name') => {
  if (sortField.value === field) sortDirection.value = sortDirection.value === 'desc' ? 'asc' : 'desc'
  else {
    sortField.value = field
    sortDirection.value = field === 'time' ? 'desc' : 'asc'
  }
  page.value = 1
  loadModels()
}

const handleSearch = async () => {
  page.value = 1
  await loadModels()
  if (!searchKeyword.value.trim()) { fileResults.value = []; return }
  try {
    const res = await modelApi.searchFiles(searchKeyword.value.trim())
    fileResults.value = res.data
  } catch (e) { /* ignore */ }
}

const handleSearchClear = () => {
  fileResults.value = []
  page.value = 1
  loadModels()
}

const handleModeChange = () => {
  selectedCategoryId.value = null
  categoryPage.value = 1
  page.value = 1
  loadModels()
}

const handleStatusChange = () => {
  page.value = 1
  loadModels()
}

const handleCategorySelect = (index: string) => {
  selectedCategoryId.value = index === 'all' ? null : Number(index)
  page.value = 1
  showCategoryDrawer.value = false
  loadModels()
}

const openModelRow = (row: any) => {
  if (selectionMode.value) toggleSelect(row.id)
  else router.push(`/models/${row.id}`)
}

const loadModels = async () => {
  loading.value = true
  try {
    const params: any = {
      page: page.value - 1,
      size: size.value,
      sortField: sortField.value,
      sortDirection: sortDirection.value,
    }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (statusFilter.value) params.status = statusFilter.value
    if (selectedCategoryId.value) {
      if (categoryMode.value === 'model') params.categoryId = selectedCategoryId.value
      else if (categoryMode.value === 'projectCategory') params.projectCategoryId = selectedCategoryId.value
      else params.projectId = selectedCategoryId.value
    }
    const [res, stats] = await Promise.all([
      modelApi.getModels(params),
      modelApi.getLibraryStats(),
    ])
    models.value = res.data.list
    total.value = res.data.total
    libraryStats.value = stats.data
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  const [mc, pc, ps] = await Promise.all([
    modelApi.getCategories(),
    projectApi.getCategories(),
    projectApi.getProjects({ page: 0, size: 200 }),
  ])
  modelCategories.value = mc.data
  projectCategories.value = pc.data
  projects.value = ps.data.list
  await refreshProjectCounts()
}

const refreshProjectCounts = async () => {
  const countResults = await Promise.all(projects.value.map(async (project: any) => {
    try {
      const response = await modelApi.getModels({ projectId: project.id, page: 0, size: 1 })
      return [String(project.id), Number(response.data.total || 0)] as const
    } catch {
      return [String(project.id), 0] as const
    }
  }))
  projectCounts.value = Object.fromEntries(countResults)
}

const handleUploadSuccess = async () => {
  await Promise.all([loadModels(), refreshProjectCounts()])
}

const statusText = (s: string) => ({
  draft: '草稿', processing: '处理中', ready: '可用', archived: '已归档', error: '处理失败'
}[s] || s)

const statusType = (s: string) => ({
  draft: 'info', processing: 'warning', ready: 'success', archived: 'info', error: 'danger'
}[s] || 'info')

const openEdit = (m: any) => {
  editForm.id = m.id
  editForm.name = m.name
  editForm.categoryIds = (m.categoryIds?.length ? m.categoryIds : (m.categoryId ? [m.categoryId] : [])).map(Number)
  editForm.projectIds = (m.projectIds?.length ? m.projectIds : (m.projectId ? [m.projectId] : [])).map(Number)
  editForm.status = m.status
  editForm.description = m.description || ''
  showEdit.value = true
}

const submitEdit = async () => {
  if (!editForm.name) return ElMessage.warning('请输入模型名称')
  editLoading.value = true
  try {
    await modelApi.updateModel(editForm.id, {
      name: editForm.name,
      categoryIds: editForm.categoryIds,
      categoryId: editForm.categoryIds[0] ?? null,
      projectIds: editForm.projectIds,
      projectId: editForm.projectIds[0] ?? null,
      status: editForm.status,
      description: editForm.description
    })
    ElMessage.success('模型已更新')
    showEdit.value = false
    await Promise.all([loadModels(), refreshProjectCounts()])
  } finally {
    editLoading.value = false
  }
}

const removeModel = async (m: any) => {
  try {
    await ElMessageBox.confirm(`确定删除模型「${m.name}」吗？该操作不可恢复。`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch { return }
  await modelApi.deleteModel(m.id)
  ElMessage.success('模型已删除')
  await Promise.all([loadModels(), refreshProjectCounts()])
}

const formatSize = (bytes: number) => {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return `${size.toFixed(1)} ${units[i]}`
}

const relativeTime = (t: string) => {
  if (!t) return '暂无更新'
  const diff = Math.max(0, Date.now() - new Date(t).getTime())
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚更新'
  if (diff < hour) return `更新于 ${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `更新于 ${Math.floor(diff / hour)} 小时前`
  if (diff < 30 * day) return `更新于 ${Math.floor(diff / day)} 天前`
  return `更新于 ${new Date(t).toLocaleDateString('zh-CN')}`
}

const onResize = () => { windowWidth.value = window.innerWidth }

onMounted(() => {
  window.addEventListener('resize', onResize)
  loadCategories()
  loadModels()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.model-library {
  height: calc(100vh - 114px);
  min-height: 620px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 4px;
}
.file-result-card { max-height: 220px; flex: none; overflow: auto; margin-bottom: 14px; }
.library-workspace { min-height: 0; flex: 1; overflow: hidden; }
.category-column, .models-column { height: 100%; min-height: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.toolbar {
  display: grid;
  grid-template-columns: max-content minmax(220px, 1fr) max-content;
  align-items: center;
  gap: 12px;
}
.toolbar-left { min-width: 0; display: flex; align-items: center; gap: 8px; }
.toolbar-search { width: 100%; max-width: 520px; justify-self: center; }
.toolbar-search :deep(.el-input-group__append) { padding: 0; }
.toolbar-search :deep(.el-input-group__append .el-button) { margin: 0; padding: 0 16px; }
.status-refresh { margin-left: 0; }
.status-refresh .el-icon { margin-right: 4px; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }
.view-settings-trigger {
  height: 32px; display: inline-flex; align-items: center; gap: 6px; padding: 0 10px;
  color: #526b61; background: #f6faf8; border: 1px solid #dbe9e3; border-radius: 8px;
  font-size: 12px; cursor: pointer; transition: all .2s ease;
}
.view-settings-trigger:hover { color: #1f8067; border-color: #9bcfbe; background: #eef8f4; }
.view-settings-panel { display: grid; gap: 10px; }
.view-settings-panel strong { color: #29453a; font-size: 12px; }
.library-view-switch { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; padding: 3px; border: 1px solid #dfe9e5; border-radius: 9px; background: #f5f9f7; }
.library-view-switch button { height: 30px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 0 10px; color: #71847d; background: transparent; border: 0; border-radius: 7px; cursor: pointer; }
.library-view-switch button.active { color: #1f8067; background: #fff; box-shadow: 0 2px 7px rgba(15,23,42,.09); }
.page-size-options { width: 100%; display: grid !important; grid-template-columns: repeat(4, minmax(0, 1fr)); }
.page-size-options :deep(.el-radio-button), .page-size-options :deep(.el-radio-button__inner) { width: 100%; }
.page-size-options :deep(.el-radio-button__inner) { padding-right: 0; padding-left: 0; }
.category-card, .library-panel { height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.category-card :deep(.el-card__header), .library-panel :deep(.el-card__header) { flex: none; }
.category-card :deep(.el-card__body) { min-height: 0; display: flex; flex: 1; flex-direction: column; overflow: hidden; }
.library-panel :deep(.el-card__body) { min-height: 0; display: flex; flex: 1; flex-direction: column; overflow: hidden; }
.mode-switch { width: 100%; display: grid !important; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-bottom: 10px; }
.mode-switch :deep(.el-radio-button) { width: 100%; }
.mode-switch :deep(.el-radio-button__inner) { width: 100%; padding-right: 4px; padding-left: 4px; }
.category-search { margin-bottom: 10px; }
.sort-controls { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-bottom: 12px; }
.sort-controls button {
  height: 30px; color: #64748b; background: #f4f7fb; border: 1px solid #e2e8f0;
  border-radius: 8px; cursor: pointer; font-size: 11px; transition: all .2s;
}
.sort-controls button:hover { color: #1f8067; border-color: #9bcfbe; background: #eff9f5; }
.sort-controls button.active { color: #1f6f5b; border-color: #8fd1bb; background: #eaf7f1; box-shadow: inset 0 0 0 1px rgba(14,165,233,.08); }
.sort-arrow { margin-right: 5px; color: #238569; font-weight: 700; }
.category-list-scroll { min-height: 0; flex: 1; overflow-y: auto; padding-right: 3px; }
.category-menu { border-right: none; }
.category-menu :deep(.el-menu-item) { gap: 3px; padding-right: 12px !important; border-radius: 8px; }
.category-label { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; }
.category-count { min-width: 25px; padding: 2px 7px; color: #64748b; background: #eef2f7; border-radius: 999px; font-size: 10px; line-height: 16px; text-align: center; }
.category-menu :deep(.el-menu-item.is-active) .category-count { color: #fff; background: #1f8067; }
.category-pagination {
  min-height: 42px; display: flex; flex: none; align-items: center; justify-content: space-between;
  gap: 6px; margin-top: 8px; padding-top: 9px; color: #71847d; border-top: 1px solid #edf2f0; font-size: 11px;
}
.category-pagination :deep(.el-button) { margin: 0; padding: 5px 8px; }

.grid-wrap { min-height: 0; flex: 1; overflow-y: auto; overflow-x: hidden; padding: 2px 4px 0 0; }
.library-pagination {
  min-height: 48px; display: flex; flex: none; align-items: center; justify-content: space-between;
  gap: 12px; padding: 10px 4px 0; border-top: 1px solid #edf2f0;
}
.page-current { color: #70837b; font-size: 12px; white-space: nowrap; }
.model-grid-item { margin-bottom: 16px; }
.model-card {
  overflow: hidden; cursor: pointer; background: #fff; border: 1px solid #e3eaf3;
  border-radius: 12px; box-shadow: 0 4px 14px rgba(31, 45, 61, .06);
  transition: transform .24s ease, box-shadow .24s ease, border-color .24s ease;
}
.model-card :deep(.el-card__body) { padding: 0; }
.model-card:hover { transform: translateY(-3px); border-color: #9bcfbe; box-shadow: 0 14px 30px rgba(35, 139, 112, .12); }
.model-thumb {
  height: 178px; background: #edf4f1;
  display: flex; align-items: center; justify-content: center;
  position: relative; overflow: hidden;
}
.model-thumb::after { display: none; }
.model-thumb img { width: 100%; height: 100%; object-fit: cover; transition: transform .45s ease; }
.model-card:hover .model-thumb img { transform: scale(1.025); }
.card-actions {
  position: absolute; top: 42px; right: 8px; z-index: 7;
  display: flex; flex-direction: column; align-items: center; gap: 5px;
  opacity: 0; pointer-events: none; transform: translateX(4px) scale(.92);
  transition: opacity .2s ease, transform .2s ease;
}
.card-actions :deep(.el-button) {
  width: 26px; height: 26px; min-height: 26px; margin: 0; padding: 0;
  color: #475569; background: rgba(255, 255, 255, .94); border-color: #dbe4ef;
  box-shadow: 0 3px 9px rgba(15, 23, 42, .1);
}
.card-actions :deep(.el-button .el-icon) { font-size: 12px; }
.card-actions :deep(.el-button--danger) { color: #dc2626; }
.model-card:hover .card-actions,
.model-card:focus-within .card-actions { opacity: 1; pointer-events: auto; transform: translateX(0) scale(1); }
.model-placeholder { color: #9aaabd; }
.format-tags { position: absolute; top: 8px; left: 8px; right: 58px; z-index: 5; display: flex; flex-wrap: wrap; gap: 4px 5px; max-height: 44px; overflow: hidden; }
.format-tag { flex-shrink: 0; padding: 3px 7px; color: #52657a; background: rgba(255, 255, 255, .9); border: 1px solid rgba(203, 213, 225, .9); border-radius: 5px; box-shadow: 0 2px 7px rgba(15, 23, 42, .06); font-size: 9px; font-weight: 650; backdrop-filter: blur(8px); }
.format-tag.category { color: #1b735e; background: rgba(239, 246, 255, .94); border-color: #b9dfd2; }
.file-count { position: absolute; top: 8px; right: 8px; z-index: 5; display: flex; align-items: center; gap: 4px; padding: 4px 7px; color: #52657a; background: rgba(255, 255, 255, .92); border: 1px solid rgba(203, 213, 225, .9); border-radius: 6px; box-shadow: 0 2px 7px rgba(15, 23, 42, .06); font-size: 9px; backdrop-filter: blur(8px); }
.thumb-metrics { position: absolute; right: 9px; bottom: 8px; left: 9px; z-index: 5; display: flex; align-items: center; justify-content: space-between; color: #475569; font-size: 10px; }
.thumb-metrics > span { min-height: 22px; display: inline-flex; align-items: center; padding: 3px 7px; background: rgba(255, 255, 255, .9); border: 1px solid rgba(226, 232, 240, .92); border-radius: 6px; box-shadow: 0 2px 8px rgba(15, 23, 42, .06); backdrop-filter: blur(8px); }
.thumb-metrics span:last-child { display: flex; align-items: center; gap: 4px; }
.model-info { position: relative; min-height: 126px; padding: 13px 13px 12px; color: #475569; background: #fff; border-top: 1px solid #edf2f7; }
.model-title-row { display: flex; align-items: center; gap: 8px; }
.model-name { min-width: 0; flex: 1; overflow: hidden; color: #1f2d3d; font-size: 14px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.model-version { flex-shrink: 0; padding: 2px 6px; color: #1f8067; background: #eff9f5; border: 1px solid #dff2eb; border-radius: 5px; font-size: 9px; font-weight: 600; }
.model-project { max-width: 100%; display: flex; align-items: center; gap: 5px; margin-top: 10px; overflow: hidden; color: #64748b; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.model-project .el-icon { color: #2d9577; }
.model-meta { display: flex; justify-content: space-between; gap: 8px; margin-top: 12px; padding-top: 10px; color: #94a3b8; border-top: 1px solid #f0f3f7; font-size: 9px; }
.model-meta > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.creator { display: flex; align-items: center; gap: 4px; min-width: 0; }
.status-line { display: inline-flex; align-items: center; gap: 5px; margin-top: 9px; padding: 3px 7px; color: #64748b; background: #f8fafc; border: 1px solid #e8edf3; border-radius: 5px; font-size: 9px; }
.status-line i { width: 6px; height: 6px; background: currentColor; border-radius: 50%; }
.status-ready { color: #16854c; background: #f0fdf4; border-color: #dcfce7; }
.status-processing { color: #b66b00; background: #fffbeb; border-color: #fef3c7; }
.status-error { color: #c24141; background: #fef2f2; border-color: #fee2e2; }
.status-draft { color: #64748b; }
.model-table { width: 100%; border: 1px solid #e5eaf1; border-radius: 10px; overflow: hidden; }
.model-table :deep(.el-table__row) { cursor: pointer; }
.model-table :deep(.el-table__row:hover > td.el-table__cell) { background: #f7fbff; }
.table-cover { width: 88px; height: 58px; display: grid; place-items: center; overflow: hidden; color: #94a3b8; background: #0b1729; border-radius: 8px; }
.table-cover img { width: 100%; height: 100%; object-fit: cover; }
.table-model-name, .table-file-stat { min-width: 0; display: grid; gap: 4px; }
.table-model-name strong { overflow: hidden; color: #1e293b; text-overflow: ellipsis; white-space: nowrap; }
.table-model-name small, .table-file-stat small { color: #94a3b8; }
.table-file-stat strong { color: #475569; font-size: 12px; }
.table-actions { display: flex; align-items: center; gap: 3px; }
.table-actions .el-button { margin-left: 0; }
@media (max-width: 1350px) {
  .toolbar { grid-template-columns: minmax(0, 1fr) max-content; }
  .toolbar-search { grid-column: 1 / -1; grid-row: 2; max-width: none; }
  .toolbar-left { flex-wrap: wrap; }
}
@media (max-width: 1200px) {
  .model-thumb { height: 165px; }
  .format-tag { padding: 3px 6px; }
}
@media (max-width: 900px) {
  .model-library { height: auto; min-height: calc(100vh - 114px); }
  .library-workspace { flex-direction: column; overflow: visible; }
  .category-column { display: none; }
  .models-column { width: 100%; }
  .toolbar {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .toolbar-left,
  .toolbar-right,
  .toolbar-search {
    grid-column: 1 / -1;
    grid-row: auto;
    width: 100%;
    max-width: none;
    justify-self: stretch;
  }
  .toolbar-search :deep(.el-input__wrapper) { width: 100%; }
  .toolbar-left { flex-wrap: wrap; gap: 6px; }
  .toolbar-right { justify-content: flex-end; }
  .library-pagination {
    flex-direction: column;
    align-items: center;
    gap: 8px;
  }
  .page-current { order: 1; }
}
@media (max-width: 640px) {
  .model-thumb { height: 148px; }
  .model-info { min-height: 112px; padding: 10px; }
  .model-name { font-size: 13px; }
  .model-project { margin-top: 6px; }
  .model-meta { margin-top: 8px; padding-top: 6px; }
  .status-line { margin-top: 6px; }
  .file-count { top: 6px; right: 6px; }
  .format-tags { left: 6px; top: 6px; right: 50px; }
  .card-actions { top: 36px; right: 6px; }
}
@media (hover: none) {
  .card-actions { opacity: 1; pointer-events: auto; transform: none; }
}
@media (max-width: 480px) {
  .model-thumb { height: 132px; }
  .library-pagination :deep(.el-pagination) { white-space: nowrap; }
}
.category-drawer :deep(.el-drawer__body) { padding: 16px; }
.category-drawer .mode-switch,
.category-drawer .category-search,
.category-drawer .sort-controls,
.category-drawer .category-list-scroll,
.category-drawer .category-pagination { width: 100%; }

/* 批量选择工具栏 */
.batch-toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.batch-count { color: #64748b; font-size: 13px; }
.batch-count strong { color: #1f8067; font-size: 15px; margin: 0 2px; }

/* 卡片选择模式 */
.model-card-selectable { cursor: pointer; }
.model-card-selectable:hover { border-color: #9bcfbe; }
.model-card-selected {
  border-color: #1f8067 !important;
  box-shadow: 0 0 0 1px rgba(35, 139, 112, 0.3), 0 8px 20px rgba(35, 139, 112, 0.1) !important;
}

/* 卡片左上角选择框 */
.card-checkbox {
  position: absolute; top: 12px; left: 12px; z-index: 5;
  width: 28px; height: 28px; border-radius: 4px;
  border: 2px solid #1f8067; background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(4px);
  display: grid; place-items: center; cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}
.card-checkbox:hover { border-color: #1b735e; background: #fff; }
.card-checkbox.checked {
  background: #1f8067; border-color: #1f8067;
}
.card-checkbox :deep(.el-icon) { color: #fff; font-size: 16px; font-weight: bold; }
</style>
