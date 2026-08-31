<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>上传记录</span>
          <el-select v-model="userId" placeholder="全部用户" clearable style="width: 180px" @change="loadRecords">
            <el-option label="全部用户" :value="undefined" />
          </el-select>
        </div>
      </template>
      <el-table :data="records" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="modelId" label="模型ID" width="90">
          <template #default="{ row }">
            <el-link v-if="row.modelId" type="primary" @click="$router.push(`/models/${row.modelId}`)">
              {{ row.modelId }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileCount" label="文件数" width="90" />
        <el-table-column label="总大小" width="110">
          <template #default="{ row }">{{ formatSize(row.totalSize) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadRecords"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { modelApi } from '@/api/model'

const records = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const userId = ref<number | undefined>()

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await modelApi.getUploadRecords({
      userId: userId.value, page: page.value - 1, size: size.value
    })
    records.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const formatSize = (bytes: number) => {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, s = bytes
  while (s >= 1024 && i < units.length - 1) { s /= 1024; i++ }
  return `${s.toFixed(1)} ${units[i]}`
}
const formatTime = (t: string) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(loadRecords)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
