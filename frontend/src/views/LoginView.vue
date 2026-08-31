<template>
  <div class="login-container">
    <!-- Brand Panel -->
    <div class="brand-panel">
      <div class="brand-content">
        <div class="brand-logo">
          <svg viewBox="0 0 32 32" width="44" height="44" aria-hidden="true">
            <defs>
              <linearGradient id="login-grad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#62b599" />
                <stop offset="100%" stop-color="#8fcdb8" />
              </linearGradient>
            </defs>
            <path d="M16 3 L28 10 V22 L16 29 L4 22 V10 Z" fill="url(#login-grad)" opacity="0.25" />
            <path d="M16 3 L28 10 V22 L16 29 L4 22 V10 Z" fill="none" stroke="url(#login-grad)" stroke-width="1.6" />
            <path d="M16 3 L16 15 M4 10 L16 15 L28 10 M16 15 L16 29" fill="none" stroke="url(#login-grad)" stroke-width="1.6" stroke-linejoin="round" />
          </svg>
          <div>
            <div class="brand-name">西秦管理平台</div>
            <div class="brand-sub">XIQIN MANAGEMENT PLATFORM</div>
          </div>
        </div>

        <h2 class="brand-slogan">让 3D 模型资产<br />看得见、管得住、用得上</h2>

        <div class="feature-list">
          <div class="feature-item">
            <div class="feature-icon" style="background: #e1f2eb; color: #1f8067">
              <el-icon :size="20"><View /></el-icon>
            </div>
            <div>
              <div class="feature-name">3D 实时预览</div>
              <div class="feature-desc">FBX / OBJ / GLB 浏览器直接预览，无需下载</div>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon" style="background: #edf7f2; color: #5a9c83">
              <el-icon :size="20"><FolderOpened /></el-icon>
            </div>
            <div>
              <div class="feature-name">文件夹一键上传</div>
              <div class="feature-desc">自动识别主模型与贴图文件，自动关联</div>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon" style="background: #e9f5ec; color: #58a769">
              <el-icon :size="20"><Lock /></el-icon>
            </div>
            <div>
              <div class="feature-name">全流程权限管控</div>
              <div class="feature-desc">注册审批 + 角色权限 + 操作记录留痕</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Form Panel -->
    <div class="form-panel">
      <div class="login-card">
        <h1>欢迎回来</h1>
        <p class="subtitle">登录以继续管理你的模型资产</p>
        <el-form :model="form" :rules="rules" ref="formRef" label-width="0" size="large">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleLogin">
              登 录
            </el-button>
          </el-form-item>
        </el-form>
        <div class="footer-links">
          <span>还没有账号？</span>
          <el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
        </div>
      </div>
      <div class="copyright">西秦管理平台 · 内部协作与资产管理</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getAccessibleHome } from '@/utils/navigation'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push(getAccessibleHome(authStore))
  } catch (e: any) {
    // error handled in interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  background: #f3f7f5;
}
.brand-panel {
  flex: 1.2;
  display: flex; align-items: center; justify-content: center;
  padding: 60px 40px;
  background: #e8f5ef;
  border-right: 1px solid #d9e9e2;
}
.brand-content { max-width: 480px; }
.brand-logo { display: flex; align-items: center; gap: 14px; }
.brand-name { font-size: 22px; font-weight: 700; color: #183c30; letter-spacing: .6px; }
.brand-sub { font-size: 12px; color: #769087; letter-spacing: 1.8px; margin-top: 2px; }
.brand-slogan {
  font-size: 32px; line-height: 1.4; color: #173c30;
  font-weight: 600; margin: 36px 0 32px;
}
.feature-list { display: flex; flex-direction: column; gap: 22px; }
.feature-item { display: flex; align-items: flex-start; gap: 14px; }
.feature-icon {
  width: 42px; height: 42px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.feature-name { font-size: 15px; font-weight: 600; color: #28483c; }
.feature-desc { font-size: 13px; color: #70877e; margin-top: 3px; }

.form-panel {
  flex: 1;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 40px;
}
.login-card {
  width: 380px; padding: 40px; background: rgba(255,255,255,0.98);
  border: 1px solid #deebe6;
  border-radius: 16px; box-shadow: 0 24px 64px rgba(25,76,59,.12);
}
h1 { text-align: center; font-size: 24px; color: #18322a; margin-bottom: 6px; }
.subtitle { text-align: center; color: #909399; margin-bottom: 28px; font-size: 13px; }
.footer-links { text-align: center; margin-top: 8px; color: #909399; font-size: 13px; }
.copyright { margin-top: 28px; color: #7c9088; font-size: 12px; }

@media (max-width: 900px) {
  .brand-panel { display: none; }
}
</style>
