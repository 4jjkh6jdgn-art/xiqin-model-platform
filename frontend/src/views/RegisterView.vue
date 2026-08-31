<template>
  <div class="register-container">
    <div class="register-card">
      <h1>注册账号</h1>
      <p class="subtitle">西秦管理平台</p>
      <el-alert
        v-if="!form.invitationCode"
        type="info"
        :closable="false"
        title="无邀请码的注册需等待组长审批后方可登录"
        style="margin-bottom: 20px"
      />
      <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（3-32字符）" size="large" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（6-64字符）" size="large" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" size="large" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="邮箱（选填）" size="large" prefix-icon="Message" />
        </el-form-item>
        <el-form-item prop="phone">
          <el-input v-model="form.phone" placeholder="手机号（选填）" size="large" prefix-icon="Iphone" />
        </el-form-item>
        <el-form-item prop="invitationCode">
          <el-input v-model="form.invitationCode" placeholder="邀请码（有则直接激活，选填）" size="large" prefix-icon="Ticket" @input="codeValid = null">
            <template #append>
              <el-button @click="validateCode" :loading="validating">验证</el-button>
            </template>
          </el-input>
          <div v-if="codeValid === true" class="code-tip valid">✓ 邀请码有效，注册后直接激活</div>
          <div v-if="codeValid === false" class="code-tip invalid">✗ 邀请码无效或已过期</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleRegister">
            注 册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="footer-links">
        <span>已有账号？</span>
        <el-link type="primary" @click="$router.push('/login')">返回登录</el-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import api from '@/api'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)
const validating = ref(false)
const codeValid = ref<boolean | null>(null)

const form = reactive({
  username: '', password: '', confirmPassword: '',
  email: '', phone: '', invitationCode: ''
})

const validatePassword = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度3-32个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度6-64个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const validateCode = async () => {
  if (!form.invitationCode) return
  form.invitationCode = form.invitationCode.trim().toUpperCase()
  validating.value = true
  try {
    const res = await api.get('/auth/invitation/validate', { params: { code: form.invitationCode } })
    codeValid.value = res.data.valid
  } catch (e) {
    codeValid.value = false
  } finally {
    validating.value = false
  }
}

onMounted(() => {
  const invitation = typeof route.query.invitation === 'string' ? route.query.invitation.trim().toUpperCase() : ''
  if (invitation) {
    form.invitationCode = invitation
    validateCode()
  }
})

const handleRegister = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    const msg = await authStore.register({
      username: form.username,
      password: form.password,
      email: form.email || undefined,
      phone: form.phone || undefined,
      invitationCode: form.invitationCode || undefined
    })
    ElMessage.success(typeof msg === 'string' ? msg : '注册成功')
    router.push('/login')
  } catch (e) {
    // handled in interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  background: #edf6f2;
  overflow-y: auto;
}
.register-card {
  width: 440px; padding: 40px; background: rgba(255,255,255,.98);
  border: 1px solid #deebe6;
  border-radius: 16px; box-shadow: 0 24px 64px rgba(25,76,59,.12);
  margin: 20px 0;
}
h1 { text-align: center; font-size: 22px; color: #18322a; margin-bottom: 8px; }
.subtitle { text-align: center; color: #909399; margin-bottom: 24px; }
.footer-links { text-align: center; margin-top: 16px; color: #909399; }
.code-tip { font-size: 12px; margin-top: 4px; }
.code-tip.valid { color: #67c23a; }
.code-tip.invalid { color: #f56c6c; }
</style>
