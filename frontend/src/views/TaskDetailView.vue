<template>
  <div class="detail-page">
    <div class="detail-topbar">
      <div class="topbar-left">
        <button class="back-btn" @click="router.push('/tasks')">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <div class="title-block">
          <div class="title-line">
            <h2>{{ task?.docName || '校验结果' }}</h2>
            <span v-if="task" class="status-pill" :class="task.status.toLowerCase()">
              <span class="dot"></span>{{ statusMeta(task.status).label }}
            </span>
          </div>
          <div class="sub-line" v-if="task">
            <span class="mono">{{ task.taskNo }}</span>
            <span class="sep">·</span>
            <span>基准模板：{{ task.templateName || '—' }}</span>
            <span class="sep">·</span>
            <span>{{ formatDateTime(task.createdAt) }}</span>
          </div>
        </div>
      </div>

      <div class="topbar-right">
        <el-button :loading="refreshing" class="soft-btn" @click="manualRefresh">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-button v-if="task?.templateId" class="soft-btn" @click="recheck">
          <el-icon><RefreshRight /></el-icon> 重新校验
        </el-button>
      </div>
    </div>

    <div v-if="loading && !task" class="loading-state">
      <el-icon class="is-loading spin"><Loading /></el-icon>
      <p>正在加载校验结果…</p>
    </div>

    <div v-else-if="!task" class="missing-state">
      <div class="missing-icon"><el-icon><DocumentDelete /></el-icon></div>
      <h3>任务不存在或无权访问</h3>
      <p>该校验任务可能已被删除，或不属于当前账号。</p>
      <el-button type="primary" @click="router.push('/tasks')">返回任务列表</el-button>
    </div>

    <template v-else>
      <div v-if="!isTerminal" class="running-layout">
        <div class="dc-card running-card">
          <div class="running-badge">
            <span class="pulse"></span>
            {{ task.status === 'QUEUED' ? 'WAITING IN QUEUE' : 'AUDIT IN PROGRESS' }}
          </div>
          <h3>{{ task.status === 'QUEUED' ? '任务已进入校验队列' : '正在智能比对格式' }}</h3>
          <p class="running-desc">
            {{ task.status === 'QUEUED'
              ? '工作进程将按提交顺序领取任务，通常只需等待数秒。'
              : '正在解析文档格式元数据并调用 DSH 模型执行全维度比对，请稍候。' }}
          </p>

          <ProgressPanel
            :status="task.status"
            :progress="task.progress || 0"
            :stage="task.stage"
            :error-message="task.errorMessage"
          />

          <div class="running-meta">
            <div class="rm-item">
              <span>任务编号</span>
              <b class="mono">{{ task.taskNo }}</b>
            </div>
            <div class="rm-item">
              <span>已用时</span>
              <b>{{ elapsedText }}</b>
            </div>
            <div class="rm-item">
              <span>提交时间</span>
              <b>{{ formatDateTime(task.createdAt) }}</b>
            </div>
          </div>

          <div class="running-tip">
            <el-icon><InfoFilled /></el-icon>
            任务在后台异步执行，你可以安全离开此页面，稍后从任务列表返回查看。
          </div>
        </div>

        <div class="dc-card running-side">
          <h4>校验维度</h4>
          <div class="dimension-list">
            <div v-for="(dim, idx) in dimensionStages" :key="dim" class="dim-line" :class="{ active: currentDim === idx, done: currentDim > idx }">
              <span class="dim-index">{{ idx + 1 }}</span>
              <span>{{ dim }}</span>
              <el-icon v-if="currentDim > idx" class="dim-check"><CircleCheckFilled /></el-icon>
              <span v-else-if="currentDim === idx" class="dim-loading"><i></i><i></i><i></i></span>
            </div>
          </div>
          <div class="side-note">
            差异结果生成后，将在右侧预览中按段落 / 表格单元格精确高亮。
          </div>
        </div>
      </div>

      <div v-else-if="task.status === 'FAILED' || task.status === 'DEAD'" class="failed-wrap">
        <div class="dc-card failed-card">
          <div class="failed-icon"><el-icon><CircleCloseFilled /></el-icon></div>
          <h3>{{ task.status === 'DEAD' ? '任务已终止' : '校验任务执行失败' }}</h3>
          <p class="failed-msg">{{ task.errorMessage || '任务执行过程中发生未知错误，请重新提交校验。' }}</p>
          <div class="failed-actions">
            <el-button type="primary" @click="recheck">使用相同模板重新校验</el-button>
            <el-button @click="router.push('/tasks')">返回任务列表</el-button>
          </div>
          <div class="failed-meta">
            <span>任务编号 {{ task.taskNo }}</span>
            <span v-if="task.startedAt">开始于 {{ formatDateTime(task.startedAt) }}</span>
            <span v-if="task.finishedAt">结束于 {{ formatDateTime(task.finishedAt) }}</span>
          </div>
        </div>
      </div>

      <div v-else class="result-wrap">
        <div class="stats-grid">
          <div class="result-stat total">
            <div class="rs-label">差异总数</div>
            <div class="rs-value">{{ task.totalDiffCount || 0 }}</div>
            <div class="rs-foot">处格式差异</div>
          </div>
          <div class="result-stat high">
            <div class="rs-label"><i></i>高危</div>
            <div class="rs-value">{{ task.highCount || 0 }}</div>
            <div class="rs-foot">需优先修正</div>
          </div>
          <div class="result-stat middle">
            <div class="rs-label"><i></i>中危</div>
            <div class="rs-value">{{ task.middleCount || 0 }}</div>
            <div class="rs-foot">建议修正</div>
          </div>
          <div class="result-stat low">
            <div class="rs-label"><i></i>低危</div>
            <div class="rs-value">{{ task.lowCount || 0 }}</div>
            <div class="rs-foot">规范提示</div>
          </div>
          <div class="result-stat time">
            <div class="rs-label">校验耗时</div>
            <div class="rs-value small">{{ formatDuration(task.durationMs) }}</div>
            <div class="rs-foot">{{ formatDateTime(task.finishedAt) }}</div>
          </div>
        </div>

        <div v-if="task.summary" class="summary-banner">
          <el-icon><MagicStick /></el-icon>
          <span>{{ task.summary }}</span>
        </div>

        <div v-if="diffTotal === 0 && !resultLoading" class="clean-card">
          <div class="clean-art">
            <el-icon><Select /></el-icon>
          </div>
          <h3>未发现格式差异</h3>
          <p>待校验文档与基准模板在当前校验维度下格式一致，无需修正。</p>
          <div class="clean-badges">
            <span>字符</span><span>段落</span><span>编号</span><span>表格</span><span>图片</span><span>页面</span><span>页眉页脚</span>
          </div>
        </div>

        <div v-else class="result-layout">
          <TaskDiffPanel
            ref="diffPanelRef"
            :diffs="diffs"
            :total="diffTotal"
            :task="task"
            :type-options="typeOptions"
            :active-diff="activeDiff"
            @select="diff => (activeDiff = diff)"
          />

          <section class="preview-panel">
            <DocumentPreview
              :document="task.document"
              :diffs="diffs"
              :active-diff="activeDiff"
              :task-id="task.id"
              :template-comments="templateComments"
              @select-diff="diff => selectDiff(diff, true)"
            />
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTaskDetail, getTaskDiffTypes, getTaskDiffs, getTaskMetadata, getTaskProgress } from '@/api/task'
import { getTemplateMetadata } from '@/api/template'
import { formatDateTime, formatDuration, statusMeta } from '@/utils/format'
import ProgressPanel from '@/components/ProgressPanel.vue'
import TaskDiffPanel from '@/components/TaskDiffPanel.vue'
import DocumentPreview from '@/components/DocumentPreview.vue'

const route = useRoute()
const router = useRouter()

const task = ref(null)
const diffs = ref([])
const diffTotal = ref(0)
const diffTypes = ref([])
const resultLoading = ref(false)
const templateComments = ref([])
const loading = ref(true)
const refreshing = ref(false)
const activeDiff = ref(null)
const diffPanelRef = ref(null)
const now = ref(Date.now())

let timer = null
let clockTimer = null
let alive = true
let polling = false
let loadedTemplateCommentKey = ''

const isTerminal = computed(() => ['SUCCESS', 'FAILED', 'DEAD'].includes(task.value?.status))

const typeOptions = computed(() => diffTypes.value.length
  ? diffTypes.value
  : [...new Set(diffs.value.map(d => d.diffType).filter(Boolean))])

const elapsedText = computed(() => {
  if (!task.value?.startedAt) return '等待开始'
  const start = new Date(task.value.startedAt).getTime()
  const end = task.value.finishedAt ? new Date(task.value.finishedAt).getTime() : now.value
  return formatDuration(Math.max(0, end - start))
})

const dimensionStages = ['字符与字体', '段落与缩进', '编号与层级', '表格结构', '图片与版式', '页面设置', '页眉页脚']
const currentDim = computed(() => {
  const progress = task.value?.progress || 0
  if (progress < 20) return 0
  return Math.min(6, Math.floor((progress - 20) / 12))
})

async function loadDetail(showLoading = false) {
  if (showLoading) loading.value = true
  try {
    const data = await getTaskDetail(route.params.id)
    if (!alive) return
    if (task.value?.id && task.value.id !== data.id) {
      templateComments.value = []
      loadedTemplateCommentKey = ''
    }
    task.value = data
    diffs.value = []
    diffTotal.value = 0
    diffTypes.value = []
    activeDiff.value = null
    if (task.value.status === 'SUCCESS') {
      await loadResult()
      if (task.value.templateId) {
        loadTemplateComments(task.value.templateId)
      }
    }
    if (!isTerminal.value) startPolling()
  } catch (e) {
    if (!task.value) task.value = null
  } finally {
    loading.value = false
  }
}

/** 元数据、差异拆分加载；差异先渲染首批，剩余页在后台追加。 */
async function loadResult() {
  resultLoading.value = true
  try {
    const taskId = task.value.id
    const [meta, first, types] = await Promise.all([
      getTaskMetadata(taskId),
      getTaskDiffs(taskId, { page: 1, size: 200 }),
      getTaskDiffTypes(taskId).catch(() => [])
    ])
    if (!alive || task.value?.id !== taskId) return
    task.value = { ...task.value, document: meta }
    diffs.value = first.records || []
    diffTotal.value = first.total || 0
    diffTypes.value = Array.isArray(types) ? types : []
    if (!activeDiff.value && diffs.value.length) activeDiff.value = diffs.value[0]
    await loadRemainingDiffs(taskId, first.page || 1, first.size || 200)
  } catch (e) {
    // 元数据/差异加载失败不覆盖任务基础信息，页面仍可显示失败原因。
  } finally {
    resultLoading.value = false
  }
}

async function loadRemainingDiffs(taskId, startPage, size) {
  let page = startPage + 1
  while (alive && task.value?.id === taskId && diffs.value.length < diffTotal.value && page <= 50) {
    const next = await getTaskDiffs(taskId, { page, size })
    if (!alive || task.value?.id !== taskId) return
    diffs.value = diffs.value.concat(next.records || [])
    if (!next.records?.length) break
    page += 1
  }
}

async function loadTemplateComments(templateId) {
  const key = String(templateId)
  if (loadedTemplateCommentKey === key) return
  loadedTemplateCommentKey = key
  try {
    const meta = await getTemplateMetadata(templateId, { silent: true })
    if (!alive || String(task.value?.templateId) !== key) return
    templateComments.value = Array.isArray(meta?.comments) ? meta.comments : []
  } catch (e) {
    // 模板被删除或无权访问时不影响任务结果，只隐藏“模板要求”标签。
    if (alive && String(task.value?.templateId) === key) templateComments.value = []
  }
}

function startPolling() {
  stopPolling()
  timer = setTimeout(poll, 2200)
}

async function poll() {
  if (!alive || polling) return
  if (isTerminal.value) return
  polling = true
  try {
    const p = await getTaskProgress(route.params.id)
    if (!alive) return
    if (task.value) {
      task.value = {
        ...task.value,
        status: p.status,
        progress: p.progress,
        stage: p.stage,
        errorMessage: p.errorMessage,
        summary: p.summary,
        totalDiffCount: p.totalDiffCount,
        highCount: p.highCount,
        middleCount: p.middleCount,
        lowCount: p.lowCount,
        durationMs: p.durationMs,
        startedAt: p.startedAt || task.value.startedAt,
        finishedAt: p.finishedAt || task.value.finishedAt
      }
    }
    if (['SUCCESS', 'FAILED', 'DEAD'].includes(p.status)) {
      stopPolling()
      await loadDetail(false)
    } else {
      startPolling()
    }
  } catch (e) {
    if (alive) startPolling()
  } finally {
    polling = false
  }
}

function stopPolling() {
  if (timer) clearTimeout(timer)
  timer = null
}

async function manualRefresh() {
  refreshing.value = true
  try {
    await loadDetail(false)
  } finally {
    refreshing.value = false
  }
}

function selectDiff(diff, fromPreview = false) {
  if (!diff) return
  activeDiff.value = diff
  if (fromPreview) nextTick(() => diffPanelRef.value?.focusDiff(diff))
}

function recheck() {
  router.push({ path: '/submit', query: { templateId: task.value?.templateId } })
}

onMounted(() => {
  loadDetail(true)
  clockTimer = setInterval(() => {
    if (task.value && !isTerminal.value) now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  alive = false
  stopPolling()
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
.detail-page {
  padding: 0 30px 28px;
  max-width: 1600px;
  margin: 0 auto;
}

.detail-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 0 16px;
  flex-wrap: wrap;
}

.topbar-left { display: flex; align-items: center; gap: 11px; min-width: 0; }

.back-btn {
  width: 31px;
  height: 31px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #64748b;
  display: grid;
  place-items: center;
  cursor: pointer;
  flex: 0 0 auto;
}
.back-btn:hover { color: var(--dc-primary); border-color: #c7d2fe; background: #f7f8ff; }

.title-line { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.title-line h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 730;
  letter-spacing: -0.02em;
  color: #0f172a;
  max-width: 540px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px;
  border-radius: 5px;
  font-size: 9.5px;
  font-weight: 600;
  white-space: nowrap;
}
.status-pill .dot { width: 4px; height: 4px; border-radius: 50%; background: currentColor; }
.status-pill.queued { background: #f1f5f9; color: #64748b; }
.status-pill.running { background: #fffbeb; color: #b45309; }
.status-pill.success { background: #ecfdf5; color: #0f766e; }
.status-pill.failed, .status-pill.dead { background: #fef2f2; color: #991b1b; }

.sub-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 5px;
  font-size: 10px;
  color: #94a3b8;
  flex-wrap: wrap;
}
.mono { font-family: "SFMono-Regular", Consolas, monospace; }
.sep { color: #d5dce7; }

.topbar-right { display: flex; gap: 7px; }
.soft-btn { border-color: #e2e8f0; color: #64748b; font-size: 11.5px; }
.soft-btn:hover { color: var(--dc-primary); border-color: #c7d2fe; background: #f8f9ff; }

.loading-state, .missing-state {
  min-height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
}
.spin { font-size: 28px; color: #818cf8; }
.loading-state p { margin-top: 12px; font-size: 12px; }

.missing-state { background: #fff; border: 1px solid var(--dc-line); border-radius: 14px; }
.missing-icon {
  width: 60px;
  height: 60px;
  border-radius: 17px;
  display: grid;
  place-items: center;
  background: #f1f5f9;
  color: #94a3b8;
  font-size: 27px;
}
.missing-state h3 { margin: 15px 0 5px; font-size: 15px; color: #475569; }
.missing-state p { margin: 0 0 17px; font-size: 11.5px; }

.running-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.6fr);
  gap: 16px;
  align-items: start;
}

.running-card { padding: 28px; }
.running-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 8.5px;
  font-weight: 750;
  letter-spacing: 0.13em;
  color: #d97706;
  background: #fffbeb;
  padding: 4px 8px;
  border-radius: 5px;
}
.pulse {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #f59e0b;
  animation: pulse 1.3s infinite;
}
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.2; } }

.running-card h3 { margin: 14px 0 0; font-size: 21px; letter-spacing: -0.02em; font-weight: 730; color: #0f172a; }
.running-desc { margin: 7px 0 20px; font-size: 12px; color: #8492a6; }

.running-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 20px;
  padding-top: 17px;
  border-top: 1px dashed #e8edf5;
}
.rm-item span { display: block; font-size: 9.5px; color: #a8b3c3; margin-bottom: 4px; }
.rm-item b { font-size: 10.5px; color: #526176; font-weight: 550; }

.running-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 18px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f9fd;
  font-size: 10px;
  line-height: 1.6;
  color: #94a3b8;
}

.running-side { padding: 20px; }
.running-side h4 { margin: 0 0 14px; font-size: 12.5px; color: #334155; font-weight: 700; }
.dimension-list { display: flex; flex-direction: column; gap: 4px; }
.dim-line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 7px;
  font-size: 10.5px;
  color: #94a3b8;
}
.dim-line.active { background: #eef2ff; color: #4338ca; font-weight: 600; }
.dim-line.done { color: #0f766e; }
.dim-index {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1px solid #e5eaf3;
  display: grid;
  place-items: center;
  font-size: 8px;
  color: #a0aec0;
}
.dim-line.active .dim-index { border-color: #c7d2fe; background: #fff; color: #4f46e5; }
.dim-line.done .dim-index { border-color: #a7f3d0; background: #ecfdf5; }
.dim-check { margin-left: auto; font-size: 13px; }
.dim-loading { margin-left: auto; display: flex; gap: 2px; }
.dim-loading i { width: 3px; height: 3px; border-radius: 50%; background: #818cf8; animation: dot 1s infinite; }
.dim-loading i:nth-child(2) { animation-delay: 0.15s; }
.dim-loading i:nth-child(3) { animation-delay: 0.3s; }
@keyframes dot { 0%, 100% { opacity: 0.2; } 50% { opacity: 1; } }

.side-note { margin-top: 16px; padding-top: 14px; border-top: 1px dashed #e8edf5; font-size: 9.5px; color: #a0aec0; line-height: 1.7; }

.failed-wrap { display: flex; justify-content: center; padding: 28px 0; }
.failed-card {
  width: min(600px, 100%);
  padding: 38px;
  text-align: center;
}
.failed-icon {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  margin: 0 auto;
  display: grid;
  place-items: center;
  background: #fef2f2;
  color: #dc2626;
  font-size: 29px;
}
.failed-card h3 { margin: 16px 0 7px; font-size: 18px; color: #1e293b; }
.failed-msg {
  margin: 0 auto;
  max-width: 440px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #fef7f7;
  border: 1px solid #fee2e2;
  color: #b91c1c;
  font-size: 11.5px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.failed-actions { display: flex; justify-content: center; gap: 8px; margin-top: 22px; }
.failed-meta { display: flex; justify-content: center; gap: 14px; flex-wrap: wrap; margin-top: 20px; padding-top: 15px; border-top: 1px dashed #eef2f7; font-size: 9.5px; color: #a8b3c3; }

.stats-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.85fr 0.85fr 0.85fr 1.15fr;
  gap: 11px;
  margin-bottom: 13px;
}

.result-stat {
  padding: 13px 15px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--dc-line);
  position: relative;
  overflow: hidden;
}
.result-stat.total { background: linear-gradient(135deg, #111a2e, #1b2744); border-color: #1b2744; }
.result-stat.total .rs-label, .result-stat.total .rs-foot { color: #94a3b8; }
.result-stat.total .rs-value { color: #fff; }

.rs-label { font-size: 9.5px; color: #94a3b8; display: flex; align-items: center; gap: 4px; }
.rs-label i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.result-stat.high .rs-label { color: #b91c1c; }
.result-stat.middle .rs-label { color: #b45309; }
.result-stat.low .rs-label { color: #64748b; }
.rs-value { margin-top: 5px; font-size: 25px; font-weight: 750; letter-spacing: -0.04em; color: #0f172a; line-height: 1.1; }
.rs-value.small { font-size: 16px; margin-top: 9px; }
.rs-foot { margin-top: 3px; font-size: 8.5px; color: #a8b3c3; }

.summary-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 13px;
  border-radius: 9px;
  background: #f5f3ff;
  border: 1px solid #ede9fe;
  color: #5b21b6;
  font-size: 11px;
  line-height: 1.6;
}
.summary-banner .el-icon { margin-top: 1px; color: #7c3aed; }

.result-layout {
  display: grid;
  grid-template-columns: 390px minmax(0, 1fr);
  gap: 13px;
  align-items: stretch;
  height: calc(100vh - 300px);
  min-height: 560px;
}

.preview-panel { min-width: 0; min-height: 0; }

.clean-card {
  padding: 54px 24px;
  text-align: center;
  background: #fff;
  border: 1px solid var(--dc-line);
  border-radius: 14px;
}
.clean-art {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  margin: 0 auto;
  display: grid;
  place-items: center;
  background: #ecfdf5;
  color: #0f9f8f;
  font-size: 32px;
  box-shadow: 0 0 0 9px #f5fffc;
}
.clean-card h3 { margin: 18px 0 6px; font-size: 18px; color: #1e293b; }
.clean-card p { margin: 0; font-size: 11.5px; color: #94a3b8; }
.clean-badges { display: flex; justify-content: center; gap: 5px; flex-wrap: wrap; margin-top: 17px; }
.clean-badges span { padding: 3px 8px; border-radius: 4px; background: #f4f7fb; color: #94a3b8; font-size: 9px; }

@media (max-width: 1200px) {
  .result-layout { grid-template-columns: 340px minmax(0, 1fr); }
  .stats-grid { grid-template-columns: repeat(3, 1fr); }
  .result-stat.time { grid-column: span 2; }
}

@media (max-width: 900px) {
  .detail-page { padding: 0 16px 24px; }
  .result-layout { grid-template-columns: 1fr; height: auto; }
  .diff-panel { max-height: 480px; }
  .preview-panel { height: 640px; }
  .running-layout { grid-template-columns: 1fr; }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .result-stat.time { grid-column: span 1; }
  .running-meta { grid-template-columns: 1fr; }
}
</style>
