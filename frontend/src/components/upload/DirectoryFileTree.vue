<template>
  <div class="directory-file-tree">
    <div class="tree-root">
      <span><el-icon><FolderOpened /></el-icon><strong>{{ rootName || '待上传资源' }}</strong></span>
      <em>{{ files.length }} 个文件</em>
    </div>
    <el-tree
      v-if="treeData.length"
      class="tree-body"
      :data="treeData"
      node-key="path"
      :indent="18"
      :expand-on-click-node="true"
      :props="{ label: 'name', children: 'children' }"
    >
      <template #default="{ data }">
        <div class="tree-row" :class="`row-${data.type}`">
          <span class="row-copy">
            <el-icon><Folder v-if="data.type === 'folder'" /><Document v-else /></el-icon>
            <span class="row-name" :title="data.name">{{ data.name }}</span>
          </span>
          <span class="row-actions">
            <em>{{ data.type === 'folder' ? `${data.fileIds.length} 个` : formatSize(data.size) }}</em>
            <el-button
              text
              type="danger"
              size="small"
              :title="data.type === 'folder' ? '移除此文件夹及其内容' : '移除此文件'"
              :disabled="disabled"
              @click.stop="requestRemove(data)"
            ><el-icon><Delete /></el-icon></el-button>
          </span>
        </div>
      </template>
    </el-tree>
    <div v-else class="tree-empty">暂无待上传文件</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Delete, Document, Folder, FolderOpened } from '@element-plus/icons-vue'

export type DirectoryFileEntry = {
  id: string
  path: string
  name: string
  size: number
}

type DirectoryNode = {
  name: string
  path: string
  type: 'folder' | 'file'
  children: DirectoryNode[]
  fileIds: string[]
  size: number
}

const props = withDefaults(defineProps<{
  rootName?: string
  files: DirectoryFileEntry[]
  disabled?: boolean
}>(), { rootName: '', disabled: false })

const emit = defineEmits<{
  (event: 'remove', fileIds: string[]): void
}>()

const normalizePath = (value: string) => value.replace(/\\/g, '/').replace(/^\/+|\/+$/g, '')

const treeData = computed(() => {
  const root: DirectoryNode = { name: props.rootName, path: '', type: 'folder', children: [], fileIds: [], size: 0 }
  props.files.forEach((file) => {
    const sourceParts = normalizePath(file.path || file.name).split('/').filter(Boolean)
    const parts = props.rootName && sourceParts[0] === props.rootName ? sourceParts.slice(1) : sourceParts
    const effectiveParts = parts.length ? parts : [file.name]
    let branch = root
    effectiveParts.forEach((name, index) => {
      const path = branch.path ? `${branch.path}/${name}` : name
      const isFile = index === effectiveParts.length - 1
      if (isFile) {
        branch.children.push({ name, path: `${path}:${file.id}`, type: 'file', children: [], fileIds: [file.id], size: file.size })
        return
      }
      let folder = branch.children.find((node) => node.type === 'folder' && node.name === name)
      if (!folder) {
        folder = { name, path, type: 'folder', children: [], fileIds: [], size: 0 }
        branch.children.push(folder)
      }
      folder.fileIds.push(file.id)
      folder.size += file.size
      branch = folder
    })
  })
  const sortNodes = (nodes: DirectoryNode[]) => {
    nodes.sort((a, b) => a.type === b.type ? a.name.localeCompare(b.name, 'zh-CN') : (a.type === 'folder' ? -1 : 1))
    nodes.forEach((node) => sortNodes(node.children))
  }
  sortNodes(root.children)
  return root.children
})

const requestRemove = async (node: DirectoryNode) => {
  if (node.type === 'folder') {
    await ElMessageBox.confirm(
      `确认移除文件夹“${node.name}”及其中 ${node.fileIds.length} 个文件？电脑中的原文件不会被删除。`,
      '移除待上传内容',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' }
    )
  }
  emit('remove', node.fileIds)
}

const formatSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let index = 0
  while (value >= 1024 && index < units.length - 1) { value /= 1024; index += 1 }
  return `${value.toFixed(index > 1 ? 1 : 0)} ${units[index]}`
}
</script>

<style scoped>
.directory-file-tree{min-width:0}.tree-root{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:8px 4px 7px;color:#1b735e}.tree-root span{min-width:0;display:flex;align-items:center;gap:7px}.tree-root strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.tree-root em{color:#94a3b8;font-size:11px;font-style:normal;white-space:nowrap}.tree-body{max-height:230px;overflow:auto;background:transparent;color:#475569;scrollbar-width:thin}.tree-body :deep(.el-tree-node__content){height:32px;border-radius:7px}.tree-body :deep(.el-tree-node__content:hover){background:#edf4ff}.tree-row{min-width:0;flex:1;display:flex;align-items:center;justify-content:space-between;gap:10px;padding-right:2px;font-size:12px}.row-copy,.row-actions{min-width:0;display:flex;align-items:center;gap:6px}.row-copy{overflow:hidden}.row-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.row-actions em{color:#94a3b8;font-style:normal;white-space:nowrap}.row-actions .el-button{width:24px;height:24px;margin:0;opacity:0}.tree-body :deep(.el-tree-node__content:hover) .row-actions .el-button,.row-actions .el-button:focus{opacity:1}.row-folder>.row-copy{color:#334155;font-weight:600}.row-file>.row-copy{color:#64748b}.tree-empty{padding:16px;text-align:center;color:#94a3b8;font-size:12px}
</style>
