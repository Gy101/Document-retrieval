<template>
  <div class="dc-page">
    <div class="dc-page-head">
      <div>
        <div class="dc-eyebrow">Template Library</div>
        <h2 class="dc-title">格式基准模板</h2>
        <p class="dc-subtitle">上传标准文档作为格式基准，系统会在服务端预解析并缓存完整格式元数据。</p>
      </div>
      <el-button type="primary" size="large" class="upload-btn" @click="openUpload">
        <el-icon><Plus /></el-icon>
        上传模板
      </el-button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon indigo"><el-icon><Files /></el-icon></div>
        <div>
          <div class="stat-value">{{ total }}</div>
          <div class="stat-label">模板总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon teal"><el-icon><OfficeBuilding /></el-icon></div>
        <div>
          <div class="stat-value">{{ publicCount }}</div>
          <div class="stat-label">公共模板</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon amber"><el-icon><User /></el-icon></div>
        <div>
          <div class="stat-value">{{ personalCount }}</div>
          <div class="stat-label">个人模板</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon slate"><el-icon><Download /></el-icon></div>
        <div>
          <div class="stat-value">{{ downloadTotal }}</div>
          <div class="stat-label">累计下载</div>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <div class="scope-tabs">
        <button
          v-for="tab in scopeTabs"
          :key="tab.value"
          class="scope-tab"
          :class="{ active: scope === tab.value }"
          @click="changeScope(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="filter-right">
        <el-input
          v-model="keyword"
          placeholder="搜索模板名称、描述或文件名"
          clearable
          class="search-input"
          :prefix-icon="Search"
          @keyup.enter="load(1)"
          @clear="load(1)"
        />
        <el-button class="search-btn" @click="load(1)">搜索</el-button>
        <el-button text class="refresh-btn" :loading="loading" @click="load()">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="list-area">
      <div v-if="!loading && items.length === 0" class="dc-card empty-card">
        <div class="empty-art"><el-icon><FolderOpened /></el-icon></div>
        <h3>还没有可用模板</h3>
        <p>上传一份格式规范的 Word 文档，作为后续校验的基准。</p>
        <el-button type="primary" @click="openUpload">
          <el-icon><Plus /></el-icon> 上传第一份模板
        </el-button>
      </div>

      <div v-else class="template-grid">
        <article v-for="item in items" :key="item.id" class="template-card">
          <div class="card-top">
            <div class="file-badge">
              <el-icon><Document /></el-icon>
              <span>DOCX</span>
            </div>
            <div class="top-tags">
              <span class="scope-badge" :class="item.scope === 'PUBLIC' ? 'public' : 'personal'">
                <el-icon><component :is="item.scope === 'PUBLIC' ? 'OfficeBuilding' : 'User'" /></el-icon>
                {{ scopeLabel(item.scope) }}
              </span>
              <span v-if="item.category" class="category-badge">{{ item.category }}</span>
            </div>
          </div>

          <div class="card-body">
            <h3 class="template-name" :title="item.name">{{ item.name }}</h3>
            <p class="template-desc" :title="item.description">
              {{ item.description || '未填写模板说明' }}
            </p>

            <div class="meta-grid">
              <div class="meta-item">
                <span>原始文件</span>
                <b :title="item.originalName">{{ item.originalName }}</b>
              </div>
              <div class="meta-item">
                <span>文件大小</span>
                <b>{{ formatFileSize(item.fileSize) }}</b>
              </div>
              <div class="meta-item">
                <span>下载次数</span>
                <b>{{ item.downloadCount || 0 }}</b>
              </div>
              <div class="meta-item">
                <span>上传时间</span>
                <b>{{ formatDateTime(item.createdAt) }}</b>
              </div>
            </div>
          </div>

          <div class="card-foot">
            <div class="parse-state" :class="parseMeta(item.parseStatus).type">
              <span class="state-dot"></span>
              {{ parseMeta(item.parseStatus).label }}
              <el-tooltip v-if="item.parseStatus === 'FAILED' && item.parseMessage" :content="item.parseMessage" placement="top">
                <el-icon class="info-icon"><InfoFilled /></el-icon>
              </el-tooltip>
            </div>
            <div class="card-actions">
              <el-tooltip content="预览模板（WPS 版式）" placement="top">
                <el-button text circle @click="openPreview(item)">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="根据模板生成检索规则 YAML" placement="top">
                <el-button text circle :loading="generatingRulesId === item.id" @click="generateRules(item)">
                  <el-icon><Tickets /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="下载模板" placement="top">
                <el-button text circle :loading="downloadingId === item.id" @click="download(item)">
                  <el-icon><Download /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="用此模板发起校验" placement="top">
                <el-button text circle @click="goCheck(item)">
                  <el-icon><Position /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="canDelete(item)" content="删除模板" placement="top">
                <el-button text circle class="danger-action" @click="remove(item)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </article>
      </div>

      <div v-if="total > pageSize" class="pager-wrap">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog
      v-model="uploadVisible"
      title="上传格式模板"
      width="560px"
      class="upload-dialog"
      :close-on-click-modal="false"
      @closed="resetUpload"
    >
      <div class="dialog-intro">
        上传后将立即解析字符、段落、表格、页面等格式信息，并作为校验基准。
      </div>
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-position="top">
        <el-form-item label="模板文件" prop="file">
          <FileUploader v-model="uploadForm.file" :disabled="uploading" @change="onFileChange" />
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="模板名称" prop="name">
            <el-input v-model="uploadForm.name" maxlength="120" show-word-limit placeholder="例如：机关公文格式模板" />
          </el-form-item>
          <el-form-item label="模板分类">
            <el-select v-model="uploadForm.category" placeholder="选择分类" style="width: 100%">
              <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="模板说明">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="2"
            maxlength="300"
            show-word-limit
            placeholder="简要说明模板的适用场景、依据标准等（可选）"
          />
        </el-form-item>

        <el-form-item label="可见范围">
          <el-radio-group v-model="uploadForm.scope" :disabled="uploading">
            <el-radio-button value="PERSONAL">仅自己可见</el-radio-button>
            <el-radio-button value="PUBLIC" :disabled="!auth.isAdmin">公共模板</el-radio-button>
          </el-radio-group>
          <div class="scope-hint">
            {{ auth.isAdmin ? '公共模板对所有用户可见；个人模板仅自己可用。' : '普通用户仅可创建个人模板，公共模板由管理员维护。' }}
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="uploading" @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">
          {{ uploading ? '正在上传并解析…' : '上传并解析' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      width="min(1180px, 94vw)"
      top="2vh"
      class="template-preview-dialog"
      destroy-on-close
      @closed="resetPreview"
    >
      <template #header>
        <div class="preview-dialog-head">
          <div class="preview-dialog-title">
            <span class="preview-doc-icon"><el-icon><Document /></el-icon></span>
            <div>
              <h3>{{ previewTemplate?.name || '模板预览' }}</h3>
              <p>
                {{ scopeLabel(previewTemplate?.scope) }}
                <span v-if="previewTemplate?.category"> · {{ previewTemplate.category }}</span>
                <span v-if="previewTemplate?.originalName"> · {{ previewTemplate.originalName }}</span>
                <span v-if="previewTemplate?.fileSize"> · {{ formatFileSize(previewTemplate.fileSize) }}</span>
              </p>
            </div>
          </div>
          <span class="preview-mode-hint">WPS 版式预览</span>
        </div>
      </template>

      <div v-loading="previewLoading" class="template-preview-body">
        <DocumentPreview
          v-if="previewDocument"
          :document="previewDocument"
          :template-id="previewTemplate?.id"
          :diffs="[]"
        />
        <el-empty v-else-if="!previewLoading" description="暂时无法加载模板预览" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import FileUploader from '@/components/FileUploader.vue'
import DocumentPreview from '@/components/DocumentPreview.vue'
import {
  listTemplates,
  uploadTemplate,
  deleteTemplate,
  downloadTemplate,
  getTemplateMetadata,
  getTemplateRulesYaml
} from '@/api/template'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, formatFileSize, scopeLabel, parseStatusMeta } from '@/utils/format'

const router = useRouter()
const auth = useAuthStore()

const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 9
const scope = ref('')
const keyword = ref('')
const loading = ref(false)
const downloadingId = ref(null)
const generatingRulesId = ref(null)
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadFormRef = ref(null)
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTemplate = ref(null)
const previewDocument = ref(null)

const categories = ['公文', '报告', '合同', '论文', '技术文档', '通用']

const scopeTabs = [
  { label: '全部模板', value: '' },
  { label: '公共模板', value: 'PUBLIC' },
  { label: '我的模板', value: 'PERSONAL' }
]

const uploadForm = reactive({
  file: null,
  name: '',
  description: '',
  category: '通用',
  scope: 'PERSONAL'
})

const uploadRules = {
  file: [{ required: true, message: '请选择要上传的 Word 文档', trigger: 'change' }],
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }]
}

const publicCount = computed(() => items.value.filter(i => i.scope === 'PUBLIC').length)
const personalCount = computed(() => items.value.filter(i => i.scope === 'PERSONAL').length)
const downloadTotal = computed(() => items.value.reduce((sum, i) => sum + (i.downloadCount || 0), 0))

function parseMeta(status) {
  return parseStatusMeta(status)
}

function canDelete(item) {
  return auth.isAdmin || item.ownerId === auth.user?.id
}

async function load(targetPage = page.value) {
  loading.value = true
  page.value = targetPage
  try {
    const data = await listTemplates({
      page: page.value,
      size: pageSize,
      scope: scope.value || undefined,
      keyword: keyword.value.trim() || undefined
    })
    items.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changeScope(value) {
  scope.value = value
  load(1)
}

function openUpload() {
  uploadVisible.value = true
}

function onFileChange(file) {
  if (file && !uploadForm.name.trim()) {
    uploadForm.name = file.name.replace(/\.docx$/i, '')
  }
}

function resetUpload() {
  uploadFormRef.value?.resetFields()
  uploadForm.file = null
  uploadForm.name = ''
  uploadForm.description = ''
  uploadForm.category = '通用'
  uploadForm.scope = 'PERSONAL'
}

async function submitUpload() {
  const valid = await uploadFormRef.value?.validate().catch(() => false)
  if (!valid || !uploadForm.file) return
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadForm.file)
    formData.append('name', uploadForm.name.trim())
    formData.append('description', uploadForm.description.trim())
    formData.append('category', uploadForm.category)
    formData.append('scope', uploadForm.scope)
    const created = await uploadTemplate(formData)
    if (created?.parseStatus === 'FAILED') {
      ElMessage.warning('模板已上传，但格式预解析失败，可在校验时自动重试')
    } else {
      ElMessage.success('模板上传并解析成功')
    }
    uploadVisible.value = false
    load(1)
  } catch (e) {
    // 请求拦截器已提示
  } finally {
    uploading.value = false
  }
}

function downloadFileName(headers, fallback) {
  const disposition = headers?.['content-disposition'] || headers?.['Content-Disposition'] || ''
  const utf8 = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8) {
    try {
      return decodeURIComponent(utf8[1])
    } catch (e) {
      // 回退到模板名称
    }
  }
  return fallback.replace(/[\\/:*?"<>|]/g, '_')
}

async function generateRules(item) {
  if (generatingRulesId.value) return
  generatingRulesId.value = item.id
  try {
    const response = await getTemplateRulesYaml(item.id)
    const blob = response.data instanceof Blob
      ? response.data
      : new Blob([response.data], { type: 'application/yaml;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = downloadFileName(
      response.headers,
      `${item.name || `template-${item.id}`}-检索规则.yml`
    )
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    ElMessage.success('检索规则 YAML 已生成')
  } catch (e) {
    ElMessage.error('生成规则失败，请稍后重试')
  } finally {
    generatingRulesId.value = null
  }
}

async function download(item) {
  if (downloadingId.value) return
  downloadingId.value = item.id
  try {
    const response = await downloadTemplate(item.id)
    const blob = response.data instanceof Blob ? response.data : new Blob([response.data])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = item.originalName || `${item.name}.docx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    item.downloadCount = (item.downloadCount || 0) + 1
  } catch (e) {
    // 请求拦截器已提示
  } finally {
    downloadingId.value = null
  }
}

async function openPreview(item) {
  previewTemplate.value = item
  previewDocument.value = null
  previewVisible.value = true
  previewLoading.value = true
  try {
    previewDocument.value = await getTemplateMetadata(item.id)
  } catch (e) {
    ElMessage.error('模板预览加载失败，请稍后重试')
  } finally {
    previewLoading.value = false
  }
}

function resetPreview() {
  previewTemplate.value = null
  previewDocument.value = null
  previewLoading.value = false
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(
      `确定删除模板「${item.name}」吗？删除后不可恢复，已创建的校验任务不受影响。`,
      '删除模板',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteTemplate(item.id)
    ElMessage.success('模板已删除')
    if (items.value.length === 1 && page.value > 1) {
      load(page.value - 1)
    } else {
      load()
    }
  } catch (e) {
    // 请求拦截器已提示
  }
}

function goCheck(item) {
  if (item.parseStatus === 'FAILED') {
    ElMessage.warning('该模板预解析失败，发起校验时会自动尝试重新解析')
  }
  router.push({ path: '/submit', query: { templateId: item.id } })
}

onMounted(() => load(1))
</script>

<style scoped>
.upload-btn {
  border: none;
  background: linear-gradient(135deg, #5b5bd6, #6d5ce8);
  box-shadow: 0 8px 20px rgba(91, 91, 214, 0.22);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 13px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 15px 17px;
  background: #fff;
  border: 1px solid var(--dc-line);
  border-radius: 12px;
  box-shadow: 0 3px 14px rgba(15, 23, 42, 0.03);
}

.stat-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 18px;
}
.stat-icon.indigo { background: #eef2ff; color: #4f46e5; }
.stat-icon.teal { background: #ecfdf5; color: #0f9f8f; }
.stat-icon.amber { background: #fffbeb; color: #d97706; }
.stat-icon.slate { background: #f1f5f9; color: #64748b; }

.stat-value { font-size: 21px; font-weight: 750; letter-spacing: -0.03em; color: #0f172a; line-height: 1.1; }
.stat-label { font-size: 10.5px; color: #94a3b8; margin-top: 3px; }

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 7px;
  margin-bottom: 18px;
  background: #fff;
  border: 1px solid var(--dc-line);
  border-radius: 12px;
  flex-wrap: wrap;
}

.scope-tabs { display: flex; gap: 2px; }

.scope-tab {
  border: none;
  background: transparent;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 12.5px;
  color: #64748b;
  cursor: pointer;
  transition: 0.16s ease;
}
.scope-tab:hover { background: #f6f8fc; color: #334155; }
.scope-tab.active {
  background: #eef2ff;
  color: #4338ca;
  font-weight: 650;
}

.filter-right { display: flex; align-items: center; gap: 7px; padding-right: 4px; }
.search-input { width: 260px; }
:deep(.search-input .el-input__wrapper) { box-shadow: none; background: #f7f9fc; border: 1px solid transparent; }
:deep(.search-input .el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #c7d2fe inset; border-color: #c7d2fe; background: #fff; }

.search-btn { border-color: #e2e8f0; color: #475569; }
.refresh-btn { color: #94a3b8; }

.list-area { min-height: 300px; position: relative; }

.template-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 15px;
}

.template-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--dc-line);
  border-radius: 14px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.035);
  transition: 0.2s ease;
  overflow: hidden;
}

.template-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.08);
  border-color: #d9e0ee;
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 15px 16px 0;
}

.file-badge {
  position: relative;
  width: 38px;
  height: 44px;
  border-radius: 8px;
  background: linear-gradient(160deg, #eef2ff, #e0e7ff);
  color: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  flex: 0 0 auto;
}

.file-badge span {
  position: absolute;
  bottom: 4px;
  font-size: 6px;
  font-weight: 800;
  letter-spacing: 0.05em;
}

.top-tags { display: flex; flex-direction: column; align-items: flex-end; gap: 5px; }

.scope-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 7px;
  border-radius: 5px;
  font-size: 9.5px;
  font-weight: 600;
}
.scope-badge.public { background: #ecfdf5; color: #0f766e; }
.scope-badge.personal { background: #f1f5f9; color: #64748b; }
.scope-badge .el-icon { font-size: 10px; }

.category-badge {
  padding: 2px 7px;
  border-radius: 5px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  color: #94a3b8;
  font-size: 9.5px;
}

.card-body { padding: 12px 16px 14px; flex: 1; }

.template-name {
  margin: 0;
  font-size: 14.5px;
  font-weight: 700;
  color: #172033;
  letter-spacing: -0.01em;
  line-height: 1.4;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

.template-desc {
  margin: 6px 0 13px;
  font-size: 11px;
  color: #8b99ad;
  line-height: 1.65;
  height: 36px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
  padding-top: 12px;
  border-top: 1px dashed #edf0f6;
}

.meta-item { min-width: 0; }
.meta-item span { display: block; font-size: 9.5px; color: #a8b3c3; margin-bottom: 2px; }
.meta-item b {
  display: block;
  font-size: 10.5px;
  font-weight: 550;
  color: #526176;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px 9px 16px;
  border-top: 1px solid #f0f3f8;
  background: #fbfcfe;
}

.parse-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  color: #64748b;
}
.state-dot { width: 5px; height: 5px; border-radius: 50%; background: #94a3b8; }
.parse-state.success { color: #0f766e; }
.parse-state.success .state-dot { background: #0f9f8f; }
.parse-state.danger { color: #b91c1c; }
.parse-state.danger .state-dot { background: #dc2626; }
.info-icon { color: #ef4444; cursor: help; font-size: 11px; }

.card-actions { display: flex; gap: 1px; }
.card-actions :deep(.el-button) { color: #8b99ad; font-size: 15px; }
.card-actions :deep(.el-button:hover) { color: var(--dc-primary); background: #eef2ff; }
.card-actions :deep(.danger-action:hover) { color: #dc2626; background: #fef2f2; }

.empty-card {
  padding: 56px 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-art {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  background: #eef2ff;
  color: #818cf8;
  font-size: 28px;
}
.empty-card h3 { margin: 16px 0 5px; font-size: 15px; color: #334155; }
.empty-card p { margin: 0 0 18px; font-size: 12px; color: #94a3b8; }

.pager-wrap { display: flex; justify-content: flex-end; padding-top: 22px; }

.dialog-intro {
  margin: -4px 0 18px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f9fd;
  color: #7c8aa0;
  font-size: 11.5px;
  line-height: 1.6;
}

.form-grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: 12px; }
.scope-hint { margin-top: 4px; font-size: 10.5px; color: #a0aec0; line-height: 1.5; }

:deep(.upload-dialog .el-dialog__header) { padding-bottom: 8px; }
:deep(.upload-dialog .el-dialog__title) { font-size: 17px; font-weight: 700; }
:deep(.upload-dialog .el-form-item__label) { font-size: 11.5px; font-weight: 600; color: #526176; }

.template-preview-dialog :deep(.el-dialog__header) {
  padding: 14px 18px 12px;
  margin-right: 0;
  border-bottom: 1px solid #eef2f7;
}
.template-preview-dialog :deep(.el-dialog__body) { padding: 12px 14px 14px; }
.preview-dialog-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.preview-dialog-title { display: flex; align-items: center; gap: 11px; min-width: 0; }
.preview-doc-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: #eef2ff;
  color: var(--dc-primary);
  font-size: 17px;
}
.preview-dialog-title h3 {
  margin: 0 0 3px;
  font-size: 15px;
  font-weight: 700;
  color: #26334a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-dialog-title p { margin: 0; font-size: 10.5px; color: #8b99ad; }
.preview-mode-hint {
  flex: 0 0 auto;
  padding: 4px 9px;
  border-radius: 20px;
  background: #f0f4ff;
  color: #5b6bb5;
  font-size: 9.5px;
  letter-spacing: 0.4px;
}
.template-preview-body {
  height: calc(92vh - 128px);
  min-height: 480px;
  overflow: hidden;
  border-radius: 10px;
  background: #f3f6fb;
}
.template-preview-body :deep(.preview-shell) { height: 100%; }

@media (max-width: 1200px) {
  .template-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 800px) {
  .stats-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .template-grid { grid-template-columns: 1fr; }
  .search-input { width: 190px; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
