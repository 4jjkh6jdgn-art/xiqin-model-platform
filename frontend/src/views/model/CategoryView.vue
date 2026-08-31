<template>
  <div class="page-container">
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>模型分类管理</span>
              <el-button v-permission="'model:category_create'" type="primary" size="small" @click="openCreate('model')">
                <el-icon><Plus /></el-icon> 新建
              </el-button>
            </div>
          </template>
          <el-table :data="modelCategories" v-loading="loading" stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="分类名称" />
            <el-table-column prop="code" label="编码" width="120" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button v-permission="'model:category_edit'" size="small" text type="primary" @click="openEdit('model', row)">编辑</el-button>
                  <el-popconfirm v-permission="'model:category_delete'" title="确定删除？" @confirm="deleteCategory('model', row.id)">
                  <template #reference>
                    <el-button size="small" text type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>项目分类管理</span>
              <el-button v-permission="'project:category_create'" type="primary" size="small" @click="openCreate('project')">
                <el-icon><Plus /></el-icon> 新建
              </el-button>
            </div>
          </template>
          <el-table :data="projectCategories" v-loading="loading" stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="分类名称" />
            <el-table-column prop="code" label="编码" width="120" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button v-permission="'project:category_edit'" size="small" text type="primary" @click="openEdit('project', row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新建分类'" width="450px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="分类名称">
          <el-input v-model="form.name" placeholder="分类名称" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.code" placeholder="英文编码（如 character）" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-permission="currentType === 'model'
            ? (editingId ? 'model:category_edit' : 'model:category_create')
            : (editingId ? 'project:category_edit' : 'project:category_create')"
          type="primary"
          @click="save"
        >保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { modelApi } from '@/api/model'
import { projectApi } from '@/api/project'

const modelCategories = ref<any[]>([])
const projectCategories = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const currentType = ref<'model' | 'project'>('model')
const form = reactive({ name: '', code: '', description: '' })

const loadCategories = async () => {
  loading.value = true
  try {
    const [mc, pc] = await Promise.all([
      modelApi.getCategories(),
      projectApi.getCategories()
    ])
    modelCategories.value = mc.data
    projectCategories.value = pc.data
  } finally {
    loading.value = false
  }
}

const openCreate = (type: 'model' | 'project') => {
  currentType.value = type
  editingId.value = null
  form.name = ''
  form.code = ''
  form.description = ''
  dialogVisible.value = true
}

const openEdit = (type: 'model' | 'project', row: any) => {
  currentType.value = type
  editingId.value = row.id
  form.name = row.name
  form.code = row.code
  form.description = row.description
  dialogVisible.value = true
}

const save = async () => {
  if (!form.name?.trim()) return ElMessage.warning('请输入分类名称')
  form.name = form.name.trim()
  if (currentType.value === 'model') {
    if (editingId.value) await modelApi.updateCategory(editingId.value, form)
    else await modelApi.createCategory(form)
  } else {
    if (editingId.value) {
      await projectApi.updateCategory(editingId.value, form)
    } else {
      await projectApi.createCategory(form)
    }
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadCategories()
}

const deleteCategory = async (type: 'model' | 'project', id: number) => {
  if (type === 'model') await modelApi.deleteCategory(id)
  else await projectApi.deleteCategory(id)
  ElMessage.success('删除成功')
  loadCategories()
}

onMounted(loadCategories)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
