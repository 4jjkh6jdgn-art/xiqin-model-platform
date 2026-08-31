<template>
  <el-dialog
    :model-value="modelValue"
    width="min(1120px, 94vw)"
    top="3vh"
    append-to-body
    destroy-on-close
    class="media-preview-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @opened="resetView"
    @closed="handleClosed"
  >
    <template #header>
      <div class="media-dialog-heading">
        <span class="media-kind">{{ mediaType === 'video' ? '视频' : '图片' }}</span>
        <div>
          <strong :title="fileName">{{ fileName || '媒体预览' }}</strong>
          <small>{{ mediaType === 'video' ? '支持播放、暂停、进度与音量控制' : '滚轮缩放，双击恢复原始视图' }}</small>
        </div>
      </div>
    </template>

    <div
      v-loading="loading"
      class="media-preview-stage"
      :class="`is-${mediaType}`"
      @wheel="handleWheel"
    >
      <el-result v-if="displayError" icon="error" title="媒体加载失败" :sub-title="displayError" />
      <div v-else-if="mediaType === 'image' && source" class="image-canvas" @dblclick="resetView">
        <img
          :src="source"
          :alt="fileName"
          :style="imageStyle"
          draggable="false"
          @load="localError = ''"
          @error="handleLoadError('图片无法加载，可能是文件损坏或浏览器不支持该格式')"
        />
      </div>
      <video
        v-else-if="mediaType === 'video' && source"
        ref="videoRef"
        :src="source"
        controls
        autoplay
        playsinline
        preload="metadata"
        @loadeddata="localError = ''"
        @error="handleLoadError('视频无法播放，请确认编码为浏览器支持的 H.264、VP8 或 VP9')"
      >
        当前浏览器不支持视频播放。
      </video>
      <el-empty v-else-if="!loading" description="暂无可预览内容" :image-size="78" />
    </div>

    <template #footer>
      <div class="media-dialog-footer">
        <div v-if="mediaType === 'image'" class="image-tools" aria-label="图片查看工具">
          <el-button circle :icon="ZoomOut" title="缩小" aria-label="缩小图片" @click="zoomBy(-0.2)" />
          <button type="button" class="zoom-value" title="恢复原始比例" @click="resetView">{{ Math.round(scale * 100) }}%</button>
          <el-button circle :icon="ZoomIn" title="放大" aria-label="放大图片" @click="zoomBy(0.2)" />
          <span class="tool-divider"></span>
          <el-button circle :icon="RefreshLeft" title="向左旋转" aria-label="向左旋转图片" @click="rotateBy(-90)" />
          <el-button circle :icon="RefreshRight" title="向右旋转" aria-label="向右旋转图片" @click="rotateBy(90)" />
        </div>
        <span v-else class="video-tip">若视频无法播放，请检查文件编码或下载后使用本地播放器。</span>
        <div class="media-footer-actions">
          <el-button @click="emit('update:modelValue', false)">关闭</el-button>
          <el-button type="primary" :disabled="!source" @click="emit('download')">
            <el-icon><Download /></el-icon>下载原文件
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Download, RefreshLeft, RefreshRight, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import type { MediaPreviewType } from '@/utils/mediaPreview'

const props = withDefaults(defineProps<{
  modelValue: boolean
  source?: string | null
  fileName?: string
  mediaType: MediaPreviewType
  loading?: boolean
  error?: string
}>(), {
  source: '',
  fileName: '',
  loading: false,
  error: '',
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'download'): void
}>()

const videoRef = ref<HTMLVideoElement>()
const scale = ref(1)
const rotation = ref(0)
const localError = ref('')
const displayError = computed(() => props.error || localError.value)
const imageStyle = computed(() => ({ transform: `scale(${scale.value}) rotate(${rotation.value}deg)` }))

const resetView = () => {
  scale.value = 1
  rotation.value = 0
  localError.value = ''
}

const zoomBy = (step: number) => {
  scale.value = Math.min(5, Math.max(0.2, Number((scale.value + step).toFixed(2))))
}

const rotateBy = (degrees: number) => {
  rotation.value = (rotation.value + degrees) % 360
}

const handleWheel = (event: WheelEvent) => {
  if (props.mediaType !== 'image' || props.loading || displayError.value) return
  event.preventDefault()
  zoomBy(event.deltaY < 0 ? 0.1 : -0.1)
}

const handleLoadError = (message: string) => {
  localError.value = message
}

const handleClosed = () => {
  videoRef.value?.pause()
  resetView()
}

watch(() => [props.source, props.mediaType], resetView)
</script>

<style scoped>
:global(.media-preview-dialog) { max-width: 1120px; overflow: hidden; border-radius: 18px; box-shadow: 0 26px 70px rgba(15, 43, 35, 0.24); }
:global(.media-preview-dialog .el-dialog__header) { margin: 0; padding: 18px 22px; border-bottom: 1px solid #e4eee9; }
:global(.media-preview-dialog .el-dialog__body) { padding: 0; }
:global(.media-preview-dialog .el-dialog__footer) { padding: 14px 20px; border-top: 1px solid #e4eee9; }
.media-dialog-heading { display: flex; align-items: center; gap: 12px; min-width: 0; padding-right: 32px; }
.media-dialog-heading > div { min-width: 0; display: grid; gap: 3px; }
.media-dialog-heading strong { overflow: hidden; color: #17362d; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.media-dialog-heading small { color: #80958e; font-size: 12px; }
.media-kind { flex: none; padding: 5px 10px; color: #16775f; font-size: 12px; font-weight: 700; background: #e7f5ef; border: 1px solid #cce9dd; border-radius: 999px; }
.media-preview-stage { position: relative; height: min(70vh, 720px); min-height: 420px; display: grid; place-items: center; overflow: auto; background: #081321; }
.image-canvas { width: 100%; height: 100%; display: grid; place-items: center; overflow: auto; cursor: zoom-in; }
.image-canvas img { max-width: 92%; max-height: 92%; object-fit: contain; transform-origin: center; transition: transform 0.16s ease; user-select: none; }
.media-preview-stage video { width: 100%; height: 100%; max-height: 70vh; background: #050b12; object-fit: contain; }
.media-preview-stage :deep(.el-result__title p) { color: #f3f8f6; }
.media-preview-stage :deep(.el-result__subtitle p) { color: #9fb2ac; }
.media-dialog-footer { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.image-tools, .media-footer-actions { display: flex; align-items: center; gap: 8px; }
.image-tools :deep(.el-button), .media-footer-actions :deep(.el-button) { margin-left: 0; }
.zoom-value { min-width: 58px; padding: 6px 8px; color: #2f6254; font: inherit; font-size: 13px; background: #f0f7f4; border: 1px solid #d9e9e2; border-radius: 8px; cursor: pointer; }
.tool-divider { width: 1px; height: 22px; margin: 0 3px; background: #dbe7e2; }
.video-tip { color: #7b8f88; font-size: 12px; }
@media (max-width: 720px) {
  :global(.media-preview-dialog) { width: 96vw !important; margin-top: 2vh; }
  .media-preview-stage { height: 64vh; min-height: 320px; }
  .media-dialog-heading small, .video-tip { display: none; }
  .media-dialog-footer { align-items: flex-end; }
  .media-footer-actions .el-button:first-child { display: none; }
}
</style>
