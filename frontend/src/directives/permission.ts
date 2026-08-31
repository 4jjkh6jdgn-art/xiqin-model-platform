import type { Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'

type PermissionBinding = string | string[]

const isAllowed = (value: PermissionBinding) => {
  const authStore = useAuthStore()
  const permissions = Array.isArray(value) ? value : [value]
  return authStore.isAdmin || permissions.some(code => authStore.hasPermission(code))
}

const applyPermission = (el: HTMLElement, value: PermissionBinding) => {
  el.style.display = isAllowed(value) ? '' : 'none'
}

const permission: Directive<HTMLElement, PermissionBinding> = {
  mounted(el, binding) {
    applyPermission(el, binding.value)
  },
  updated(el, binding) {
    applyPermission(el, binding.value)
  },
}

export default permission
