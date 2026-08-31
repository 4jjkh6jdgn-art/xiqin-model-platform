<template>
  <div class="page-container">
    <el-row :gutter="20">
      <el-col :span="16">
        <el-card>
          <template #header>个人信息</template>
          <el-form :model="form" label-width="90px" style="max-width: 500px">
            <el-form-item label="用户名">
              <el-input :model-value="authStore.user?.username" disabled />
            </el-form-item>
            <el-form-item label="角色">
              <el-input :model-value="authStore.user?.roleName" disabled />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="form.email" placeholder="邮箱" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="手机号" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="form.password" type="password" placeholder="留空则不修改" show-password />
            </el-form-item>
            <el-form-item>
              <el-button v-permission="'profile:edit'" type="primary" @click="saveProfile" :loading="saving">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>账号信息</template>
          <div class="avatar-section">
            <el-avatar :size="80">{{ authStore.user?.username?.charAt(0)?.toUpperCase() }}</el-avatar>
            <div style="margin-top: 12px; font-size: 18px; font-weight: bold">{{ authStore.user?.username }}</div>
            <el-tag type="primary" style="margin-top: 8px">{{ authStore.user?.roleName }}</el-tag>
          </div>
          <el-divider />
          <div v-if="authStore.user?.permissions?.length" class="permission-section">
            <div class="permission-summary">
              <div class="permission-title">
                <span>拥有的权限</span>
                <el-tag size="small" effect="plain" round>{{ authStore.user.permissions.length }} 项</el-tag>
              </div>
              <el-button
                type="primary"
                link
                :aria-expanded="permissionsExpanded"
                @click="permissionsExpanded = !permissionsExpanded"
              >
                {{ permissionsExpanded ? '收起权限' : '展开权限' }}
              </el-button>
            </div>
            <el-collapse-transition>
              <div v-show="permissionsExpanded" class="permission-list">
                <el-tag v-for="p in authStore.user.permissions" :key="p" size="small" effect="plain">
                  {{ p }}
                </el-tag>
              </div>
            </el-collapse-transition>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 系统配色设置 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="theme-header">
              <span>系统配色</span>
              <el-tag size="small" type="info" effect="plain">更换整个系统主题颜色</el-tag>
            </div>
          </template>
          <div class="theme-presets">
            <div
              v-for="t in presetThemes"
              :key="t.name"
              class="theme-swatch-wrap"
              @click="applyTheme(t)"
            >
              <div
                class="theme-swatch"
                :class="{ active: currentTheme === t.name }"
                :style="{ background: t.primary }"
                :title="t.label"
              >
                <el-icon v-if="currentTheme === t.name" class="check-icon"><Check /></el-icon>
              </div>
              <span class="theme-swatch-label">{{ t.label }}</span>
            </div>
          </div>
          <el-divider>自定义颜色</el-divider>
          <div class="custom-theme-row">
            <el-color-picker v-model="customColor" show-alpha @change="applyCustomColor" />
            <span class="custom-hint">选择自定义主色调，实时预览效果</span>
            <el-button v-if="currentTheme === 'custom'" size="small" @click="resetTheme">恢复默认</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/index-modules'

const authStore = useAuthStore()
const saving = ref(false)
const permissionsExpanded = ref(false)
const form = reactive({ email: '', phone: '', password: '' })

// 系统配色 - 4套内置主题，支持深色/浅色侧边栏
const presetThemes = [
  {
    name: 'tech-blue', label: '科技蓝', primary: '#2563eb', sidebarType: 'dark',
    sidebarBg: '#0f172a', sidebarBgDark: '#0b1220',
    sidebarText: '#94a3b8', sidebarActive: '#dbeafe',
    sidebarGlow: 'rgba(59,130,246,0.16)',
    sidebarActiveBg: 'rgba(59,130,246,0.26)', sidebarActiveBgLight: 'rgba(59,130,246,0.08)',
    contentBg: '#f0f4f8', contentGlow: 'rgba(59,130,246,0.07)', contentText: '#1e293b'
  },
  {
    name: 'polar-purple', label: '极夜紫', primary: '#7c3aed', sidebarType: 'dark',
    sidebarBg: '#1a1030', sidebarBgDark: '#150d28',
    sidebarText: '#c4b5fd', sidebarActive: '#ede9fe',
    sidebarGlow: 'rgba(124,58,237,0.16)',
    sidebarActiveBg: 'rgba(124,58,237,0.26)', sidebarActiveBgLight: 'rgba(124,58,237,0.08)',
    contentBg: '#f5f0fa', contentGlow: 'rgba(124,58,237,0.07)', contentText: '#3b0764'
  },
  {
    name: 'fresh-green', label: '清新绿', primary: '#1f8067', sidebarType: 'light',
    sidebarBg: '#fbfdfc', sidebarBgDark: '#f5faf7',
    sidebarText: '#536a60', sidebarActive: '#1f8067',
    sidebarGlow: 'transparent',
    sidebarActiveBg: '#e8f6f0', sidebarActiveBgLight: '#f3faf7',
    contentBg: '#f3f7f5', contentGlow: 'transparent', contentText: '#18322a'
  },
  {
    name: 'warm-orange', label: '暖阳橙', primary: '#ea580c', sidebarType: 'light',
    sidebarBg: '#ffffff', sidebarBgDark: '#fff7ed',
    sidebarText: '#57534e', sidebarActive: '#ea580c',
    sidebarGlow: 'rgba(234,88,12,0.06)',
    sidebarActiveBg: 'rgba(234,88,12,0.1)', sidebarActiveBgLight: 'rgba(234,88,12,0.04)',
    contentBg: '#fff7ed', contentGlow: 'rgba(234,88,12,0.07)', contentText: '#7c2d12'
  },
]
const DESIGN_THEME_VERSION = 'fresh-green-2026-08'
const currentTheme = ref('fresh-green')
const customColor = ref('#1f8067')

const hexToRgb = (hex: string) => {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : null
}

const applyPrimaryColor = (color: string) => {
  const root = document.documentElement
  root.style.setProperty('--el-color-primary', color)
  const rgb = hexToRgb(color)
  if (rgb) {
    for (let i = 1; i <= 9; i++) {
      const ratio = i / 10
      const r = Math.round(rgb.r + (255 - rgb.r) * ratio)
      const g = Math.round(rgb.g + (255 - rgb.g) * ratio)
      const b = Math.round(rgb.b + (255 - rgb.b) * ratio)
      root.style.setProperty(`--el-color-primary-light-${i}`, `rgb(${r}, ${g}, ${b})`)
    }
    root.style.setProperty('--el-color-primary-dark-2', `rgb(${Math.round(rgb.r * 0.8)}, ${Math.round(rgb.g * 0.8)}, ${Math.round(rgb.b * 0.8)})`)
  }
}

const applyFullTheme = (theme: typeof presetThemes[0]) => {
  const root = document.documentElement
  // 设置侧边栏类型
  document.body.setAttribute('data-sidebar-theme', theme.sidebarType)
  // 主色调
  applyPrimaryColor(theme.primary)
  // 侧边栏
  root.style.setProperty('--sidebar-bg', theme.sidebarBg)
  root.style.setProperty('--sidebar-bg-dark', theme.sidebarBgDark)
  root.style.setProperty('--sidebar-text', theme.sidebarText)
  root.style.setProperty('--sidebar-active', theme.sidebarActive)
  root.style.setProperty('--sidebar-glow', theme.sidebarGlow)
  root.style.setProperty('--sidebar-active-bg', theme.sidebarActiveBg)
  root.style.setProperty('--sidebar-active-bg-light', theme.sidebarActiveBgLight)
  // 内容区
  root.style.setProperty('--content-bg', theme.contentBg)
  root.style.setProperty('--content-glow', theme.contentGlow)
  root.style.setProperty('--content-text', theme.contentText)
}

const applyTheme = (theme: typeof presetThemes[0]) => {
  currentTheme.value = theme.name
  customColor.value = theme.primary
  applyFullTheme(theme)
  // 存储完整主题变量，供App.vue启动时恢复
  const vars: Record<string, string> = {
    '--sidebar-bg': theme.sidebarBg,
    '--sidebar-bg-dark': theme.sidebarBgDark,
    '--sidebar-text': theme.sidebarText,
    '--sidebar-active': theme.sidebarActive,
    '--sidebar-glow': theme.sidebarGlow,
    '--sidebar-active-bg': theme.sidebarActiveBg,
    '--sidebar-active-bg-light': theme.sidebarActiveBgLight,
    '--content-bg': theme.contentBg,
    '--content-glow': theme.contentGlow,
    '--content-text': theme.contentText,
  }
  localStorage.setItem('xiqin-theme', JSON.stringify({ name: theme.name, color: theme.primary, full: true, sidebarType: theme.sidebarType, vars, designVersion: DESIGN_THEME_VERSION }))
  ElMessage.success(`已切换到「${theme.label}」主题`)
}

const applyCustomColor = (color: string) => {
  if (!color) return
  currentTheme.value = 'custom'
  applyPrimaryColor(color)
  localStorage.setItem('xiqin-theme', JSON.stringify({ name: 'custom', color, full: false, sidebarType: 'light', designVersion: DESIGN_THEME_VERSION }))
}

const resetTheme = () => {
  const defaultTheme = presetThemes.find(theme => theme.name === 'fresh-green')!
  applyTheme(defaultTheme)
}

onMounted(async () => {
  try {
    await authStore.fetchCurrentUser()
    form.email = authStore.user?.email || ''
    form.phone = authStore.user?.phone || ''
  } catch (e) { /* ignore */ }

  // 加载保存的主题
  const saved = localStorage.getItem('xiqin-theme')
  if (saved) {
    try {
      const { name, color, full } = JSON.parse(saved)
      currentTheme.value = name
      customColor.value = color
      if (full && name !== 'custom') {
        const theme = presetThemes.find(t => t.name === name)
        if (theme) applyFullTheme(theme)
      } else {
        applyPrimaryColor(color)
      }
    } catch (e) { /* ignore */ }
  } else {
    const defaultTheme = presetThemes.find(theme => theme.name === 'fresh-green')!
    applyTheme(defaultTheme)
  }
})

const saveProfile = async () => {
  saving.value = true
  try {
    const data: any = { email: form.email, phone: form.phone }
    if (form.password) data.password = form.password
    await userApi.updateProfile(data)
    ElMessage.success('保存成功')
    await authStore.fetchCurrentUser()
    form.password = ''
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.avatar-section { display: flex; flex-direction: column; align-items: center; padding: 20px 0; }
.permission-section { min-width: 0; }
.permission-summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.permission-title { display: flex; align-items: center; gap: 8px; min-width: 0; font-size: 13px; color: #606266; }
.permission-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 220px;
  margin-top: 12px;
  padding: 10px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.theme-header { display: flex; align-items: center; justify-content: space-between; }
.theme-presets { display: flex; flex-wrap: wrap; gap: 20px; padding: 8px 0; }
.theme-swatch-wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; cursor: pointer; }
.theme-swatch {
  width: 52px; height: 52px; border-radius: 14px;
  cursor: pointer; position: relative;
  border: 3px solid transparent;
  transition: all 0.25s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
}
.theme-swatch:hover { transform: scale(1.08); box-shadow: 0 4px 14px rgba(0,0,0,0.18); }
.theme-swatch.active { border-color: #fff; box-shadow: 0 0 0 2px var(--el-color-primary), 0 4px 14px rgba(0,0,0,0.2); }
.theme-swatch .check-icon { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); color: #fff; font-size: 24px; font-weight: bold; }
.theme-swatch-label { font-size: 12px; color: #64748b; white-space: nowrap; }
.custom-theme-row { display: flex; align-items: center; gap: 16px; padding: 8px 0; }
.custom-hint { color: #909399; font-size: 13px; }
</style>
