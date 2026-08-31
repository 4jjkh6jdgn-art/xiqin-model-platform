<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'

const DESIGN_THEME_VERSION = 'fresh-green-2026-08'
const freshTheme = {
  name: 'fresh-green',
  color: '#1f8067',
  sidebarType: 'light',
  vars: {
    '--sidebar-bg': '#fbfdfc',
    '--sidebar-bg-dark': '#f5faf7',
    '--sidebar-text': '#536a60',
    '--sidebar-active': '#1f8067',
    '--sidebar-glow': 'transparent',
    '--sidebar-active-bg': '#e8f6f0',
    '--sidebar-active-bg-light': '#f3faf7',
    '--content-bg': '#f3f7f5',
    '--content-glow': 'transparent',
    '--content-text': '#18322a',
  },
}

const applyPrimaryScale = (color: string) => {
  const root = document.documentElement
  const match = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(color)
  root.style.setProperty('--el-color-primary', color)
  if (!match) return
  const rgb = { r: parseInt(match[1], 16), g: parseInt(match[2], 16), b: parseInt(match[3], 16) }
  for (let i = 1; i <= 9; i++) {
    const ratio = i / 10
    const r = Math.round(rgb.r + (255 - rgb.r) * ratio)
    const g = Math.round(rgb.g + (255 - rgb.g) * ratio)
    const b = Math.round(rgb.b + (255 - rgb.b) * ratio)
    root.style.setProperty(`--el-color-primary-light-${i}`, `rgb(${r}, ${g}, ${b})`)
  }
  root.style.setProperty('--el-color-primary-dark-2', `rgb(${Math.round(rgb.r * .8)}, ${Math.round(rgb.g * .8)}, ${Math.round(rgb.b * .8)})`)
}

const applyTheme = (theme: typeof freshTheme) => {
  const root = document.documentElement
  document.body.setAttribute('data-sidebar-theme', theme.sidebarType)
  document.body.setAttribute('data-ui-theme', 'fresh-green')
  applyPrimaryScale(theme.color)
  Object.entries(theme.vars).forEach(([key, value]) => root.style.setProperty(key, value))
}

onMounted(() => {
  let saved: any
  try { saved = JSON.parse(localStorage.getItem('xiqin-theme') || 'null') } catch { /* 使用新版默认主题 */ }

  // 老主题使用的是零散蓝色变量，会覆盖新版视觉系统；首次升级时统一迁移到清新绿。
  if (!saved || saved.designVersion !== DESIGN_THEME_VERSION) {
    applyTheme(freshTheme)
    localStorage.setItem('xiqin-theme', JSON.stringify({ ...freshTheme, full: true, designVersion: DESIGN_THEME_VERSION }))
    return
  }

  applyTheme({
    ...freshTheme,
    ...saved,
    vars: { ...freshTheme.vars, ...(saved.vars || {}) },
  })
})
</script>
