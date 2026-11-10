<template>
  <div class="dc-page submit-page">
    <div class="dc-page-head">
      <div>
        <div class="dc-eyebrow">New Audit</div>
        <h2 class="dc-title">发起格式校验</h2>
        <p class="dc-subtitle">选择格式基准模板并上传待校验文档，系统将异步完成全维度比对。</p>
      </div>
      <div class="head-hint">
        <el-icon><Timer /></el-icon>
        平均耗时 10–30 秒
      </div>
    </div>

    <div class="submit-layout">
      <div class="form-column">
        <section class="dc-card form-card">
          <div class="step-head">
            <span class="step-no">01</span>
            <div>
              <h3>选择基准模板</h3>
              <p>以模板中的格式元数据作为比对基准</p>
            </div>
          </div>

          <div class="template-select-row">
            <el-select
              v-model="templateId"
              filterable
              clearable
              placeholder="搜索并选择模板"
              class="template-select"
              :loading="templatesLoading"
              @change="onTemplateChange"
            >
              <el-option
                v-for="tpl in templates"
                :key="tpl.id"
                :label="tpl.name"
                :value="tpl.id"
              >
                <div class="option-row">
                  <div class="option-main">
                    <el-icon><Document /></el-icon>
                    <span class="option-name">{{ tpl.name }}</span>
                  </div>
                  <span class="option-scope" :class="tpl.scope === 'PUBLIC' ? 'public' : ''">
                    {{ scopeLabel(tpl.scope) }}
                  </span>
                </div>
              </el-option>
            </el-select>
            <el-button class="manage-btn" @click="router.push('/templates')">
              <el-icon><Setting /></el-icon>
              管理模板
            </el-button>
          </div>

          <div v-if="selectedTemplate" class="selected-template">
            <div class="selected-file">
              <div class="selected-icon"><el-icon><Document /></el-icon><span>DOCX</span></div>
              <div class="selected-main">
                <div class="selected-name">{{ selectedTemplate.name }}</div>
                <div class="selected-sub">
                  {{ selectedTemplate.category || '通用' }} · {{ formatFileSize(selectedTemplate.fileSize) }}
                  <span class="sep">|</span>
                  {{ selectedTemplate.originalName }}
                </div>
              </div>
              <div class="parse-pill" :class="parseMeta(selectedTemplate.parseStatus).type">
                {{ parseMeta(selectedTemplate.parseStatus).label }}
              </div>
            </div>

            <div v-if="templateMeta" class="meta-summary">
              <div class="meta-cell">
                <b>{{ templateMeta.statistics?.paragraphCount || 0 }}</b>
                <span>段落</span>
              </div>
              <div class="meta-cell">
                <b>{{ templateMeta.statistics?.tableCount || 0 }}</b>
                <span>表格</span>
              </div>
              <div class="meta-cell">
                <b>{{ templateMeta.statistics?.imageCount || 0 }}</b>
                <span>图片</span>
              </div>
              <div class="meta-cell">
                <b>{{ templateMeta.statistics?.characterCount || 0 }}</b>
                <span>字符</span>
              </div>
              <div class="meta-cell wide">
                <b>{{ pageSetupText }}</b>
                <span>页面设置</span>
              </div>
            </div>
            <div v-else class="meta-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              正在读取模板格式指纹…
            </div>
          </div>

          <div v-else class="template-placeholder">
            <el-icon><Pointer /></el-icon>
            尚未选择模板，请从上方模板库中选择一个格式基准。
          </div>
        </section>

        <section class="dc-card form-card">
          <div class="step-head">
            <span class="step-no">02</span>
            <div>
              <h3>上传待校验文档</h3>
              <p>仅支持 .docx 格式，文件不超过 50 MB</p>
            </div>
          </div>
          <FileUploader v-model="targetFile" :max-size-mb="50" />
        </section>

        <section class="dc-card form-card">
          <div class="step-head">
            <span class="step-no">03</span>
            <div>
              <h3>补充校验规则 <span class="optional">可选</span></h3>
              <p>描述模板之外的额外格式要求，将一并提交给 DSH 模型</p>
            </div>
          </div>
          <el-input
            v-model="customRules"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            resize="none"
            placeholder="例如：正文必须使用宋体小四；一级标题段前 0.5 行；表格不得跨页断行。"
          />
          <div class="rules-hint">
            <el-icon><InfoFilled /></el-icon>
            自定义规则只影响本次校验，不会修改模板本身。
          </div>
        </section>
      </div>

      <aside class="side-column">
        <section class="dc-card summary-card">
          <div class="summary-title">
            <el-icon><Tickets /></el-icon>
            本次校验
          </div>

          <div class="summary-list">
            <div class="summary-item">
              <span>基准模板</span>
              <b :class="{ empty: !selectedTemplate }">{{ selectedTemplate?.name || '未选择' }}</b>
            </div>
            <div class="summary-item">
              <span>待校验文档</span>
              <b :class="{ empty: !targetFile }">{{ targetFile?.name || '未上传' }}</b>
            </div>
            <div class="summary-item">
              <span>自定义规则</span>
              <b :class="{ empty: !customRules }">{{ customRules ? `${customRules.length} 字` : '无' }}</b>
            </div>
            <div class="summary-item">
              <span>执行模式</span>
              <b class="mode-text"><i></i> DSH 智能比对</b>
            </div>
          </div>

          <el-button
            class="submit-btn"
            type="primary"
            size="large"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="submit"
          >
            {{ submitting ? '正在创建任务…' : '开始格式校验' }}
            <el-icon v-if="!submitting"><Right /></el-icon>
          </el-button>

          <div v-if="!canSubmit" class="submit-tip">
            {{ !selectedTemplate ? '请先选择一个基准模板' : '请上传待校验的 .docx 文档' }}
          </div>
        </section>

        <section class="dc-card dimension-card">
          <div class="dimension-head">
            <h4>校验维度</h4>
            <span>7 DIMENSIONS</span>
          </div>
          <div class="dimension-grid">
            <div v-for="dim in dimensions" :key="dim.label" class="dimension-item">
              <div class="dimension-icon"><el-icon><component :is="dim.icon" /></el-icon></div>
              <div>
                <div class="dimension-label">{{ dim.label }}</div>
                <div class="dimension-desc">{{ dim.desc }}</div>
              </div>
            </div>
          </div>
        </section>

        <div class="process-note">
          <div class="process-line">
            <span class="process-dot"></span>
            <div>
              <b>异步队列处理</b>
              <p>提交后可离开页面，任务会在后台继续执行，进度与结果自动保存。</p>
            </div>
          </div>
          <div class="process-line">
            <span class="process-dot teal"></span>
            <div>
              <b>字符级定位</b>
              <p>每条差异都会映射到段落或表格单元格，在预览中直接高亮。</p>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FileUploader from '@/components/FileUploader.vue'
import { listTemplates, getTemplateMetadata } from '@/api/template'
import { createTask } from '@/api/task'
import { formatFileSize, scopeLabel, parseStatusMeta } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const templates = ref([])
const templatesLoading = ref(false)
const templateId = ref(null)
const templateMeta = ref(null)
const targetFile = ref(null)
const customRules = ref('')
const submitting = ref(false)

const dimensions = [
  { label: '字符格式', desc: '字体 / 字号 / 加粗', icon: 'EditPen' },
  { label: '段落格式', desc: '对齐 / 缩进 / 行距', icon: 'Sort' },
  { label: '编号层级', desc: '多级编号 / 起始值', icon: 'List' },
  { label: '表格结构', desc: '列宽 / 边框 / 表头', icon: 'Grid' },
  { label: '图片版式', desc: '环绕 / 尺寸 / 位置', icon: 'Picture' },
  { label: '页面设置', desc: '纸张 / 页边距 / 分栏', icon: 'Document' },
  { label: '页眉页脚', desc: '内容 / 页码 / 距边界', icon: 'Postcard' }
]

const selectedTemplate = computed(() => templates.value.find(t => t.id === templateId.value) || null)

const pageSetupText = computed(() => {
  const setup = templateMeta.value?.sections?.[0]?.pageSetup
  if (!setup) return '—'
  const orientation = setup.orientation === 'landscape' ? '横向' : '纵向'
  return `${Math.round(setup.widthPt || 0)}×${Math.round(setup.heightPt || 0)}pt · ${orientation}`
})

const canSubmit = computed(() => Boolean(selectedTemplate.value && targetFile.value && !submitting.value))

function parseMeta(status) {
  return parseStatusMeta(status)
}

async function loadTemplates() {
  templatesLoading.value = true
  try {
    const data = await listTemplates({ page: 1, size: 200 })
    templates.value = data.records || []
    const queryId = Number(route.query.templateId)
    if (queryId && templates.value.some(t => t.id === queryId)) {
      templateId.value = queryId
      await loadMeta(queryId)
    }
  } catch (e) {
    templates.value = []
  } finally {
    templatesLoading.value = false
  }
}

async function loadMeta(id) {
  templateMeta.value = null
  if (!id) return
  try {
    templateMeta.value = await getTemplateMetadata(id)
  } catch (e) {
    templateMeta.value = null
  }
}

function onTemplateChange(id) {
  loadMeta(id)
}

async function submit() {
  if (submitting.value) return
  if (!selectedTemplate.value) {
    ElMessage.warning('请先选择基准模板')
    return
  }
  if (!targetFile.value) {
    ElMessage.warning('请上传待校验文档')
    return
  }
  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('file', targetFile.value)
    formData.append('templateId', templateId.value)
    if (customRules.value.trim()) {
      formData.append('customRules', customRules.value.trim())
    }
    const created = await createTask(formData)
    ElMessage.success('校验任务已创建，正在进入队列')
    router.push(`/tasks/${created.taskId}`)
  } catch (e) {
    // 请求拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(loadTemplates)
</script>

<style scoped>
.head-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 11px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid var(--dc-line);
  font-size: 11px;
  color: #8492a6;
}

.submit-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(320px, 0.78fr);
  gap: 18px;
  align-items: start;
}

.form-column { display: flex; flex-direction: column; gap: 15px; }

.form-card { padding: 22px; }

.step-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 17px;
}

.step-no {
  width: 27px;
  height: 27px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.02em;
  flex: 0 0 auto;
  margin-top: 1px;
}

.step-head h3 {
  margin: 0;
  font-size: 14.5px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: -0.01em;
}

.step-head p { margin: 4px 0 0; font-size: 11px; color: #94a3b8; }

.optional {
  margin-left: 5px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #94a3b8;
  font-size: 9px;
  font-weight: 500;
  vertical-align: 1px;
}

.template-select-row { display: flex; gap: 9px; }
.template-select { flex: 1; }

:deep(.template-select .el-select__wrapper) {
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  border-radius: 9px;
  min-height: 40px;
}

:deep(.template-select .el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1.5px #818cf8 inset;
}

.manage-btn { border-color: #e2e8f0; color: #64748b; }

.option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.option-main { display: flex; align-items: center; gap: 7px; min-width: 0; }
.option-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.option-scope { font-size: 9px; color: #94a3b8; background: #f1f5f9; padding: 1px 5px; border-radius: 4px; }
.option-scope.public { color: #0f766e; background: #ecfdf5; }

.selected-template {
  margin-top: 14px;
  border: 1px solid #e5eaf3;
  border-radius: 11px;
  overflow: hidden;
  background: #fbfcff;
}

.selected-file {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 14px;
}

.selected-icon {
  position: relative;
  width: 37px;
  height: 42px;
  border-radius: 8px;
  background: #eef2ff;
  color: #4f46e5;
  display: grid;
  place-items: center;
  font-size: 18px;
  flex: 0 0 auto;
}
.selected-icon span {
  position: absolute;
  bottom: 3px;
  font-size: 6px;
  font-weight: 800;
}

.selected-main { flex: 1; min-width: 0; }
.selected-name { font-size: 13px; font-weight: 650; color: #1e293b; }
.selected-sub {
  margin-top: 4px;
  font-size: 10px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-sub .sep { margin: 0 5px; color: #d5dce7; }

.parse-pill {
  padding: 3px 7px;
  border-radius: 5px;
  font-size: 9px;
  font-weight: 600;
  background: #f1f5f9;
  color: #64748b;
  white-space: nowrap;
}
.parse-pill.success { background: #ecfdf5; color: #0f766e; }
.parse-pill.danger { background: #fef2f2; color: #b91c1c; }

.meta-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border-top: 1px solid #edf1f8;
  background: #fff;
}

.meta-cell {
  padding: 11px 8px;
  text-align: center;
  border-right: 1px dashed #edf1f8;
}
.meta-cell:last-child { border-right: none; }
.meta-cell b { display: block; font-size: 13px; font-weight: 700; color: #334155; white-space: nowrap; }
.meta-cell span { display: block; margin-top: 2px; font-size: 9px; color: #a8b3c3; }
.meta-cell.wide b { font-size: 10px; margin-top: 2px; }

.meta-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 13px;
  border-top: 1px solid #edf1f8;
  background: #fff;
  font-size: 10.5px;
  color: #94a3b8;
}

.template-placeholder {
  margin-top: 13px;
  padding: 15px;
  border: 1px dashed #dfe5ef;
  border-radius: 9px;
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  color: #a0aec0;
  background: #fbfcfe;
}

.rules-hint {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
  font-size: 10px;
  color: #a0aec0;
}

:deep(.el-textarea__inner) { border-radius: 9px; font-size: 12px; line-height: 1.7; }

.side-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: 96px;
}

.summary-card { padding: 20px; }

.summary-title {
  display: flex;
  align-items: center;
  gap: 7px;
  padding-bottom: 14px;
  border-bottom: 1px solid #eff2f7;
  font-size: 13.5px;
  font-weight: 700;
  color: #1e293b;
}
.summary-title .el-icon { color: var(--dc-primary); }

.summary-list { padding: 5px 0 14px; }

.summary-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 8px 0;
}
.summary-item span { font-size: 11px; color: #94a3b8; flex: 0 0 auto; }
.summary-item b {
  font-size: 11.5px;
  font-weight: 550;
  color: #334155;
  text-align: right;
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.summary-item b.empty { color: #c1cad8; font-weight: 400; }

.mode-text { display: inline-flex; align-items: center; gap: 4px; color: #0f766e !important; }
.mode-text i { width: 5px; height: 5px; border-radius: 50%; background: #14b8a6; }

.submit-btn {
  width: 100%;
  height: 42px;
  border: none;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 650;
  background: linear-gradient(135deg, #5b5bd6, #6d5ce8);
  box-shadow: 0 8px 18px rgba(91, 91, 214, 0.22);
}
.submit-btn.is-disabled { background: #e8ecf4; color: #a5b1c2; box-shadow: none; }

.submit-tip { margin-top: 8px; text-align: center; font-size: 10px; color: #b0bac8; }

.dimension-card { padding: 18px 20px; }

.dimension-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.dimension-head h4 { margin: 0; font-size: 12.5px; font-weight: 700; color: #334155; }
.dimension-head span { font-size: 8px; letter-spacing: 0.12em; color: #b0bac8; }

.dimension-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
}

.dimension-item {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 8px;
  border-radius: 8px;
  background: #fafbfe;
  border: 1px solid #f1f4f9;
}

.dimension-icon {
  width: 23px;
  height: 23px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: #eef2ff;
  color: #6366f1;
  font-size: 12px;
  flex: 0 0 auto;
}

.dimension-label { font-size: 10.5px; font-weight: 600; color: #475569; }
.dimension-desc { margin-top: 2px; font-size: 8.5px; color: #a0aec0; white-space: nowrap; }

.process-note {
  padding: 4px 4px 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.process-line { display: flex; gap: 9px; }
.process-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #818cf8;
  margin-top: 4px;
  flex: 0 0 auto;
  box-shadow: 0 0 0 3px #eef2ff;
}
.process-dot.teal { background: #14b8a6; box-shadow: 0 0 0 3px #ecfdf5; }
.process-line b { font-size: 10.5px; color: #64748b; font-weight: 600; }
.process-line p { margin: 3px 0 0; font-size: 9.5px; line-height: 1.6; color: #a0aec0; }

@media (max-width: 1050px) {
  .submit-layout { grid-template-columns: 1fr; }
  .side-column { position: static; }
}

@media (max-width: 640px) {
  .meta-summary { grid-template-columns: repeat(3, 1fr); }
  .meta-cell:nth-child(n + 4) { border-top: 1px dashed #edf1f8; }
  .template-select-row { flex-direction: column; }
}
</style>
