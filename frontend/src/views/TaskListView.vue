<template>
  <div class="dc-page">
    <div class="dc-page-head">
      <div>
        <div class="dc-eyebrow">Audit History</div>
        <h2 class="dc-title">校验任务</h2>
        <p class="dc-subtitle">查看历史校验进度与结果，任务异步执行，可随时离开页面。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" class="refresh-action" @click="load(page, false)">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button type="primary" class="new-action" @click="router.push('/submit')">
          <el-icon><Plus /></el-icon>
          新建校验
        </el-button>
      </div>
    </div>

    <div class="status-bar">
      <button
        v-for="tab in statusTabs"
        :key="tab.value"
        class="status-tab"
        :class="{ active: status === tab.value }"
        @click="changeStatus(tab.value)"
      >
        <span class="tab-dot" :class="tab.value || 'all'"></span>
        {{ tab.label }}
      </button>
      <div class="status-live" v-if="hasRunning">
        <span class="live-dot"></span>
        有任务正在执行，列表自动刷新
      </div>
    </div>

    <div class="dc-card table-card">
      <el-table
        v-loading="loading"
        :data="items"
        class="task-table"
        row-key="id"
        @row-click="openDetail"
      >
        <el-table-column label="任务编号" min-width="170">
          <template #default="{ row }">
            <div class="task-no">
              <span class="task-id">{{ row.taskNo || `#${row.id}` }}</span>
              <span class="task-time">{{ formatDateTime(row.createdAt) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="模板 / 文档" min-width="260">
          <template #default="{ row }">
            <div class="doc-cell">
              <div class="doc-icon"><el-icon><Document /></el-icon></div>
              <div class="doc-text">
                <div class="doc-name" :title="row.docName">{{ row.docName }}</div>
                <div class="doc-tpl" :title="row.templateName">
                  基准：{{ row.templateName || '—' }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="status-pill" :class="row.status.toLowerCase()">
                <span class="dot"></span>
                {{ statusMeta(row.status).label }}
              </span>
              <div v-if="row.status === 'RUNNING'" class="mini-progress">
                <span :style="{ width: `${row.progress || 0}%` }"></span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="差异分布" width="190">
          <template #default="{ row }">
            <div v-if="row.status === 'SUCCESS'" class="diff-counts">
              <span class="dc-total">{{ row.totalDiffCount || 0 }}</span>
              <span class="dc-high" title="高危">{{ row.highCount || 0 }}</span>
              <span class="dc-middle" title="中危">{{ row.middleCount || 0 }}</span>
              <span class="dc-low" title="低危">{{ row.lowCount || 0 }}</span>
            </div>
            <span v-else-if="row.status === 'FAILED'" class="fail-text">执行失败</span>
            <span v-else class="dc-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            <span class="duration">{{ formatDuration(row.durationMs) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="" width="105" align="right">
          <template #default="{ row }">
            <el-button text class="detail-link" @click.stop="openDetail(row)">
              查看结果
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty">
            <div class="empty-icon"><el-icon><DataLine /></el-icon></div>
            <h3>暂无校验任务</h3>
            <p>选择模板并上传文档，创建你的第一次格式校验。</p>
            <el-button type="primary" @click="router.push('/submit')">新建校验</el-button>
          </div>
        </template>
      </el-table>

      <div v-if="total > pageSize" class="pager-wrap">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listTasks } from '@/api/task'
import { formatDateTime, formatDuration, statusMeta } from '@/utils/format'

const router = useRouter()
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const status = ref('')
const loading = ref(false)

let timer = null
let alive = true

const statusTabs = [
  { label: '全部', value: '' },
  { label: '排队中', value: 'QUEUED' },
  { label: '处理中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' }
]

const hasRunning = computed(() => items.value.some(i => i.status === 'QUEUED' || i.status === 'RUNNING'))

async function load(targetPage = page.value, silent = false) {
  if (!silent) loading.value = true
  page.value = targetPage
  try {
    const data = await listTasks({
      page: page.value,
      size: pageSize,
      status: status.value || undefined
    })
    if (!alive) return
    items.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
    schedule()
  }
}

function schedule() {
  if (timer) clearTimeout(timer)
  if (!alive) return
  if (hasRunning.value) {
    timer = setTimeout(() => load(page.value, true), 4000)
  }
}

function changeStatus(value) {
  status.value = value
  load(1)
}

function onPageChange(value) {
  load(value)
}

function openDetail(row) {
  router.push(`/tasks/${row.id}`)
}

onMounted(() => load(1))
onUnmounted(() => {
  alive = false
  if (timer) clearTimeout(timer)
})
</script>

<style scoped>
.head-actions { display: flex; gap: 8px; }
.refresh-action { border-color: #e2e8f0; color: #64748b; }
.new-action {
  border: none;
  background: linear-gradient(135deg, #5b5bd6, #6d5ce8);
  box-shadow: 0 7px 18px rgba(91, 91, 214, 0.22);
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 6px;
  margin-bottom: 14px;
  background: #fff;
  border: 1px solid var(--dc-line);
  border-radius: 11px;
  flex-wrap: wrap;
}

.status-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 13px;
  border: none;
  background: transparent;
  border-radius: 7px;
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
  transition: 0.16s ease;
}
.status-tab:hover { background: #f7f9fc; color: #334155; }
.status-tab.active { background: #eef2ff; color: #4338ca; font-weight: 650; }

.tab-dot { width: 5px; height: 5px; border-radius: 50%; background: #cbd5e1; }
.tab-dot.QUEUED { background: #94a3b8; }
.tab-dot.RUNNING { background: #d97706; }
.tab-dot.SUCCESS { background: #0f9f8f; }
.tab-dot.FAILED { background: #dc2626; }
.status-tab.active .tab-dot { box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.16); }

.status-live {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-right: 10px;
  font-size: 10px;
  color: #94a3b8;
}
.live-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #f59e0b;
  animation: blink 1.2s infinite;
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.25; } }

.table-card { overflow: hidden; }

:deep(.task-table) { --el-table-border-color: #f0f3f8; --el-table-header-bg-color: #fafbfe; }
:deep(.task-table th.el-table__cell) {
  padding: 11px 0;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #94a3b8;
  background: #fafbfe;
}
:deep(.task-table td.el-table__cell) { padding: 13px 0; border-bottom: 1px solid #f2f5f9; }
:deep(.task-table .el-table__row) { cursor: pointer; }
:deep(.task-table .el-table__row:hover > td.el-table__cell) { background: #fafbff; }

.task-no { display: flex; flex-direction: column; gap: 4px; }
.task-id { font-size: 11.5px; font-weight: 650; color: #334155; font-family: "SFMono-Regular", Consolas, monospace; }
.task-time { font-size: 9.5px; color: #a8b3c3; }

.doc-cell { display: flex; align-items: center; gap: 9px; min-width: 0; }
.doc-icon {
  width: 30px;
  height: 34px;
  border-radius: 7px;
  background: #eef2ff;
  color: #6366f1;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex: 0 0 auto;
}
.doc-text { min-width: 0; }
.doc-name {
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 250px;
}
.doc-tpl {
  margin-top: 3px;
  font-size: 9.5px;
  color: #9aa7ba;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 250px;
}

.status-cell { display: flex; flex-direction: column; gap: 5px; align-items: flex-start; }
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 7px;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 600;
  background: #f1f5f9;
  color: #64748b;
}
.status-pill .dot { width: 4px; height: 4px; border-radius: 50%; background: currentColor; }
.status-pill.queued { background: #f1f5f9; color: #64748b; }
.status-pill.running { background: #fffbeb; color: #b45309; }
.status-pill.success { background: #ecfdf5; color: #0f766e; }
.status-pill.failed { background: #fef2f2; color: #b91c1c; }

.mini-progress {
  width: 72px;
  height: 3px;
  border-radius: 99px;
  background: #eef2f7;
  overflow: hidden;
}
.mini-progress span { display: block; height: 100%; background: #f59e0b; border-radius: 99px; transition: width 0.5s; }

.diff-counts { display: flex; align-items: center; gap: 5px; }
.diff-counts span {
  min-width: 22px;
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 9.5px;
  text-align: center;
  font-weight: 600;
}
.dc-total { background: #eef2ff; color: #4338ca; }
.dc-high { background: #fef2f2; color: #b91c1c; }
.dc-middle { background: #fffbeb; color: #b45309; }
.dc-low { background: #f8fafc; color: #64748b; }

.fail-text { font-size: 10px; color: #dc2626; }
.duration { font-size: 10.5px; color: #8492a6; }

.detail-link { font-size: 10.5px; color: #6366f1; padding: 4px; }
.detail-link:hover { color: #4338ca; background: #eef2ff; }
.detail-link .el-icon { font-size: 11px; margin-left: 2px; }

.table-empty { padding: 44px 20px; }
.empty-icon {
  width: 54px;
  height: 54px;
  margin: 0 auto 13px;
  border-radius: 15px;
  display: grid;
  place-items: center;
  background: #eef2ff;
  color: #818cf8;
  font-size: 24px;
}
.table-empty h3 { margin: 0 0 5px; font-size: 14px; color: #475569; }
.table-empty p { margin: 0 0 16px; font-size: 11px; color: #a0aec0; }

.pager-wrap { display: flex; justify-content: flex-end; padding: 16px 18px; border-top: 1px solid #f3f5f9; }

@media (max-width: 760px) {
  .status-live { display: none; }
  :deep(.task-table .el-table__cell:nth-child(4)),
  :deep(.task-table .el-table__cell:nth-child(5)) { display: none; }
}
</style>
