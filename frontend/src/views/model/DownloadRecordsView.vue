<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>下载记录</span>
        </div>
      </template>
      <el-table :data="records" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="modelId" label="模型ID" width="90">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/models/${row.modelId}`)">{{ row.modelId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="下载时间" width="180">
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

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await modelApi.getDownloadRecords({ page: page.value - 1, size: size.value })
    records.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const formatTime = (t: string) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(loadRecords)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
