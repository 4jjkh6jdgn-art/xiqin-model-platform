<template>
  <div class="organization-page">
    <div v-if="returnTarget" class="source-navigation">
      <el-button text @click="router.push(returnTarget)">返回来源</el-button>
    </div>
    <el-tabs v-model="activeTab" type="border-card" class="organization-tabs" @tab-change="onTabChange">
      <el-tab-pane label="用户管理" name="users" v-if="authStore.hasPermission('user:view')">
        <UserListView />
      </el-tab-pane>
      <el-tab-pane label="角色管理" name="roles" v-if="authStore.hasPermission('role:view')">
        <RoleListView />
      </el-tab-pane>
      <el-tab-pane label="注册与邀请" name="registrations" v-if="canOpenRegistration">
        <RegistrationReviewView />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import UserListView from '@/views/user/UserListView.vue'
import RoleListView from '@/views/role/RoleListView.vue'
import RegistrationReviewView from '@/views/RegistrationReviewView.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const canOpenRegistration = computed(() => [
  'registration:view',
  'invitation:view',
  'invitation:create',
  'invitation:revoke',
].some(code => authStore.hasPermission(code)))
const returnTarget = computed(() => typeof route.query.returnTo === 'string' && route.query.returnTo.startsWith('/') ? route.query.returnTo : '')

const availableTabs = () => {
  const tabs: string[] = []
  if (authStore.hasPermission('user:view')) tabs.push('users')
  if (authStore.hasPermission('role:view')) tabs.push('roles')
  if (canOpenRegistration.value) tabs.push('registrations')
  return tabs
}

const activeTab = ref('users')

const syncFromQuery = () => {
  const tab = route.query.tab as string
  if (tab && availableTabs().includes(tab)) {
    activeTab.value = tab
  } else {
    activeTab.value = availableTabs()[0] || 'users'
  }
}

onMounted(syncFromQuery)
watch(() => route.query.tab, syncFromQuery)

const onTabChange = (tab: string | number) => {
  router.replace({ path: '/organization', query: { ...route.query, tab: String(tab) } })
}
</script>

<style scoped>
.organization-page {
  padding: 4px;
}
.source-navigation {
  min-height: 36px;
  margin-bottom: 8px;
}
.organization-tabs :deep(.el-tabs__content) {
  padding: 16px;
}
/* 内容区两层（.page-container 与内部 el-card）统一高度 800，内容在卡片内部滚动 */
.organization-tabs :deep(.el-tabs__content .page-container) {
  height: 800px;
}
.organization-tabs :deep(.el-tabs__content .page-container > .el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.organization-tabs :deep(.el-tabs__content .page-container > .el-card > .el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
