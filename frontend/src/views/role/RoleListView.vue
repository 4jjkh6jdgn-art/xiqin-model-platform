<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>角色管理</span>
          <el-button v-permission="'role:create'" type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> 新建角色
          </el-button>
        </div>
      </template>

      <el-table :data="roles" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="角色名称" width="160" />
        <el-table-column prop="code" label="角色编码" width="140" />
        <el-table-column prop="description" label="描述" />
        <el-table-column label="权限" width="300">
          <template #default="{ row }">
            <el-tag v-for="p in row.permissions?.slice(0, 3)" :key="p.id" size="small" style="margin-right: 4px">
              {{ p.name }}
            </el-tag>
            <el-tag v-if="row.permissions?.length > 3" size="small" type="info">
              +{{ row.permissions.length - 3 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="系统" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isSystem ? 'danger' : 'info'" size="small">
              {{ row.isSystem ? '系统' : '自定义' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'role:permission_assign'" size="small" @click="openPermissions(row)" :disabled="row.code === 'admin'">权限</el-button>
            <el-button v-permission="'role:edit'" size="small" @click="openEdit(row)" :disabled="row.isSystem">编辑</el-button>
            <el-popconfirm title="确定删除该角色？" @confirm="deleteRole(row.id)">
              <template #reference>
                <el-button v-permission="'role:delete'" size="small" type="danger" :disabled="row.isSystem">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色' : '新建角色'" width="450px">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="角色名称">
          <el-input v-model="roleForm.name" placeholder="如：模型审核员" />
        </el-form-item>
        <el-form-item label="角色编码" v-if="!editingId">
          <el-input v-model="roleForm.code" placeholder="如：model_reviewer（英文）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="editingId ? 'role:edit' : 'role:create'" type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <!-- Permissions Dialog -->
    <el-dialog v-model="permDialogVisible" title="分配页面与按钮权限" width="min(900px, 94vw)" top="5vh" class="permission-dialog">
      <div class="permission-toolbar">
        <div>
          <strong>{{ currentRole?.name }}</strong>
          <span>已选择 {{ selectedPerms.length }} 项权限</span>
        </div>
        <div class="permission-toolbar-right">
          <el-checkbox
            :model-value="isAllSelected"
            :indeterminate="isAllIndeterminate"
            @update:model-value="toggleAll"
          >全部全选</el-checkbox>
          <el-input v-model="permissionKeyword" clearable placeholder="搜索页面、按钮或权限说明" class="permission-search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
      </div>
      <div class="permission-groups">
        <section v-for="group in filteredPermGroups" :key="group.module" class="permission-group">
          <header class="permission-group-header">
            <div>
              <strong>{{ group.title }}</strong>
              <span>{{ group.description }}</span>
            </div>
            <el-checkbox
              :model-value="isGroupSelected(group)"
              :indeterminate="isGroupIndeterminate(group)"
              @update:model-value="toggleGroup(group, $event)"
            >全选</el-checkbox>
          </header>
          <el-checkbox-group v-model="selectedPerms" class="permission-grid">
            <el-checkbox v-for="p in group.perms" :key="p.id" :value="p.id" class="permission-item">
              <span class="permission-copy">
                <strong>{{ p.name }}</strong>
                <small>{{ p.description || p.code }}</small>
              </span>
            </el-checkbox>
          </el-checkbox-group>
        </section>
        <el-empty v-if="!filteredPermGroups.length" description="没有匹配的权限" :image-size="70" />
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button v-permission="'role:permission_assign'" type="primary" @click="savePermissions">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { roleApi } from '@/api/index-modules'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const roles = ref<any[]>([])
const permissions = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const permDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const currentRole = ref<any>(null)
const selectedPerms = ref<number[]>([])
const permissionKeyword = ref('')

const roleForm = reactive({ name: '', code: '', description: '' })

const moduleMeta: Record<string, { title: string; description: string; order: number }> = {
  dashboard: { title: '仪表盘', description: '平台首页', order: 10 },
  model_library: { title: '模型库', description: '模型列表、上传与资料维护', order: 20 },
  model_detail: { title: '模型详情', description: '预览工具栏、文件、缩略图与版本记录', order: 30 },
  model_data: { title: '数据管理', description: '模型分类、项目分类及上传下载记录', order: 40 },
  project_list: { title: '项目列表', description: '项目查询、新建与删除', order: 50 },
  project_detail: { title: '项目工作台', description: '项目成员、任务、资料、排班和阶段', order: 60 },
  organization_user: { title: '用户管理', description: '用户资料、角色和账号状态', order: 70 },
  organization_role: { title: '角色管理', description: '自定义角色与权限分配', order: 80 },
  organization_registration: { title: '注册与邀请', description: '注册审批和邀请码', order: 90 },
  notification: { title: '提醒与站内信', description: '消息查看、处理和工作提醒', order: 100 },
  profile: { title: '个人设置', description: '个人资料维护', order: 110 },
  system: { title: '系统设置', description: '系统级配置', order: 120 },
}

const permGroups = computed(() => {
  const groups: Record<string, any[]> = {}
  permissions.value.forEach(p => {
    if (!groups[p.module]) groups[p.module] = []
    groups[p.module].push(p)
  })
  return Object.entries(groups)
    .map(([module, perms]) => ({
      module,
      title: moduleMeta[module]?.title || module,
      description: moduleMeta[module]?.description || '页面功能权限',
      order: moduleMeta[module]?.order || 999,
      perms: perms.sort((a, b) => a.id - b.id),
    }))
    .sort((a, b) => a.order - b.order)
})

const filteredPermGroups = computed(() => {
  const keyword = permissionKeyword.value.trim().toLowerCase()
  if (!keyword) return permGroups.value
  return permGroups.value
    .map(group => ({
      ...group,
      perms: group.perms.filter((p: any) => [group.title, p.name, p.code, p.description]
        .some(value => String(value || '').toLowerCase().includes(keyword))),
    }))
    .filter(group => group.perms.length)
})

const totalPermissionCount = computed(() => permissions.value.length)
const isAllSelected = computed(() => totalPermissionCount.value > 0 && selectedPerms.value.length === totalPermissionCount.value)
const isAllIndeterminate = computed(() => {
  const count = selectedPerms.value.length
  return count > 0 && count < totalPermissionCount.value
})
const toggleAll = (checked: boolean) => {
  selectedPerms.value = checked ? permissions.value.map((p: any) => p.id) : []
}

const isGroupSelected = (group: any) => group.perms.length > 0 && group.perms.every((p: any) => selectedPerms.value.includes(p.id))
const isGroupIndeterminate = (group: any) => {
  const count = group.perms.filter((p: any) => selectedPerms.value.includes(p.id)).length
  return count > 0 && count < group.perms.length
}
const toggleGroup = (group: any, checked: boolean) => {
  const ids = group.perms.map((p: any) => p.id)
  selectedPerms.value = checked
    ? Array.from(new Set([...selectedPerms.value, ...ids]))
    : selectedPerms.value.filter(id => !ids.includes(id))
}

const loadRoles = async () => {
  loading.value = true
  try {
    const res = await roleApi.getRoles()
    roles.value = res.data
  } finally {
    loading.value = false
  }
}

const loadPermissions = async () => {
  const res = await roleApi.getPermissions()
  permissions.value = res.data
}

const openCreate = () => {
  editingId.value = null
  roleForm.name = ''
  roleForm.code = ''
  roleForm.description = ''
  dialogVisible.value = true
}

const openEdit = (row: any) => {
  editingId.value = row.id
  roleForm.name = row.name
  roleForm.code = row.code
  roleForm.description = row.description
  dialogVisible.value = true
}

const saveRole = async () => {
  if (!roleForm.name) return ElMessage.warning('请输入角色名称')
  if (editingId.value) {
    await roleApi.updateRole(editingId.value, roleForm)
  } else {
    if (!roleForm.code) return ElMessage.warning('请输入角色编码')
    await roleApi.createRole(roleForm)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadRoles()
}

const openPermissions = async (row: any) => {
  currentRole.value = row
  permissionKeyword.value = ''
  // 每次打开都刷新权限目录，避免页面久未刷新时携带已被重新播种而失效的权限 ID
  await loadPermissions()
  const res = await roleApi.getRole(row.id)
  selectedPerms.value = res.data.permissions?.map((p: any) => p.id) || []
  permDialogVisible.value = true
}

const savePermissions = async () => {
  await roleApi.assignPermissions(currentRole.value.id, selectedPerms.value)
  ElMessage.success('权限已更新')
  permDialogVisible.value = false
  loadRoles()
}

const deleteRole = async (id: number) => {
  await roleApi.deleteRole(id)
  ElMessage.success('删除成功')
  loadRoles()
}

onMounted(() => {
  loadRoles()
  if (authStore.hasPermission('role:permission_assign')) loadPermissions()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.permission-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin: -4px 0 16px; flex-wrap: wrap; }
.permission-toolbar > div { display: flex; align-items: baseline; gap: 10px; color: #1e293b; flex-wrap: wrap; }
.permission-toolbar > div strong { font-size: 15px; }
.permission-toolbar > div span { color: #94a3b8; font-size: 12px; }
.permission-toolbar-right { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; justify-content: flex-end; }
.permission-search { width: min(340px, 100%); }
.permission-groups { max-height: 62vh; display: grid; gap: 12px; overflow-y: auto; padding-right: 8px; }
.permission-group { overflow: hidden; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.permission-group-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; background: #f4f9f7; border-bottom: 1px solid #e1ebe7; min-height: 48px; }
.permission-group-header > div { display: grid; gap: 2px; min-width: 0; flex: 1; }
.permission-group-header strong { color: #0f172a; font-size: 14px; font-weight: 700; letter-spacing: .2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.permission-group-header span { color: #64748b; font-size: 11px; line-height: 1.3; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.permission-group-header :deep(.el-checkbox) { margin-right: 0; flex-shrink: 0; }
.permission-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 4px; padding: 10px; }
.permission-item { height: auto; min-height: 56px; align-items: flex-start; margin: 0; padding: 10px 12px; border-radius: 8px; transition: background .15s; }
.permission-item:hover { background: #eff9f5; }
.permission-item :deep(.el-checkbox__input) { margin-top: 3px; flex-shrink: 0; }
.permission-item :deep(.el-checkbox__label) { min-width: 0; white-space: normal; padding-left: 8px; }
.permission-copy { display: grid; gap: 2px; }
.permission-copy strong { color: #1e293b; font-size: 12px; font-weight: 600; line-height: 1.35; word-break: break-all; }
.permission-copy small { color: #94a3b8; font-size: 10px; line-height: 1.35; display: block; word-break: break-all; }
@media (max-width: 760px) {
  .permission-toolbar { align-items: stretch; flex-direction: column; }
  .permission-search { width: 100%; }
  .permission-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 480px) {
  .permission-grid { grid-template-columns: 1fr; }
}
</style>
