<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="780px"
    destroy-on-close
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="90px">
      <el-row :gutter="12" v-if="mode !== 'read'">
        <el-col :span="12">
          <el-form-item label="模型名称" required>
            <el-input v-model="form.modelName" placeholder="输入模型名称" :disabled="isVersionUpdate" @input="nameEdited = true" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模型分类">
            <el-select v-model="form.categoryIds" multiple collapse-tags collapse-tags-tooltip placeholder="可选择多个分类" clearable style="width: 100%" :disabled="isVersionUpdate">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12" v-if="mode !== 'read'">
        <el-col :span="12">
          <el-form-item label="所属项目">
            <el-select v-model="form.projectIds" multiple collapse-tags collapse-tags-tooltip placeholder="可关联多个项目" clearable style="width: 100%" :disabled="isVersionUpdate">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="上传方式">
            <div class="upload-mode-row">
              <el-radio-group v-model="mode" size="small" :disabled="isVersionUpdate">
                <el-radio-button value="single">单个文件</el-radio-button>
                <el-radio-button value="batch">整个文件夹</el-radio-button>
                <el-radio-button value="read">批量读取</el-radio-button>
              </el-radio-group>
            </div>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-if="mode === 'read'">
        <el-col :span="24">
          <el-form-item label="上传方式">
            <div class="upload-mode-row">
              <el-radio-group v-model="mode" size="small" :disabled="isVersionUpdate">
                <el-radio-button value="single">单个文件</el-radio-button>
                <el-radio-button value="batch">整个文件夹</el-radio-button>
                <el-radio-button value="read">批量读取</el-radio-button>
              </el-radio-group>
            </div>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item v-if="isVersionUpdate" label="变更说明">
        <el-input v-model="form.changeLog" placeholder="简要说明本次更新内容（选填）" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>

    <el-alert
      v-if="isVersionUpdate"
      :title="`本次将创建 v${nextVersion}，v${props.updateModel?.latestVersion || props.updateModel?.version || 1} 的资料会完整保留`"
      type="info"
      :closable="false"
      show-icon
      class="version-alert"
    />

    <!-- Upload Zone - 紧凑拖拽区，仅在无文件时显示 -->
    <div
      v-if="(mode === 'read' ? !packages.length : !pendingFiles.length)"
      class="upload-dragger compact"
      :class="{ dragging }"
      @drop.prevent="handleDrop"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @click="openPrimaryPicker"
    >
      <el-icon :size="28" color="#c0c4cc"><UploadFilled /></el-icon>
      <p class="upload-text">
        {{ mode === 'read' ? '选择包含多个模型文件夹的父目录，系统会按文件夹拆分为独立模型' : (mode === 'batch' ? '拖拽文件夹到此处，或点击添加文件夹' : '拖拽文件到此处，或点击添加文件') }}
      </p>
      <p v-if="mode === 'read'" class="upload-hint">支持 fbx、tex、sp、unity、icon、screenshot 等原目录</p>
    </div>
    <input
      ref="fileInput"
      type="file"
      :multiple="true"
      style="display: none"
      @change="handleFileSelect"
    />
    <input
      ref="folderInput"
      type="file"
      webkitdirectory
      directory
      multiple
      style="display: none"
      @change="handleFileSelect"
    />

    <!-- Files Preview -->
    <div v-if="pendingFiles.length" class="resource-panel">
      <div class="resource-header">
        <div class="resource-title">
          <strong>资源文件（可多选 / 整夹拖入）</strong><em>*</em>
          <span>{{ pendingFiles.length }} 个 / {{ formatSize(totalSize) }}</span>
        </div>
        <small>{{ detectSummary }}</small>
      </div>
      <div class="resource-tree-shell">
        <div class="resource-tree-label"><span>原目录结构</span><small>点击文件夹展开或折叠</small></div>
        <DirectoryFileTree
          :root-name="directoryRootName"
          :files="directoryEntries"
          :disabled="uploading"
          @remove="removeDirectoryEntries"
        />
      </div>
      <div class="resource-actions">
        <el-button v-permission="'model:upload'" size="small" :disabled="uploading" @click="fileInput?.click()">＋ 追加文件</el-button>
        <el-button v-permission="'model:upload'" size="small" :disabled="uploading" @click="folderInput?.click()">▣ 追加文件夹</el-button>
        <el-button v-permission="'model:upload'" size="small" text type="danger" :disabled="uploading" @click="clearFiles">清空</el-button>
      </div>
    </div>

    <!-- Read-mode Package List (批量读取) -->
    <div v-if="mode === 'read' && packages.length" class="read-panel">
      <div class="read-toolbar">
        <div class="read-summary"><strong>已读取 {{ packages.length }} 个模型</strong><span>{{ totalReadFiles }} 个文件 · {{ formatSize(totalReadBytes) }}</span></div>
        <div class="read-bulk">
          <el-select v-model="bulk.categoryIds" multiple collapse-tags collapse-tags-tooltip placeholder="批量模型分类" clearable><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select>
          <el-select v-model="bulk.projectIds" multiple collapse-tags collapse-tags-tooltip placeholder="批量关联项目" clearable><el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" /></el-select>
          <el-button size="small" @click="applyBulkConfig">应用到全部</el-button>
          <el-button size="small" @click="folderInput?.click()">＋ 继续读取</el-button>
          <el-button size="small" text type="danger" :disabled="uploading" @click="packages=[]">清空</el-button>
        </div>
      </div>
      <div class="read-list">
        <article v-for="(item, index) in packages" :key="item.id" class="read-card" :class="`state-${item.status}`">
          <header>
            <div class="read-index">{{ String(index+1).padStart(2, '0') }}</div>
            <div class="read-title">
              <el-input v-model="item.name" class="read-name-input" maxlength="100" @input="item.nameEdited=true" />
              <span><el-icon><Folder /></el-icon>{{ item.folderName }} · {{ item.files.length }} 个文件 · {{ formatSize(packageSize(item)) }}</span>
            </div>
            <el-tag v-if="item.status === 'success'" type="success" size="small">已添加</el-tag>
            <el-tag v-else-if="item.status === 'uploading'" size="small">添加中 {{ item.progress }}%</el-tag>
            <el-tag v-else-if="item.status === 'error'" type="danger" size="small">添加失败</el-tag>
            <el-button text type="danger" size="small" :disabled="uploading" @click="removePackage(item.id)"><el-icon><Close /></el-icon></el-button>
          </header>
          <div class="read-body">
            <div class="read-form">
              <label>模型分类</label><el-select v-model="item.categoryIds" multiple filterable placeholder="选择一个或多个分类" clearable size="small"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select>
              <label>关联项目</label><el-select v-model="item.projectIds" multiple filterable placeholder="该模型可同时用于多个项目" clearable size="small"><el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" /></el-select>
              <label>说明</label><el-input v-model="item.description" type="textarea" :rows="1" placeholder="选填" size="small" />
            </div>
            <div class="read-preview">
              <div class="read-preview-head"><span>原目录结构</span><span>{{ readKindSummary(item) }}</span></div>
              <DirectoryFileTree
                :root-name="item.folderName"
                :files="readDirectoryEntries(item)"
                :disabled="uploading || item.status === 'success'"
                @remove="removeReadDirectoryEntries(item, $event)"
              />
              <div v-if="item.error" class="read-error">{{ item.error }}</div>
            </div>
          </div>
        </article>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button v-permission="'model:upload'" type="primary" :loading="uploading" @click="startUpload" :disabled="mode === 'read' ? (!packages.length || uploading) : (!form.modelName || !pendingFiles.length)">
        {{ uploading ? (mode === 'read' ? `正在添加 ${completedReadCount}/${packages.length}` : `上传中 ${progress}%`) : (isVersionUpdate ? `上传并创建 v${nextVersion}` : (mode === 'read' ? '全部添加' : '开始上传')) }}
      </el-button>
    </template>
    <el-progress v-if="uploading && mode !== 'read'" :percentage="progress" style="padding: 0 24px 16px" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { modelApi } from '@/api/model'
import { projectApi } from '@/api/project'
import { useAuthStore } from '@/stores/auth'
import { classifyModelFile, DISPLAY_EXTS, getFileExtension, type ModelFileKind } from '@/utils/modelFileRules'
import DirectoryFileTree, { type DirectoryFileEntry } from '@/components/upload/DirectoryFileTree.vue'

type UploadMode = 'single' | 'batch' | 'read'
type AssetFile = { file: File; path: string; kind: ModelFileKind }
type AssetPackage = {
  id: string
  folderName: string
  name: string
  nameEdited: boolean
  categoryIds: number[]
  projectIds: number[]
  description: string
  files: AssetFile[]
  status: 'ready' | 'uploading' | 'success' | 'error'
  progress: number
  error: string
}
const STRUCTURE_FOLDERS = new Set(['fbx', 'obj', 'gltf', 'glb', 'stl', 'icon', 'maya', 'screenshot', 'screenshots', 'sp', 'tex', 'texture', 'textures', 'unity', 'max', 'blend', 'source'])

const props = defineProps<{
  modelValue: boolean
  initialMode?: UploadMode
  updateModel?: {
    id: number
    name: string
  categoryId?: number | null
  projectId?: number | null
  categoryIds?: number[]
  projectIds?: number[]
    version?: number
    latestVersion?: number
  } | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success', payload?: any): void
}>()

const authStore = useAuthStore()
const fileInput = ref<HTMLInputElement>()
const folderInput = ref<HTMLInputElement>()
const mode = ref<UploadMode>('single')
const dragging = ref(false)
const uploading = ref(false)
const progress = ref(0)
const pendingFiles = ref<File[]>([])
const nameEdited = ref(false)
const categories = ref<any[]>([])
const projects = ref<any[]>([])
const packages = ref<AssetPackage[]>([])
const bulk = reactive({ categoryIds: [] as number[], projectIds: [] as number[] })
const isVersionUpdate = computed(() => Boolean(props.updateModel?.id))
const nextVersion = computed(() => (props.updateModel?.latestVersion || props.updateModel?.version || 1) + 1)
const dialogTitle = computed(() => isVersionUpdate.value ? '更新模型版本' : '上传模型')

const form = reactive({
  modelName: '',
  categoryIds: [] as number[],
  projectIds: [] as number[],
  description: '',
  changeLog: ''
})

const displayNames = computed(() => pendingFiles.value
  .filter((file) => DISPLAY_EXTS.includes(getFileExtension(file.name)))
  .map((file) => file.name))
const classificationFor = (file: File) => classifyModelFile(file.name, displayNames.value)
const filesByKind = (kind: ModelFileKind) => pendingFiles.value.filter((file) => classificationFor(file).kind === kind)
const displayFiles = computed(() => filesByKind('display'))
const textureFiles = computed(() => filesByKind('texture'))
const referenceFiles = computed(() => filesByKind('reference'))
const otherFiles = computed(() => filesByKind('other'))
const totalSize = computed(() => pendingFiles.value.reduce((sum, file) => sum + file.size, 0))

const detectSummary = computed(() =>
  `${displayFiles.value.length} 模型 · ${textureFiles.value.length} 材质贴图 · ${referenceFiles.value.length} 参考图片 · ${otherFiles.value.length} 其他`
)
type PathFile = File & { webkitRelativePath?: string; _relativePath?: string }
const filePath = (file: File) => {
  const pathFile = file as PathFile
  return pathFile.webkitRelativePath || pathFile._relativePath || file.name
}
const fileKey = (file: File) => `${filePath(file)}:${file.size}:${file.lastModified}`
const directoryRootName = computed(() => {
  const roots = new Set(pendingFiles.value.map((file) => filePath(file).split('/').filter(Boolean)).filter((parts) => parts.length > 1).map((parts) => parts[0]!))
  return roots.size === 1 ? Array.from(roots)[0]! : (form.modelName || props.updateModel?.name || '待上传资源')
})
const directoryEntries = computed<DirectoryFileEntry[]>(() => pendingFiles.value.map((file) => ({
  id: fileKey(file),
  path: filePath(file),
  name: file.name,
  size: file.size
})))

// ========== 批量读取模式（读取上传）==========
const totalReadFiles = computed(() => packages.value.reduce((sum, item) => sum + item.files.length, 0))
const totalReadBytes = computed(() => packages.value.reduce((sum, item) => sum + packageSize(item), 0))
const completedReadCount = computed(() => packages.value.filter(item => item.status === 'success').length)
const normalizePath = (value: string) => value.replace(/\\/g, '/').replace(/^\/+|\/+$/g, '')
const pathFor = (file: File) => normalizePath((file as any).webkitRelativePath || file.name)

const packageFiles = (files: File[]) => {
  const valid = files.filter(file => !file.name.startsWith('.') && file.name !== 'Thumbs.db')
  const rows = valid.map(file => ({ file, parts: pathFor(file).split('/').filter(Boolean) }))
  const groups = new Map<string, { folder: string; rows: typeof rows }>()
  for (const root of new Set(rows.map(row => row.parts[0]))) {
    const rootRows = rows.filter(row => row.parts[0] === root)
    const seconds = new Set(rootRows.map(row => (row.parts[1] || '').toLowerCase()).filter(Boolean))
    const rootIsAsset = !seconds.size || Array.from(seconds).every(name => STRUCTURE_FOLDERS.has(name))
    for (const row of rootRows) {
      const folder = rootIsAsset ? root : (row.parts[1] || root)
      const key = rootIsAsset ? root : `${root}/${folder}`
      if (!groups.has(key)) groups.set(key, { folder, rows: [] })
      groups.get(key)!.rows.push(row)
    }
  }
  return Array.from(groups.entries()).map(([key, group]) => {
    const rootIsPackage = key.split('/').length === 1
    const assetFiles = group.rows.map(row => {
      const relative = normalizePath((rootIsPackage ? row.parts : row.parts.slice(1)).join('/'))
      return { file: row.file, path: relative, kind: classifyModelFile(relative).kind }
    })
    return {
      id: `${key}-${Date.now()}-${Math.random()}`,
      folderName: group.folder,
      name: group.folder,
      nameEdited: false,
      categoryIds: [...bulk.categoryIds],
      projectIds: [...bulk.projectIds],
      description: '',
      files: assetFiles,
      status: 'ready',
      progress: 0,
      error: ''
    } as AssetPackage
  }).filter(item => item.files.length)
}

const addReadFiles = (files: File[]) => {
  const additions = packageFiles(files)
  if (!additions.length) return ElMessage.warning('没有读取到有效文件')
  packages.value.push(...additions)
  ElMessage.success(`已按目录识别 ${additions.length} 个模型`)
}

const packageSize = (item: AssetPackage) => item.files.reduce((sum, row) => sum + row.file.size, 0)
const assetId = (asset: AssetFile) => `${asset.path}:${asset.file.size}:${asset.file.lastModified}`
const readDirectoryEntries = (item: AssetPackage): DirectoryFileEntry[] => item.files.map(asset => ({
  id: assetId(asset),
  path: asset.path,
  name: asset.file.name,
  size: asset.file.size
}))
const removeReadDirectoryEntries = (item: AssetPackage, fileIds: string[]) => {
  const targets = new Set(fileIds)
  item.files = item.files.filter(asset => !targets.has(assetId(asset)))
  if (!item.files.length) {
    removePackage(item.id)
    ElMessage.info(`“${item.name}”已无待上传文件，已从列表移除`)
  }
}
const removePackage = (id: string) => { packages.value = packages.value.filter(item => item.id !== id) }
const applyBulkConfig = () => {
  packages.value.filter(item => item.status !== 'success').forEach(item => {
    item.categoryIds = [...bulk.categoryIds]
    item.projectIds = [...bulk.projectIds]
  })
  ElMessage.success('批量配置已应用')
}
const readKindSummary = (item: AssetPackage) => {
  const c: Record<string, number> = { display: 0, texture: 0, reference: 0, other: 0 }
  item.files.forEach(row => c[row.kind]++)
  return `${c.display} 模型 · ${c.texture} 贴图 · ${c.reference} 参考 · ${c.other} 其他`
}

const uploadOnePackage = async (item: AssetPackage) => {
  item.status = 'uploading'
  item.progress = 0
  item.error = ''
  const data = new FormData()
  item.files.forEach(row => data.append('files', row.file, row.file.name))
  data.append('modelName', item.name.trim() || item.folderName)
  data.append('categoryIds', JSON.stringify(item.categoryIds))
  data.append('projectIds', JSON.stringify(item.projectIds))
  data.append('description', item.description || '')
  data.append('filePaths', JSON.stringify(item.files.map(row => row.path)))
  data.append('fileTypes', JSON.stringify(Object.fromEntries(item.files.map(row => [row.path, row.kind]))))
  try {
    await modelApi.uploadFolder(data, {
      onUploadProgress: (event: any) => { if (event.total) item.progress = Math.round(event.loaded / event.total * 100) }
    })
    item.progress = 100
    item.status = 'success'
  } catch (error: any) {
    item.status = 'error'
    item.error = error?.response?.data?.message || '添加失败，请检查文件后重试'
    throw error
  }
}

const uploadAllPackages = async () => {
  const pending = packages.value.filter(item => item.status !== 'success')
  if (!pending.length) return ElMessage.info('这些模型已经添加完成')
  if (pending.some(item => !item.name.trim())) return ElMessage.warning('请补全模型名称')
  uploading.value = true
  progress.value = 0
  let failed = 0
  for (const item of pending) {
    try { await uploadOnePackage(item) } catch { failed++ }
  }
  uploading.value = false
  progress.value = 100
  failed ? ElMessage.warning(`添加完成，${failed} 个模型需要重试`) : ElMessage.success('全部模型已添加，系统正在生成预览')
  if (!failed) {
    handleClose()
    emit('success')
  }
}

const formatSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit += 1 }
  return `${value >= 100 || unit === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[unit]}`
}

const openPrimaryPicker = () => {
  if (uploading.value) return
  if (mode.value === 'single') fileInput.value?.click()
  else folderInput.value?.click()
}

const handleDrop = (e: DragEvent) => {
  dragging.value = false
  if (mode.value === 'single') {
    addFiles(Array.from(e.dataTransfer?.files || []))
    return
  }
  if (mode.value === 'read') {
    addReadFiles(Array.from(e.dataTransfer?.files || []))
    return
  }
  const items = e.dataTransfer?.items
  if (!items) { addFiles(Array.from(e.dataTransfer?.files || [])); return }
  const entries: FileSystemEntry[] = []
  for (let i = 0; i < items.length; i++) {
    const entry = items[i].webkitGetAsEntry()
    if (entry) entries.push(entry)
  }
  const files: File[] = []
  let pending = entries.length
  const done = () => { if (--pending === 0) addFiles(files) }
  const traverse = (entry: FileSystemEntry, callback: () => void) => {
    if (entry.isFile) {
      (entry as FileSystemFileEntry).file((file: File) => {
        Object.defineProperty(file, '_relativePath', { value: entry.fullPath.replace(/^\//, ''), configurable: true })
        files.push(file)
        callback()
      }, () => callback())
    } else if (entry.isDirectory) {
      const reader = (entry as FileSystemDirectoryEntry).createReader()
      const readAll = () => {
        reader.readEntries(async (subEntries) => {
          if (subEntries.length === 0) { callback(); return }
          let sub = subEntries.length
          for (const se of subEntries) traverse(se, () => { if (--sub === 0) readAll() })
        }, () => callback())
      }
      readAll()
    } else { callback() }
  }
  for (const entry of entries) traverse(entry, done)
  if (entries.length === 0) addFiles(Array.from(e.dataTransfer?.files || []))
}

const handleFileSelect = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (mode.value === 'read') addReadFiles(Array.from(input.files || []))
  else addFiles(Array.from(input.files || []))
  input.value = ''
}

const addFiles = (files: File[]) => {
  const valid = files.filter(f => !f.name.startsWith('.'))
  if (!valid.length) return
  const existing = new Set(pendingFiles.value.map(fileKey))
  pendingFiles.value.push(...valid.filter((file) => !existing.has(fileKey(file))))
  if (!nameEdited.value && !form.modelName) {
    const firstPath = filePath(valid[0]!)
    const rootFolder = firstPath.includes('/') ? firstPath.split('/').filter(Boolean)[0] : ''
    const display = valid.find(f => DISPLAY_EXTS.includes(getFileExtension(f.name)))
    form.modelName = rootFolder || (display ? display.name.replace(/\.[^.]+$/, '') : '')
  }
}

const removeDirectoryEntries = (fileIds: string[]) => {
  const targets = new Set(fileIds)
  pendingFiles.value = pendingFiles.value.filter((file) => !targets.has(fileKey(file)))
}

const clearFiles = () => {
  pendingFiles.value = []
  packages.value = []
  bulk.categoryIds = []
  bulk.projectIds = []
  if (fileInput.value) fileInput.value.value = ''
  if (folderInput.value) folderInput.value.value = ''
}

const startUpload = async () => {
  if (mode.value === 'read') {
    await uploadAllPackages()
    return
  }
  if (!form.modelName) return ElMessage.warning('请输入模型名称')
  if (!pendingFiles.value.length) return ElMessage.warning('请选择文件')
  uploading.value = true
  progress.value = 0
  const formData = new FormData()
  pendingFiles.value.forEach(f => formData.append('files', f, f.name))
  if (isVersionUpdate.value) {
    if (form.changeLog) formData.append('changeLog', form.changeLog)
  } else {
    formData.append('modelName', form.modelName)
    if (form.categoryIds.length) {
      formData.append('categoryIds', JSON.stringify(form.categoryIds))
      formData.append('categoryId', String(form.categoryIds[0]))
    }
    if (form.projectIds.length) {
      formData.append('projectIds', JSON.stringify(form.projectIds))
      formData.append('projectId', String(form.projectIds[0]))
    }
    if (form.description) formData.append('description', form.description)
  }
  formData.append('fileTypes', JSON.stringify(Object.fromEntries(
    pendingFiles.value.map((file) => [filePath(file), classificationFor(file).kind])
  )))
  formData.append('filePaths', JSON.stringify(pendingFiles.value.map(filePath)))
  try {
    const endpoint = isVersionUpdate.value
      ? `/api/models/${props.updateModel!.id}/versions/upload`
      : '/api/models/upload-folder'
    const response = await axios.post(endpoint, formData, {
      headers: { 'Content-Type': 'multipart/form-data', Authorization: `Bearer ${authStore.token}` },
      timeout: 600000,
      onUploadProgress: (e) => { if (e.total) progress.value = Math.round((e.loaded / e.total) * 100) }
    })
    ElMessage.success(isVersionUpdate.value ? `v${nextVersion.value} 上传成功，旧版本资料已保留` : '上传成功，系统正在后台处理模型')
    // 成功响应后立即解除关闭保护，随后清空表单并关闭弹窗。
    uploading.value = false
    handleClose()
    emit('success', response.data?.data)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const handleClose = () => {
  if (uploading.value) return
  clearFiles()
  form.modelName = ''
  form.categoryIds = []
  form.projectIds = []
  nameEdited.value = false
  form.description = ''
  form.changeLog = ''
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (visible) => {
  if (!visible) return
  clearFiles()
  mode.value = props.updateModel ? 'batch' : (props.initialMode || 'single')
  if (!props.updateModel) return
  form.modelName = props.updateModel.name || ''
  form.categoryIds = props.updateModel.categoryIds?.length ? props.updateModel.categoryIds : (props.updateModel.categoryId ? [props.updateModel.categoryId] : [])
  form.projectIds = props.updateModel.projectIds?.length ? props.updateModel.projectIds : (props.updateModel.projectId ? [props.updateModel.projectId] : [])
  nameEdited.value = false
  form.changeLog = ''
})

onMounted(async () => {
  const [cats, projs] = await Promise.all([
    modelApi.getCategories(),
    projectApi.getProjects({ page: 0, size: 100 })
  ])
  categories.value = cats.data
  projects.value = projs.data.list
})
</script>

<style scoped>
.upload-dragger {
  border: 2px dashed #d9d9d9; border-radius: 10px;
  padding: 30px 16px; text-align: center; cursor: pointer;
  background: #fafbfc; transition: all 0.3s;
  margin: 4px 0 16px;
}
.upload-dragger.compact {
  padding: 18px 16px;
  margin: 0 0 16px;
}
.upload-mode-row {
  display: flex; align-items: center; gap: 12px;
}
.version-alert { margin: -2px 0 14px; }
.upload-dragger:hover { border-color: #1f8067; background: #f5faf8; }
.upload-dragger.dragging { border-color: #1f8067; background: #eaf7f1; }
.upload-text { font-size: 14px; color: #606266; margin-top: 10px; }
.upload-hint { font-size: 12px; color: #909399; margin-top: 6px; }
.resource-panel {
  margin-bottom: 8px; padding: 12px;
  background: #f5faf8;
  border: 1px dashed #62b599; border-radius: 12px;
}
.resource-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 9px; }
.resource-title { display: flex; align-items: center; gap: 7px; min-width: 0; color: #1e293b; font-size: 13px; }
.resource-title em { color: #ef4444; font-style: normal; }
.resource-title span { padding: 3px 8px; color: #1f8067; background: #e8f1ff; border-radius: 7px; font-size: 11px; white-space: nowrap; }
.resource-header small { color: #64748b; font-size: 11px; line-height: 22px; white-space: nowrap; }
.resource-tree-shell{padding:9px 10px 8px;background:rgba(255,255,255,.82);border:1px solid #e7edf6;border-radius:10px}.resource-tree-label{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-bottom:5px;border-bottom:1px solid #edf1f6;color:#64748b;font-size:11px}.resource-tree-label small{color:#94a3b8}
.resource-actions { display: flex; align-items: center; gap: 4px; margin-top: 10px; }
.read-panel { margin-bottom: 8px; border: 1px dashed #62b599; border-radius: 12px; background: #f5faf8; overflow: hidden; }
.read-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 12px; border-bottom: 1px solid #e2e8f0; background: rgba(255,255,255,0.55); flex-wrap: wrap; }
.read-summary { display: grid; gap: 2px; }
.read-summary span { color: #94a3b8; font-size: 11px; }
.read-bulk { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.read-bulk .el-select { width: 150px; }
.read-list { max-height: 420px; overflow: auto; padding: 10px; display: grid; gap: 10px; }
.read-card { border: 1px solid #e3e9f1; border-radius: 12px; background: #fff; overflow: hidden; }
.read-card.state-success { border-color: #bbf7d0; }
.read-card.state-error { border-color: #fecaca; }
.read-card > header { min-height: 52px; display: flex; align-items: center; gap: 10px; padding: 8px 12px; border-bottom: 1px solid #edf1f6; background: #fcfdff; }
.read-index { width: 28px; height: 28px; display: grid; place-items: center; border-radius: 8px; color: #1f8067; background: #eff9f5; font-size: 11px; font-weight: 700; }
.read-title { min-width: 0; flex: 1; display: grid; gap: 2px; }
.read-name-input { max-width: 320px; }
.read-title span { color: #64748b; font-size: 11px; display: flex; align-items: center; gap: 4px; }
.read-body { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 10px 12px; }
.read-form { display: grid; gap: 8px; }
.read-form label { color: #475569; font-size: 12px; }
.read-preview { min-width: 0; }
.read-preview-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: #64748b; font-size: 11px; margin-bottom: 6px; }
.read-error { margin-top: 6px; color: #ef4444; font-size: 12px; }
@media (max-width: 780px) { .read-body { grid-template-columns: 1fr; } }
</style>
