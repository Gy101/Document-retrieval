<template>
  <div
    class="file-uploader"
    :class="{ 'is-dragover': dragover, 'is-disabled': disabled, 'has-file': !!modelValue }"
    @dragenter.prevent="dragover = true"
    @dragover.prevent="dragover = true"
    @dragleave.prevent="dragover = false"
    @drop.prevent="onDrop"
  >
    <input
      ref="inputRef"
      type="file"
      class="native-input"
      :accept="accept"
      :disabled="disabled"
      @change="onInputChange"
    />

    <div v-if="!modelValue" class="drop-zone" @click="openPicker">
      <div class="drop-icon">
        <el-icon><UploadFilled /></el-icon>
      </div>
      <div class="drop-title">拖拽 Word 文档到此处，或 <span>点击选择</span></div>
      <div class="drop-hint">仅支持 {{ acceptLabel }} 格式，单个文件不超过 {{ maxSizeMb }} MB</div>
      <div class="drop-meta">
        <span><el-icon><Lock /></el-icon> 本地安全存储</span>
        <span><el-icon><Cpu /></el-icon> 自动解析格式</span>
      </div>
    </div>

    <div v-else class="file-card">
      <div class="file-icon">
        <el-icon><Document /></el-icon>
        <span class="file-ext">DOCX</span>
      </div>
      <div class="file-main">
        <div class="file-name" :title="modelValue.name">{{ modelValue.name }}</div>
        <div class="file-sub">
          {{ formatFileSize(modelValue.size) }}
          <span class="dot">·</span>
          已就绪，等待上传
        </div>
      </div>
      <el-button v-if="!disabled" text circle class="file-remove" @click.stop="clearFile">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { formatFileSize } from '@/utils/format'

const props = defineProps({
  modelValue: { type: Object, default: null },
  accept: { type: String, default: '.docx' },
  maxSizeMb: { type: Number, default: 50 },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])
const inputRef = ref(null)
const dragover = ref(false)

const acceptLabel = computed(() => {
  if (props.accept.includes('docx')) return 'DOCX'
  return props.accept.replaceAll('.', '').toUpperCase()
})

function openPicker() {
  if (props.disabled) return
  inputRef.value?.click()
}

function onInputChange(event) {
  const file = event.target.files?.[0]
  if (file) acceptFile(file)
  event.target.value = ''
}

function onDrop(event) {
  dragover.value = false
  if (props.disabled) return
  const file = event.dataTransfer?.files?.[0]
  if (file) acceptFile(file)
}

function acceptFile(file) {
  const lower = file.name.toLowerCase()
  if (!lower.endsWith('.docx')) {
    ElMessage.warning('仅支持 .docx 格式的 Word 文档')
    return
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.warning(`文件大小不能超过 ${props.maxSizeMb} MB`)
    return
  }
  emit('update:modelValue', file)
  emit('change', file)
}

function clearFile() {
  emit('update:modelValue', null)
  emit('change', null)
}

defineExpose({ openPicker, clearFile })
</script>

<style scoped>
.file-uploader {
  border: 1.5px dashed #d5dbe8;
  border-radius: 14px;
  background: linear-gradient(180deg, #fbfcff 0%, #f7f9fe 100%);
  transition: 0.2s ease;
  overflow: hidden;
}

.file-uploader:hover,
.file-uploader.is-dragover {
  border-color: var(--dc-primary);
  background: #f5f5ff;
  box-shadow: 0 0 0 3px rgba(91, 91, 214, 0.08);
}

.file-uploader.is-disabled { opacity: 0.65; cursor: not-allowed; }
.file-uploader.has-file { border-style: solid; border-color: #dfe4ef; background: #fff; }

.native-input { display: none; }

.drop-zone {
  padding: 34px 20px 26px;
  text-align: center;
  cursor: pointer;
}

.drop-icon {
  width: 54px;
  height: 54px;
  margin: 0 auto 14px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #eef0ff, #e4e7ff);
  color: var(--dc-primary);
  font-size: 26px;
  box-shadow: inset 0 0 0 1px rgba(91, 91, 214, 0.08);
}

.drop-title {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.drop-title span { color: var(--dc-primary); }

.drop-hint {
  margin-top: 7px;
  font-size: 12px;
  color: #94a3b8;
}

.drop-meta {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 18px;
  padding-top: 15px;
  border-top: 1px dashed #e8ecf4;
  color: #8a97ab;
  font-size: 11px;
}

.drop-meta span { display: inline-flex; align-items: center; gap: 5px; }

.file-card {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 15px 16px;
}

.file-icon {
  position: relative;
  width: 46px;
  height: 52px;
  border-radius: 9px;
  background: linear-gradient(160deg, #eef2ff, #e0e7ff);
  color: #4f46e5;
  display: grid;
  place-items: center;
  font-size: 24px;
  flex: 0 0 auto;
}

.file-ext {
  position: absolute;
  bottom: 4px;
  font-size: 7px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #6366f1;
}

.file-main { flex: 1; min-width: 0; }

.file-name {
  font-size: 13.5px;
  font-weight: 650;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-sub {
  margin-top: 5px;
  font-size: 11.5px;
  color: #94a3b8;
}

.file-sub .dot { margin: 0 4px; }

.file-remove { color: #94a3b8; }
.file-remove:hover { color: #dc2626; background: #fef2f2; }
</style>
