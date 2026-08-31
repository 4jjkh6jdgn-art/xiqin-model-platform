<template>
  <div class="page-container registration-page">
    <el-card class="registration-workspace" shadow="never">
      <template #header>
        <div class="workspace-header">
          <div>
            <strong>注册与邀请</strong>
            <span>审批普通注册申请，或生成邀请码让新成员直接激活账号</span>
          </div>
          <el-button v-if="canCreateInvitation" v-permission="'invitation:create'" type="primary" :loading="generating" @click="generateCode">
            <el-icon><Plus /></el-icon>生成邀请码
          </el-button>
        </div>
      </template>

      <el-tabs v-model="activeWorkspace" class="workspace-tabs" @tab-change="handleWorkspaceTabChange">
        <el-tab-pane v-if="canReviewRegistration" name="requests">
          <template #label><span>注册申请</span><el-badge v-if="pendingCount" :value="pendingCount" :max="99" class="tab-badge" /></template>
          <div class="section-toolbar">
            <div><strong>注册审批</strong><span>无邀请码注册的账号需要审批后才能登录</span></div>
            <el-radio-group v-model="statusFilter" @change="changeRequestStatus">
              <el-radio-button :value="0">待审批</el-radio-button><el-radio-button :value="1">已通过</el-radio-button><el-radio-button :value="2">已拒绝</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="requests" v-loading="loading" stripe height="500">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="userId" label="用户ID" width="90" />
            <el-table-column label="用户名" min-width="130"><template #default="{ row }">{{ getUserInfo(row.userId) }}</template></el-table-column>
            <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="requestStatusType(row.status)">{{ requestStatusText(row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="reviewNote" label="审批备注" min-width="180"><template #default="{ row }">{{ row.reviewNote || '-' }}</template></el-table-column>
            <el-table-column label="申请时间" width="180"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 0">
                  <el-button v-permission="'registration:approve'" size="small" type="success" @click="review(row.id, 'approve')">通过</el-button>
                  <el-button v-permission="'registration:approve'" size="small" type="danger" plain @click="review(row.id, 'reject')">拒绝</el-button>
                </template>
                <span v-else class="text-muted">已处理</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <span>本页 {{ requests.length }} 条，共 {{ requestTotal }} 条</span>
            <el-pagination v-model:current-page="requestPage" :page-size="requestPageSize" :total="requestTotal" layout="prev, pager, next" @current-change="loadRequests" />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canViewInvitations" label="邀请码管理" name="invitations">
          <div class="invitation-summary">
            <div><span>可使用</span><strong>{{ invitationSummary.available }}</strong></div>
            <div><span>已使用</span><strong>{{ invitationSummary.used }}</strong></div>
            <div><span>已过期</span><strong>{{ invitationSummary.expired }}</strong></div>
            <div><span>已撤销</span><strong>{{ invitationSummary.revoked }}</strong></div>
          </div>
          <div class="section-toolbar invitation-toolbar">
            <div><strong>邀请码记录</strong><span>邀请码单次有效，使用后自动绑定注册账号</span></div>
            <div class="toolbar-actions">
              <el-select v-model="invitationStatus" style="width: 130px" @change="changeInvitationStatus">
                <el-option label="全部状态" :value="null" /><el-option label="可使用" :value="0" /><el-option label="已使用" :value="1" /><el-option label="已过期" :value="2" /><el-option label="已撤销" :value="3" />
              </el-select>
              <el-button :loading="invitationLoading" @click="refreshInvitations"><el-icon><Refresh /></el-icon>刷新</el-button>
            </div>
          </div>
          <el-table :data="invitationCodes" v-loading="invitationLoading" stripe height="430">
            <el-table-column label="邀请码" min-width="190"><template #default="{ row }"><div class="code-cell"><code>{{ row.code }}</code><el-button text type="primary" @click="copyText(row.code, '邀请码')">复制</el-button></div></template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="invitationStatusType(row.status)">{{ invitationStatusText(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="创建人" min-width="120"><template #default="{ row }">{{ getUserInfo(row.createdBy) }}</template></el-table-column>
            <el-table-column label="使用人" min-width="120"><template #default="{ row }">{{ row.usedBy ? getUserInfo(row.usedBy) : '-' }}</template></el-table-column>
            <el-table-column label="创建时间" width="180"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
            <el-table-column label="有效期至" width="180"><template #default="{ row }">{{ formatTime(row.expiresAt) }}</template></el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 0 && canRevokeInvitation" v-permission="'invitation:revoke'" size="small" type="danger" plain @click="revokeCode(row)">撤销</el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <span>本页 {{ invitationCodes.length }} 条，共 {{ invitationTotal }} 条</span>
            <el-pagination v-model:current-page="invitationPage" :page-size="invitationPageSize" :total="invitationTotal" layout="prev, pager, next" @current-change="loadInvitations" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="generatedDialogVisible" title="邀请码已生成" width="500px" destroy-on-close>
      <div class="generated-code">
        <span>新成员可输入邀请码，或直接打开注册链接</span><code>{{ generatedInvitation.code }}</code>
        <small>有效期至 {{ formatTime(generatedInvitation.expiresAt) }}，仅可使用一次</small>
      </div>
      <template #footer>
        <el-button @click="copyText(generatedInvitation.code, '邀请码')">复制邀请码</el-button>
        <el-button @click="copyText(generatedInvitation.link, '注册链接')">复制注册链接</el-button>
        <el-button type="primary" @click="generatedDialogVisible = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { authApi, userApi } from '@/api/index-modules'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const canReviewRegistration = computed(() => authStore.hasPermission('registration:view'))
const canCreateInvitation = computed(() => authStore.hasPermission('invitation:create'))
const canRevokeInvitation = computed(() => authStore.hasPermission('invitation:revoke'))
const canViewInvitations = computed(() => ['invitation:view', 'invitation:create', 'invitation:revoke'].some(code => authStore.hasPermission(code)))
const activeWorkspace = ref(canReviewRegistration.value ? 'requests' : 'invitations')
const users = ref<any[]>([])
const requests = ref<any[]>([])
const loading = ref(false)
const statusFilter = ref(0)
const pendingCount = ref(0)
const requestPage = ref(1)
const requestPageSize = 20
const requestTotal = ref(0)
const invitationCodes = ref<any[]>([])
const invitationLoading = ref(false)
const invitationStatus = ref<number | null>(null)
const invitationPage = ref(1)
const invitationPageSize = 20
const invitationTotal = ref(0)
const invitationSummary = ref({ available: 0, used: 0, expired: 0, revoked: 0, total: 0 })
const generating = ref(false)
const generatedDialogVisible = ref(false)
const generatedInvitation = ref({ code: '', expiresAt: '', link: '' })

const loadRequests = async () => {
  if (!canReviewRegistration.value) return
  loading.value = true
  try {
    const res = await authApi.getRegistrationRequests({ status: statusFilter.value, page: requestPage.value - 1, size: requestPageSize })
    const data = res.data || {}
    requests.value = data.content || data.list || []
    requestTotal.value = Number(data.totalElements ?? data.total ?? requests.value.length)
    if (statusFilter.value === 0) pendingCount.value = requestTotal.value
  } finally { loading.value = false }
}
const loadPendingCount = async () => {
  if (!canReviewRegistration.value || statusFilter.value === 0) return
  const res = await authApi.getRegistrationRequests({ status: 0, page: 0, size: 1 })
  pendingCount.value = Number(res.data?.totalElements ?? res.data?.total ?? 0)
}
const changeRequestStatus = () => { requestPage.value = 1; loadRequests() }
const loadUsers = async () => {
  if (!authStore.hasPermission('user:view')) return
  try { const res = await userApi.getUsers({ page: 0, size: 500 }); users.value = res.data?.list || res.data?.content || [] } catch (_) { /* 非必要信息 */ }
}
const getUserInfo = (userId: number) => users.value.find(item => item.id === userId)?.username || `#${userId}`
const review = async (id: number, action: string) => {
  await authApi.reviewRegistration(id, { action })
  ElMessage.success(action === 'approve' ? '注册申请已通过' : '注册申请已拒绝')
  await Promise.all([loadRequests(), loadPendingCount()])
}
const loadInvitationSummary = async () => {
  if (!canViewInvitations.value) return
  const res = await authApi.getInvitationSummary()
  invitationSummary.value = { ...invitationSummary.value, ...(res.data || {}) }
}
const loadInvitations = async () => {
  if (!canViewInvitations.value) return
  invitationLoading.value = true
  try {
    const params: any = { page: invitationPage.value - 1, size: invitationPageSize }
    if (invitationStatus.value !== null) params.status = invitationStatus.value
    const res = await authApi.getInvitationCodes(params)
    invitationCodes.value = res.data?.list || res.data?.content || []
    invitationTotal.value = Number(res.data?.total ?? res.data?.totalElements ?? invitationCodes.value.length)
  } finally { invitationLoading.value = false }
}
const refreshInvitations = async () => { await Promise.all([loadInvitations(), loadInvitationSummary()]) }
const changeInvitationStatus = () => { invitationPage.value = 1; loadInvitations() }
const generateCode = async () => {
  generating.value = true
  try {
    const res = await authApi.generateInvitationCode()
    const code = res.data.code
    generatedInvitation.value = { code, expiresAt: res.data.expiresAt, link: `${window.location.origin}/register?invitation=${encodeURIComponent(code)}` }
    generatedDialogVisible.value = true
    activeWorkspace.value = 'invitations'
    await refreshInvitations()
    ElMessage.success('邀请码已生成')
  } finally { generating.value = false }
}
const revokeCode = async (row: any) => {
  await ElMessageBox.confirm(`撤销邀请码 ${row.code}？撤销后不可恢复。`, '撤销邀请码', { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '取消' })
  await authApi.revokeInvitationCode(row.id)
  ElMessage.success('邀请码已撤销')
  await refreshInvitations()
}
const copyText = async (value: string, label: string) => { if (value) { await navigator.clipboard.writeText(value); ElMessage.success(`${label}已复制`) } }
const handleWorkspaceTabChange = (name: string | number) => {
  if (name === 'invitations') refreshInvitations()
  if (name === 'requests') Promise.all([loadRequests(), loadPendingCount()])
}
const requestStatusText = (status: number) => ({ 0: '待审批', 1: '已通过', 2: '已拒绝' }[status] || '未知')
const requestStatusType = (status: number) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[status] || 'info')
const invitationStatusText = (status: number) => ({ 0: '可使用', 1: '已使用', 2: '已过期', 3: '已撤销' }[status] || '未知')
const invitationStatusType = (status: number) => ({ 0: 'success', 1: 'info', 2: 'warning', 3: 'danger' }[status] || 'info')
const formatTime = (time?: string) => time ? new Date(time).toLocaleString('zh-CN', { hour12: false }) : '-'
onMounted(async () => {
  await loadUsers()
  if (canReviewRegistration.value) await Promise.all([loadRequests(), loadPendingCount()])
  if (canViewInvitations.value) await refreshInvitations()
})
</script>

<style scoped>
.registration-page { height: 100%; min-height: 680px; }
.registration-workspace { height: 100%; display: flex; flex-direction: column; }
.registration-workspace :deep(.el-card__body) { flex: 1; min-height: 0; padding: 0 20px 16px; overflow: hidden; }
.workspace-header, .section-toolbar, .pagination-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.workspace-header > div, .section-toolbar > div:first-child { display: grid; gap: 4px; }
.workspace-header strong { color: #16372c; font-size: 18px; }
.workspace-header span, .section-toolbar span { color: #879b94; font-size: 12px; }
.workspace-tabs { height: 100%; display: flex; flex-direction: column; }
.workspace-tabs :deep(.el-tabs__content) { flex: 1; min-height: 0; overflow: hidden; }
.workspace-tabs :deep(.el-tab-pane) { height: 100%; }
.tab-badge { margin-left: 8px; transform: translateY(-1px); }
.section-toolbar { min-height: 56px; padding: 4px 0 12px; }
.section-toolbar strong { color: #1f3730; font-size: 15px; }
.toolbar-actions { display: flex; align-items: center; gap: 10px; }
.invitation-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; padding: 4px 0 14px; }
.invitation-summary > div { display: flex; align-items: center; justify-content: space-between; min-height: 64px; padding: 12px 16px; border: 1px solid #dfece7; border-radius: 12px; background: #f7fbf9; }
.invitation-summary span { color: #6f857d; font-size: 12px; }
.invitation-summary strong { color: #178468; font-size: 22px; }
.code-cell { display: flex; align-items: center; gap: 8px; }
.code-cell code, .generated-code code { color: #176f5b; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-weight: 700; letter-spacing: 1.5px; }
.text-muted { color: #a0afa9; font-size: 12px; }
.pagination-row { min-height: 46px; color: #82958e; font-size: 12px; }
.generated-code { display: grid; gap: 14px; padding: 18px; text-align: center; border: 1px solid #d9ebe4; border-radius: 14px; background: #f6fbf9; }
.generated-code span, .generated-code small { color: #71867e; }
.generated-code code { font-size: 30px; letter-spacing: 4px; }
@media (max-width: 760px) {
  .workspace-header, .section-toolbar { align-items: stretch; flex-direction: column; }
  .invitation-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .toolbar-actions { justify-content: space-between; }
  .pagination-row > span { display: none; }
}
</style>
