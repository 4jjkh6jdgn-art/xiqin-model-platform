<template>
  <el-container class="main-layout">
    <!-- Sidebar -->
    <el-aside :width="isCollapse ? '72px' : '232px'" class="sidebar">
      <div class="logo" @click="$router.push('/dashboard')">
        <svg class="logo-mark" viewBox="0 0 32 32" width="30" height="30" aria-hidden="true">
          <defs>
            <linearGradient id="xq-grad" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#47a889" />
              <stop offset="100%" stop-color="#87c8b1" />
            </linearGradient>
          </defs>
          <path d="M16 3 L28 10 V22 L16 29 L4 22 V10 Z" fill="url(#xq-grad)" opacity="0.25" />
          <path d="M16 3 L28 10 V22 L16 29 L4 22 V10 Z" fill="none" stroke="url(#xq-grad)" stroke-width="1.6" />
          <path d="M16 3 L16 15 M4 10 L16 15 L28 10 M16 15 L16 29" fill="none" stroke="url(#xq-grad)" stroke-width="1.6" stroke-linejoin="round" />
        </svg>
        <span v-if="!isCollapse" class="logo-text">西秦管理平台</span>
        <el-badge v-if="authStore.hasPermission('notification:view') && !isCollapse" :value="unreadCount" :hidden="!unreadCount" :max="99" class="inbox-badge">
          <button v-permission="'notification:view'" class="inbox-trigger" title="提醒与站内信" @click.stop="openMailbox">
            <el-icon><Bell /></el-icon>
          </button>
        </el-badge>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="$route.path"
          :collapse="isCollapse"
          :collapse-transition="false"
          router
          :background-color="'var(--sidebar-bg)'"
          :text-color="'var(--sidebar-text)'"
          :active-text-color="'var(--sidebar-active)'"
        >
          <el-menu-item v-if="authStore.hasPermission('dashboard:view')" index="/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>

          <el-menu-item v-if="authStore.hasPermission('project:view')" index="/projects">
            <el-icon><Folder /></el-icon>
            <span>项目列表</span>
          </el-menu-item>

          <el-sub-menu v-if="authStore.hasPermission('model:view') || authStore.hasPermission('model:upload')" index="models">
            <template #title>
              <el-icon><Box /></el-icon>
              <span>模型管理</span>
            </template>
            <el-menu-item v-if="authStore.hasPermission('model:view')" index="/models">模型库</el-menu-item>
            <el-menu-item v-if="hasDataPermission" index="/models/data">数据管理</el-menu-item>
          </el-sub-menu>

          <el-menu-item
            v-if="hasOrganizationPermission"
            index="/organization"
          >
            <el-icon><User /></el-icon>
            <span>组织管理</span>
          </el-menu-item>

          <el-menu-item v-if="hasStoragePermission" index="/storage">
            <el-icon><Coin /></el-icon>
            <span>存储空间</span>
          </el-menu-item>

        </el-menu>
      </el-scrollbar>

      <div v-if="authStore.hasPermission('user:view') && !isCollapse" class="sidebar-platform-stats">
        <div><el-icon><Coin /></el-icon><span>占用 {{ formatStorage(sidebarStats.totalStorageBytes) }}</span></div>
        <div><el-icon><User /></el-icon><span>{{ sidebarStats.memberCount }} 位成员 · 下载 {{ sidebarStats.downloadCount }} 次</span></div>
      </div>

      <div class="sidebar-user" :class="{ collapsed: isCollapse }">
        <el-dropdown placement="top-start" trigger="click" @command="handleCommand">
          <div class="sidebar-user-trigger" :class="{ active: $route.path === '/profile' }">
            <el-avatar :size="34" :src="authStore.user?.avatar">
              {{ authStore.user?.username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <div v-if="!isCollapse" class="sidebar-user-meta">
              <span class="sidebar-username">{{ authStore.user?.username }}</span>
              <span class="sidebar-role">{{ authStore.user?.roleName }}</span>
            </div>
            <span v-if="!isCollapse" class="sidebar-user-arrow">⌃</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人设置</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-aside>

    <el-container>
      <!-- Header -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item
              v-for="(item, index) in breadcrumbItems"
              :key="`${item.title}-${index}`"
              :to="item.to && index < breadcrumbItems.length - 1 ? item.to : undefined"
            >
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
      </el-header>

      <!-- Main content -->
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>

    <el-drawer v-model="mailboxVisible" direction="ltr" size="430px" class="mailbox-drawer" :show-close="false">
      <template #header>
        <div class="mailbox-header">
          <div>
            <h3>提醒与站内信</h3>
            <p>显示与当前账号相关的提醒</p>
          </div>
          <el-button text circle @click="mailboxVisible = false"><el-icon><Close /></el-icon></el-button>
        </div>
      </template>

      <div class="mailbox-tools">
        <el-input v-model="messageKeyword" clearable placeholder="搜索标题、内容或提交人" @keyup.enter="loadMessages">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="mailbox-filters">
          <el-select v-model="messageType" clearable placeholder="全部类型" @change="loadMessages">
            <el-option label="上传错误" value="upload_error" />
            <el-option label="问题反馈" value="feedback" />
            <el-option label="工作提醒" value="reminder" />
            <el-option label="项目协作" value="project_membership" />
          </el-select>
          <el-checkbox v-model="onlyUnread" @change="loadMessages">仅看未读</el-checkbox>
          <el-button v-permission="'notification:read'" text type="primary" @click="markAllRead">全部已读</el-button>
        </div>
      </div>

      <div v-permission="'notification:publish'" class="reminder-composer">
        <el-input v-model="reminderTitle" placeholder="提醒标题" maxlength="80" />
        <el-input v-model="reminderContent" type="textarea" :rows="2" placeholder="提醒内容" maxlength="300" show-word-limit />
        <el-button v-permission="'notification:publish'" type="primary" :disabled="!reminderTitle.trim()" @click="createReminder">发布提醒</el-button>
      </div>

      <div v-loading="messagesLoading" class="message-list">
        <button
          v-for="item in messages"
          :key="item.id"
          class="message-item"
          :class="{ unread: !item.isRead }"
          :disabled="!authStore.hasPermission('notification:read')"
          @click="markRead(item)"
        >
          <span class="message-accent" :class="item.severity"></span>
          <span class="message-body">
            <span class="message-title-row">
              <strong>{{ item.title }}</strong>
              <el-tag size="small" effect="plain">{{ messageTypeText(item.messageType) }}</el-tag>
            </span>
            <span class="message-content">{{ item.content || '暂无详细说明' }}</span>
            <span class="message-meta">{{ item.createdByName || '系统' }} · {{ formatMessageTime(item.createdAt) }}</span>
          </span>
        </button>
        <el-empty v-if="!messagesLoading && !messages.length" description="暂无站内消息" :image-size="72" />
      </div>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { modelApi } from '@/api/model'
import { notificationApi } from '@/api/index-modules'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const windowWidth = ref(window.innerWidth)
const manualCollapse = ref(false)
const isCollapse = ref(windowWidth.value < 1024)
const sidebarStats = ref({ totalStorageBytes: 0, memberCount: 0, downloadCount: 0 })
const mailboxVisible = ref(false)
const messagesLoading = ref(false)
const messages = ref<any[]>([])
const unreadCount = ref(0)
const messageKeyword = ref('')
const messageType = ref('')
const onlyUnread = ref(false)
const reminderTitle = ref('')
const reminderContent = ref('')
let inboxTimer: number | undefined

const COLLAPSE_BREAKPOINT = 1024
const MOBILE_BREAKPOINT = 768

watch(windowWidth, (width) => {
  if (!manualCollapse.value) {
    isCollapse.value = width < COLLAPSE_BREAKPOINT
  }
})

const toggleCollapse = () => {
  manualCollapse.value = true
  isCollapse.value = !isCollapse.value
}

const isMobile = computed(() => windowWidth.value < MOBILE_BREAKPOINT)

const routeMeta = computed(() => ({
  title: (route.meta.title as string) || '',
  group: (route.meta.group as string) || ''
}))
const breadcrumbItems = computed(() => {
  const configured = route.meta.breadcrumb as Array<{ title: string; to?: string }> | undefined
  if (configured?.length) return configured
  const items: Array<{ title: string; to?: string }> = []
  if (routeMeta.value.group) items.push({ title: routeMeta.value.group })
  items.push({ title: routeMeta.value.title || '页面' })
  return items
})
const hasDataPermission = computed(() => [
  'model:category_view',
  'project:category_view',
  'model:process_records_view',
  'model:upload_records_view',
  'model:download_records_view',
].some(code => authStore.hasPermission(code)))
const hasStoragePermission = computed(() => ['storage:asset_view', 'storage:view'].some(code => authStore.hasPermission(code)))
const hasOrganizationPermission = computed(() => [
  'user:view',
  'role:view',
  'registration:view',
  'invitation:view',
  'invitation:create',
  'invitation:revoke',
].some(code => authStore.hasPermission(code)))

const handleCommand = (command: string) => {
  if (command === 'logout') {
    authStore.logout()
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

const formatStorage = (bytes: number) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++ }
  return `${value.toFixed(unit >= 3 ? 1 : 0)} ${units[unit]}`
}

const loadUnreadCount = async () => {
  if (!authStore.hasPermission('notification:view')) return
  try { unreadCount.value = (await notificationApi.getUnreadCount()).data.count || 0 } catch { /* 静默刷新 */ }
}

const loadMessages = async () => {
  if (!authStore.hasPermission('notification:view')) return
  messagesLoading.value = true
  try {
    const res = await notificationApi.getMessages({
      keyword: messageKeyword.value || undefined,
      type: messageType.value || undefined,
      unread: onlyUnread.value || undefined,
      page: 0,
      size: 100
    })
    messages.value = res.data.list || []
    await loadUnreadCount()
  } finally { messagesLoading.value = false }
}

const openMailbox = async () => {
  mailboxVisible.value = true
  await loadMessages()
}

const markRead = async (item: any) => {
  if (item.isRead) return
  await notificationApi.markRead(item.id)
  item.isRead = true
  unreadCount.value = Math.max(0, unreadCount.value - 1)
}

const markAllRead = async () => {
  await notificationApi.markAllRead()
  messages.value.forEach(item => { item.isRead = true })
  unreadCount.value = 0
}

const createReminder = async () => {
  await notificationApi.createReminder({ title: reminderTitle.value.trim(), content: reminderContent.value.trim() })
  reminderTitle.value = ''
  reminderContent.value = ''
  ElMessage.success('提醒已发布')
  await loadMessages()
}

const messageTypeText = (type: string) => ({ upload_error: '上传错误', feedback: '问题反馈', reminder: '工作提醒', project_membership: '项目协作' }[type] || '系统消息')
const formatMessageTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'

const onResize = () => { windowWidth.value = window.innerWidth }

onMounted(async () => {
  window.addEventListener('resize', onResize)
  try { await authStore.fetchCurrentUser() } catch { /* 保留本地登录信息 */ }
  if (authStore.hasPermission('model:view')) {
    try {
      const res = await modelApi.getLibraryStats()
      sidebarStats.value = res.data
    } catch { /* 页面主体不受统计信息加载影响 */ }
  }
  if (authStore.hasPermission('notification:view')) {
    await loadUnreadCount()
    inboxTimer = window.setInterval(loadUnreadCount, 30000)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (inboxTimer) window.clearInterval(inboxTimer)
})
</script>

<style scoped>
.main-layout { height: 100vh; }
.sidebar {
  background:
    radial-gradient(circle at 20% 0%, var(--sidebar-glow, rgba(45, 149, 119, 0.16)), transparent 30%),
    linear-gradient(180deg, var(--sidebar-bg) 0%, var(--sidebar-bg-dark, var(--sidebar-bg)) 100%);
  transition: width 0.25s, background 0.3s;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0 19px;
  gap: 10px;
  color: #fff;
  cursor: pointer;
  white-space: nowrap;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.logo-mark { display: block; }
.logo-text { font-size: 16px; font-weight: 700; letter-spacing: 0.6px; }
.inbox-badge { margin-left: auto; }
.inbox-trigger { width: 32px; height: 32px; border: 1px solid rgba(125,211,252,.2); border-radius: 9px; color: #cbd5e1; background: rgba(15,23,42,.55); cursor: pointer; display: grid; place-items: center; transition: .2s ease; }
.inbox-trigger:hover { color: #fff; border-color: #62b599; background: rgba(45,149,119,.2); }
.menu-scroll { flex: 1; }
.menu-scroll :deep(.el-menu) { padding: 14px 10px; background: transparent !important; }
.menu-scroll :deep(.el-menu-item),
.menu-scroll :deep(.el-sub-menu__title) {
  height: 48px;
  margin: 4px 0;
  border-radius: 10px;
}
.menu-scroll :deep(.el-sub-menu .el-menu) { padding: 2px 0 4px 10px; }
.menu-scroll :deep(.el-sub-menu .el-menu-item) { min-width: 0; padding-left: 48px !important; }
.sidebar-platform-stats { flex-shrink: 0; padding: 12px 20px; border-top: 1px solid rgba(255,255,255,.07); color: #8291a8; }
.sidebar-platform-stats div { display: flex; align-items: center; gap: 8px; min-height: 25px; font-size: 11px; white-space: nowrap; }
.sidebar-platform-stats .el-icon { color: #8fd1bb; font-size: 13px; }
.sidebar-user {
  flex-shrink: 0;
  padding: 12px;
  border-top: 1px solid rgba(255,255,255,0.08);
}
.sidebar-user :deep(.el-dropdown) { display: block; width: 100%; }
.sidebar-user-trigger {
  min-height: 46px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 11px;
  cursor: pointer;
  color: #cbd5e1;
  transition: background 0.2s;
  outline: none;
}
.sidebar-user-trigger:hover,
.sidebar-user-trigger.active { background: rgba(96,165,250,0.14); }
.sidebar-user.collapsed .sidebar-user-trigger { justify-content: center; padding: 6px 0; }
.sidebar-user-meta { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 2px; }
.sidebar-username { font-size: 13px; color: #f8fafc; overflow: hidden; text-overflow: ellipsis; }
.sidebar-role { font-size: 11px; color: #8291a8; overflow: hidden; text-overflow: ellipsis; }
.sidebar-user-arrow { color: #64748b; font-size: 14px; }
.menu-group {
  padding: 14px 20px 6px;
  font-size: 11px;
  color: #5c6b83;
  letter-spacing: 1px;
  text-transform: uppercase;
  white-space: nowrap;
}
:deep(.el-menu) { border-right: none; }
:deep(.el-menu-item.is-active) {
  background: var(--sidebar-active-bg, #e8f6f0);
  box-shadow: inset 3px 0 0 var(--el-color-primary, #62b599);
}
.header {
  display: flex; justify-content: space-between; align-items: center;
  background: rgba(255,255,255,0.92);
  border-bottom: 1px solid rgba(148,163,184,0.16);
  box-shadow: 0 8px 28px rgba(15,23,42,0.035);
  backdrop-filter: blur(14px);
  padding: 0 26px; height: 68px;
}
.header-left { display: flex; align-items: center; gap: 16px; }
.collapse-btn {
  font-size: 19px; cursor: pointer; color: #475569;
  width: 34px; height: 34px; border-radius: 9px;
  display: inline-flex; align-items: center; justify-content: center;
  transition: all .2s ease;
}
.collapse-btn:hover { color: #1f8067; background: #eff9f5; }
.breadcrumb { font-size: 13px; }
.main-content {
  background:
    radial-gradient(circle at 92% 0%, var(--content-glow, rgba(45,149,119,0.07)), transparent 28%),
    var(--content-bg, #f5f7fb);
  color: var(--content-text, #1e293b);
  overflow-y: auto;
  padding: 26px;
  transition: background 0.3s, color 0.3s;
}
:global(.mailbox-drawer .el-drawer__header) { margin: 0; padding: 22px 22px 14px; border-bottom: 1px solid #e8edf5; }
:global(.mailbox-drawer .el-drawer__body) { padding: 0 20px 20px; background: #f7f9fc; }
.mailbox-header { width: 100%; display: flex; justify-content: space-between; align-items: center; }
.mailbox-header h3 { margin: 0; color: #172033; font-size: 18px; }
.mailbox-header p { margin: 4px 0 0; color: #94a3b8; font-size: 12px; }
.mailbox-tools { position: sticky; top: 0; z-index: 2; padding: 16px 0 12px; background: #f7f9fc; }
.mailbox-filters { margin-top: 10px; display: flex; align-items: center; gap: 10px; }
.mailbox-filters .el-select { width: 130px; }
.reminder-composer { display: grid; gap: 9px; padding: 14px; margin-bottom: 14px; background: #fff; border: 1px solid #e6ebf3; border-radius: 14px; box-shadow: 0 8px 24px rgba(15,23,42,.04); }
.reminder-composer .el-button { justify-self: end; }
.message-list { min-height: 220px; display: grid; gap: 10px; align-content: start; }
.message-item { position: relative; width: 100%; text-align: left; border: 1px solid #e5eaf2; border-radius: 13px; background: #fff; padding: 14px 14px 14px 18px; cursor: pointer; display: flex; overflow: hidden; box-shadow: 0 6px 18px rgba(15,23,42,.035); }
.message-item.unread { border-color: #b9dfd2; background: #f5faf8; }
.message-accent { position: absolute; inset: 0 auto 0 0; width: 4px; background: #94a3b8; }
.message-accent.error { background: #ef4444; }
.message-accent.warning { background: #f59e0b; }
.message-accent.info { background: #2d9577; }
.message-body { min-width: 0; display: grid; gap: 7px; flex: 1; }
.message-title-row { display: flex; justify-content: space-between; align-items: center; gap: 10px; color: #1e293b; }
.message-content { color: #536176; font-size: 13px; line-height: 1.55; white-space: pre-wrap; }
.message-meta { color: #94a3b8; font-size: 11px; }

/* 浅色侧边栏适配 */
body[data-sidebar-theme="light"] .logo { color: #1e293b; border-bottom-color: rgba(0,0,0,0.06); }
body[data-sidebar-theme="light"] .logo-text { color: #1e293b; }
body[data-sidebar-theme="light"] .inbox-trigger { border-color: rgba(0,0,0,0.1); color: #475569; background: rgba(255,255,255,0.8); }
body[data-sidebar-theme="light"] .inbox-trigger:hover { color: var(--el-color-primary); border-color: var(--el-color-primary); background: rgba(255,255,255,0.9); }
body[data-sidebar-theme="light"] .sidebar-platform-stats { border-top-color: rgba(0,0,0,0.07); color: #64748b; }
body[data-sidebar-theme="light"] .sidebar-platform-stats .el-icon { color: var(--el-color-primary); }
body[data-sidebar-theme="light"] .sidebar-user { border-top-color: rgba(0,0,0,0.08); }
body[data-sidebar-theme="light"] .sidebar-user-trigger { color: #334155; }
body[data-sidebar-theme="light"] .sidebar-user-trigger:hover,
body[data-sidebar-theme="light"] .sidebar-user-trigger.active { background: rgba(0,0,0,0.04); }
body[data-sidebar-theme="light"] .sidebar-username { color: #1e293b; }
body[data-sidebar-theme="light"] .sidebar-role { color: #64748b; }
body[data-sidebar-theme="light"] .sidebar-user-arrow { color: #94a3b8; }

/* 清新绿主框架：保持信息结构不变，只统一空间、对比度和交互反馈。 */
.sidebar {
  background: #fbfdfc;
  border-right: 1px solid #e3ede9;
  box-shadow: 8px 0 30px rgba(30, 84, 65, .025);
}
.logo {
  height: 66px;
  padding: 0 18px;
  color: #173c30;
  border-bottom-color: #edf3f0;
}
.logo-text { color: #183b30; font-weight: 680; letter-spacing: .2px; }
.inbox-trigger {
  color: #467065;
  border-color: #d8e7e1;
  background: #f8fcfa;
  box-shadow: none;
}
.inbox-trigger:hover { color: #1f8067; border-color: #8dc6b3; background: #edf8f3; }
.menu-scroll :deep(.el-menu) { padding: 13px 10px; }
.menu-scroll :deep(.el-menu-item),
.menu-scroll :deep(.el-sub-menu__title) {
  height: 46px;
  color: #536960;
  border-radius: 10px;
}
.menu-scroll :deep(.el-menu-item:hover),
.menu-scroll :deep(.el-sub-menu__title:hover) { color: #1f8067 !important; background: #f1f8f5 !important; }
:deep(.el-menu-item.is-active) {
  color: #1d7d64 !important;
  background: #e8f5ef !important;
  box-shadow: inset 3px 0 0 #3a9a7c;
}
.sidebar-platform-stats {
  border-top-color: #e8f0ed;
  color: #758980;
  background: #fafcfb;
}
.sidebar-platform-stats .el-icon { color: #4ca88a; }
.sidebar-user { border-top-color: #e6efeb; background: #f8fbfa; }
.sidebar-user-trigger { color: #486056; }
.sidebar-user-trigger:hover,
.sidebar-user-trigger.active { background: #eaf6f1; }
.sidebar-username { color: #1e3a30; font-weight: 600; }
.sidebar-role, .sidebar-user-arrow { color: #84968f; }
.header {
  height: 66px;
  padding: 0 24px;
  border-bottom-color: #e7efec;
  background: rgba(255,255,255,.9);
  box-shadow: 0 7px 24px rgba(31, 80, 63, .025);
  backdrop-filter: blur(14px);
}
.collapse-btn { color: #60756c; border: 1px solid transparent; }
.collapse-btn:hover { color: #1f8067; border-color: #dcebe5; background: #f1f8f5; }
.main-content {
  padding: 24px;
  background: #f3f7f5;
  color: #18322a;
}
:global(.mailbox-drawer .el-drawer__header) { border-bottom-color: #e5eeea; }
:global(.mailbox-drawer .el-drawer__body), .mailbox-tools { background: #f5f9f7; }
.mailbox-header h3 { color: #18322a; }
.mailbox-header p, .message-meta { color: #8a9c95; }
.reminder-composer, .message-item { border-color: #e1ebe7; box-shadow: 0 7px 20px rgba(25,76,59,.035); }
.message-item.unread { border-color: #a9d5c5; background: #f3faf7; }
.message-accent.info { background: #2d9577; }
.message-title-row { color: #233d33; }
.message-content { color: #596e65; }

@media (max-width: 900px) {
  .header { padding: 0 16px; height: 60px; }
  .main-content { padding: 16px; }
  .collapse-btn { width: 32px; height: 32px; }
}
@media (max-width: 640px) {
  .header { padding: 0 12px; }
  .main-content { padding: 12px; }
  .breadcrumb { font-size: 12px; }
  .logo { padding: 0 14px; }
}
@media (max-width: 480px) {
  .breadcrumb { display: none; }
  .header { justify-content: flex-start; }
}
</style>
