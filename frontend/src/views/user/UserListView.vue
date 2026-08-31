<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <div class="header-actions">
            <el-button v-if="canCreateUser" type="primary" @click="openCreate">
              <el-icon><Plus /></el-icon>添加用户
            </el-button>
            <el-input v-model="keyword" placeholder="搜索用户名/邮箱/手机号" style="width: 250px" clearable @clear="loadUsers" @keyup.enter="loadUsers">
              <template #append><el-button @click="loadUsers"><el-icon><Search /></el-icon></el-button></template>
            </el-input>
          </div>
        </div>
      </template>

      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="roleName" label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="row.roleCode === 'admin' ? 'danger' : row.roleCode === 'leader' ? 'warning' : 'info'">
              {{ row.roleName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusText" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'warning' : 'danger'">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="170">
          <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'user:edit'" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'user:status'" size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-popconfirm title="确定删除该用户？" @confirm="deleteUser(row.id)">
              <template #reference>
                <el-button v-permission="'user:delete'" size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadUsers"
      />
    </el-card>

    <!-- Edit Dialog -->
    <el-dialog v-model="editDialogVisible" title="编辑用户" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.phone" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.roleId" placeholder="选择角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="重置密码">
          <el-input v-model="editForm.password" placeholder="留空则不修改" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button v-permission="'user:edit'" type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createDialogVisible" title="添加用户" width="560px" destroy-on-close>
      <el-tabs v-model="createMode" class="create-tabs">
        <el-tab-pane v-if="authStore.hasPermission('user:create')" label="单个添加" name="single">
          <el-form label-position="top">
            <el-form-item label="用户名" required><el-input v-model="createForm.username" maxlength="32" placeholder="3-32 个字符" /></el-form-item>
            <div class="form-grid">
              <el-form-item label="邮箱"><el-input v-model="createForm.email" placeholder="可选" /></el-form-item>
              <el-form-item label="手机号"><el-input v-model="createForm.phone" placeholder="可选" /></el-form-item>
            </div>
            <el-form-item label="角色" required>
              <el-select v-model="createForm.roleId" placeholder="选择角色" style="width:100%">
                <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane v-if="authStore.hasPermission('user:batch_create')" label="批量生成" name="batch">
          <el-form label-position="top">
            <div class="form-grid batch-grid">
              <el-form-item label="账号前缀" required><el-input v-model="batchForm.prefix" placeholder="例如 xiqin" /></el-form-item>
              <el-form-item label="角色" required>
                <el-select v-model="batchForm.roleId" placeholder="选择角色" style="width:100%">
                  <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="起始序号"><el-input-number v-model="batchForm.startNumber" :min="0" :max="999999" controls-position="right" /></el-form-item>
              <el-form-item label="生成数量"><el-input-number v-model="batchForm.count" :min="1" :max="100" controls-position="right" /></el-form-item>
              <el-form-item label="序号位数"><el-input-number v-model="batchForm.numberWidth" :min="1" :max="6" controls-position="right" /></el-form-item>
            </div>
            <div class="account-preview">
              <span>账号预览</span>
              <strong>{{ batchPreview }}</strong>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <el-alert title="所有新用户的初始密码均为 123456" description="账号创建后可立即登录，建议用户首次登录后在个人设置中修改密码。" type="warning" :closable="false" show-icon />
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">{{ createMode === 'batch' ? `生成 ${batchForm.count} 个用户` : '添加用户' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, roleApi } from '@/api/index-modules'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const keyword = ref('')
const users = ref<any[]>([])
const roles = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const editDialogVisible = ref(false)
const createDialogVisible = ref(false)
const creating = ref(false)
const createMode = ref('single')
const editForm = reactive({ id: 0, username: '', email: '', phone: '', roleId: null as number | null, password: '' })
const createForm = reactive({ username: '', email: '', phone: '', roleId: null as number | null })
const batchForm = reactive({ prefix: 'user', startNumber: 1, count: 10, numberWidth: 3, roleId: null as number | null })
const canCreateUser = computed(() => authStore.hasPermission('user:create') || authStore.hasPermission('user:batch_create'))
const formatGeneratedName = (number: number) => `${batchForm.prefix}${String(number).padStart(batchForm.numberWidth, '0')}`
const batchPreview = computed(() => {
  const start = formatGeneratedName(batchForm.startNumber)
  const end = formatGeneratedName(batchForm.startNumber + batchForm.count - 1)
  return batchForm.count === 1 ? start : `${start} — ${end}`
})

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await userApi.getUsers({ keyword: keyword.value || undefined, page: page.value - 1, size: size.value })
    users.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const loadRoles = async () => {
  if (!['user:edit', 'user:create', 'user:batch_create'].some(code => authStore.hasPermission(code))) return
  const res = await roleApi.getRoles()
  roles.value = res.data
}

const openCreate = () => {
  createMode.value = authStore.hasPermission('user:create') ? 'single' : 'batch'
  createForm.username = ''
  createForm.email = ''
  createForm.phone = ''
  createForm.roleId = roles.value[0]?.id || null
  batchForm.roleId = roles.value[0]?.id || null
  createDialogVisible.value = true
}

const submitCreate = async () => {
  if (creating.value) return
  if (createMode.value === 'single' && (!createForm.username.trim() || !createForm.roleId)) {
    return ElMessage.warning('请填写用户名并选择角色')
  }
  if (createMode.value === 'batch' && (!batchForm.prefix.trim() || !batchForm.roleId)) {
    return ElMessage.warning('请填写账号前缀并选择角色')
  }
  creating.value = true
  try {
    if (createMode.value === 'batch') {
      const res = await userApi.batchCreateUsers({ ...batchForm, prefix: batchForm.prefix.trim() })
      ElMessage.success(`已生成 ${res.data.length} 个用户，初始密码为 123456`)
    } else {
      await userApi.createUser({ ...createForm, username: createForm.username.trim() })
      ElMessage.success('用户已添加，初始密码为 123456')
    }
    createDialogVisible.value = false
    await loadUsers()
  } finally { creating.value = false }
}

const openEdit = (row: any) => {
  editForm.id = row.id
  editForm.username = row.username
  editForm.email = row.email || ''
  editForm.phone = row.phone || ''
  editForm.roleId = row.roleId
  editForm.password = ''
  editDialogVisible.value = true
}

const saveEdit = async () => {
  const data: any = { email: editForm.email, phone: editForm.phone }
  if (editForm.roleId) data.roleId = editForm.roleId
  if (editForm.password) data.password = editForm.password
  await userApi.updateUser(editForm.id, data)
  ElMessage.success('保存成功')
  editDialogVisible.value = false
  loadUsers()
}

const toggleStatus = async (row: any) => {
  await userApi.updateUserStatus(row.id, row.status === 1 ? 2 : 1)
  ElMessage.success(row.status === 1 ? '已禁用' : '已启用')
  loadUsers()
}

const deleteUser = async (id: number) => {
  await userApi.deleteUser(id)
  ElMessage.success('删除成功')
  loadUsers()
}

const formatTime = (t: string) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>

<style scoped>
.card-header, .header-actions { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
.batch-grid :deep(.el-input-number) { width: 100%; }
.account-preview { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 2px 0 16px; padding: 12px 14px; color: #64748b; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; font-size: 12px; }
.account-preview strong { overflow: hidden; color: #1f8067; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; text-overflow: ellipsis; white-space: nowrap; }
</style>
