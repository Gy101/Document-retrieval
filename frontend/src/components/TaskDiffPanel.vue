<template>
  <aside class="diff-panel">
    <div class="diff-panel-head">
      <div class="diff-title-row">
        <h3>差异明细</h3>
        <span class="diff-total">{{ filteredDiffs.length }} / {{ total }}</span>
      </div>
      <div class="severity-filter">
        <button
          v-for="f in severityFilters"
          :key="f.value"
          class="sev-btn"
          :class="[f.value, { active: severity === f.value }]"
          @click="severity = f.value"
        >
          {{ f.label }}
          <span>{{ f.count }}</span>
        </button>
      </div>
      <div class="diff-filter-row">
        <el-select v-model="typeFilter" size="small" class="type-select" placeholder="全部类型">
          <el-option label="全部类型" value="" />
          <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
        </el-select>
        <el-input
          v-model="keyword"
          size="small"
          placeholder="搜索差异内容"
          clearable
          :prefix-icon="Search"
        />
      </div>
    </div>

    <div class="diff-list">
      <article
        v-for="diff in filteredDiffs"
        :key="diff.id || diff.diffNo"
        class="diff-item"
        :class="[diff.severity, { active: activeDiff?.id === diff.id }]"
        :data-diff-id="diff.id"
        @click="choose(diff)"
      >
        <div class="di-head">
          <DiffTag :severity="diff.severity" />
          <span class="di-type">{{ diff.diffType }}</span>
          <span class="di-no">#{{ diff.seq || diff.id }}</span>
        </div>
        <div class="di-location">
          <el-icon><Aim /></el-icon>
          <span>{{ diff.location || diff.anchorPath || '文档正文' }}</span>
        </div>
        <p v-if="diff.contentSnippet" class="di-snippet">“{{ diff.contentSnippet }}”</p>
        <div class="di-compare">
          <div class="compare-cell expected">
            <span class="cc-label">模板</span>
            <b>{{ formatDiffValue(diff, diff.templateFormat) }}</b>
          </div>
          <el-icon class="compare-arrow"><Right /></el-icon>
          <div class="compare-cell actual">
            <span class="cc-label">实际</span>
            <b>{{ formatDiffValue(diff, diff.actualFormat) }}</b>
          </div>
        </div>
        <div v-if="diff.suggestion" class="di-suggestion">
          <el-icon><Opportunity /></el-icon>
          <span>{{ diff.suggestion }}</span>
        </div>
        <el-icon class="di-arrow"><ArrowRight /></el-icon>
      </article>

      <div v-if="filteredDiffs.length === 0" class="diff-empty">
        <el-icon><Filter /></el-icon>
        <p>没有符合筛选条件的差异</p>
        <button @click="resetFilters">清除筛选</button>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { Aim, ArrowRight, Filter, Opportunity, Right, Search } from '@element-plus/icons-vue'
import DiffTag from './DiffTag.vue'

const props = defineProps({
  diffs: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  task: { type: Object, default: null },
  typeOptions: { type: Array, default: () => [] },
  activeDiff: { type: Object, default: null }
})
const emit = defineEmits(['select'])

const severity = ref('ALL')
const typeFilter = ref('')
const keyword = ref('')

const severityFilters = computed(() => [
  { value: 'ALL', label: '全部', count: props.total },
  { value: 'high', label: '高危', count: props.task?.highCount || 0 },
  { value: 'middle', label: '中危', count: props.task?.middleCount || 0 },
  { value: 'low', label: '低危', count: props.task?.lowCount || 0 }
])

const filteredDiffs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return props.diffs.filter(d => {
    if (severity.value !== 'ALL' && d.severity !== severity.value) return false
    if (typeFilter.value && d.diffType !== typeFilter.value) return false
    if (kw) {
      const haystack = [d.diffType, d.location, d.contentSnippet, d.templateFormat, d.actualFormat, d.suggestion]
        .filter(Boolean).join(' ').toLowerCase()
      if (!haystack.includes(kw)) return false
    }
    return true
  })
})

function choose(diff) {
  emit('select', diff)
}

function resetFilters() {
  severity.value = 'ALL'
  typeFilter.value = ''
  keyword.value = ''
}

function focusDiff(diff) {
  if (!diff) return
  if (!filteredDiffs.value.some(item => item.id === diff.id)) resetFilters()
  emit('select', diff)
  nextTick(() => {
    const item = document.querySelector(`.diff-item[data-diff-id="${diff.id}"]`)
    item?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

function formatDiffValue(diff, value) {
  if (value === null || value === undefined || value === '') return '—'
  if ((diff.diffType === '字重' || /bold/i.test(diff.diffType || ''))
      && /^(true|false)$/i.test(String(value))) {
    return String(value).toLowerCase() === 'true' ? '加粗' : '未加粗'
  }
  return value
}

defineExpose({ focusDiff, resetFilters })
</script>

<style scoped>
.diff-panel { background: #fff; border: 1px solid var(--dc-line); border-radius: 13px; display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.diff-panel-head { padding: 14px 14px 10px; border-bottom: 1px solid #eef2f7; flex: 0 0 auto; }
.diff-title-row { display: flex; align-items: center; justify-content: space-between; }
.diff-title-row h3 { margin: 0; font-size: 13.5px; font-weight: 700; color: #1e293b; }
.diff-total { font-size: 9.5px; color: #94a3b8; background: #f4f6fb; padding: 2px 6px; border-radius: 4px; }
.severity-filter { display: flex; gap: 4px; margin: 11px 0 8px; }
.sev-btn { flex: 1; display: inline-flex; align-items: center; justify-content: center; gap: 4px; height: 28px; border: 1px solid #eef2f7; background: #fafbfe; border-radius: 6px; font-size: 10px; color: #8492a6; cursor: pointer; transition: .15s ease; }
.sev-btn span { min-width: 14px; padding: 0 3px; height: 14px; display: inline-grid; place-items: center; border-radius: 3px; background: #eef2f7; color: #94a3b8; font-size: 8px; }
.sev-btn.active { background: #eef2ff; border-color: #c7d2fe; color: #4338ca; font-weight: 600; }
.sev-btn.active span { background: #e0e7ff; color: #4338ca; }
.sev-btn.high.active { background: #fef2f2; border-color: #fecaca; color: #b91c1c; }
.sev-btn.high.active span { background: #fee2e2; color: #b91c1c; }
.sev-btn.middle.active { background: #fffbeb; border-color: #fde68a; color: #b45309; }
.sev-btn.middle.active span { background: #fef3c7; color: #b45309; }
.diff-filter-row { display: flex; gap: 6px; }
.type-select { width: 116px; flex: 0 0 auto; }
:deep(.type-select .el-select__wrapper), :deep(.diff-filter-row .el-input__wrapper) { box-shadow: 0 0 0 1px #edf0f6 inset; background: #fafbfe; border-radius: 6px; }
:deep(.diff-filter-row .el-input__inner) { font-size: 10.5px; }
.diff-list { flex: 1; overflow-y: auto; padding: 8px; }
.diff-item { position: relative; padding: 11px 11px 10px; margin-bottom: 6px; border: 1px solid #eef2f7; border-radius: 9px; background: #fff; cursor: pointer; transition: .16s ease; }
.diff-item:hover { border-color: #dce3f1; background: #fcfdff; }
.diff-item.active { border-color: #a5b4fc; background: #f7f8ff; box-shadow: 0 0 0 2px rgba(99, 102, 241, .08); }
.diff-item.active::before { content: ''; position: absolute; left: -1px; top: 10px; bottom: 10px; width: 2.5px; border-radius: 3px; background: #6366f1; }
.di-head { display: flex; align-items: center; gap: 6px; }
.di-type { font-size: 11.5px; font-weight: 650; color: #334155; }
.di-no { margin-left: auto; font-size: 8.5px; color: #b6c0ce; font-family: monospace; }
.di-location { display: flex; align-items: center; gap: 4px; margin-top: 6px; font-size: 9px; color: #94a3b8; }
.di-location .el-icon { font-size: 10px; }
.di-snippet { margin: 7px 0 0; font-size: 10.5px; line-height: 1.55; color: #64748b; background: #f8fafc; border-radius: 5px; padding: 5px 7px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.di-compare { display: flex; align-items: center; gap: 7px; margin-top: 8px; }
.compare-cell { flex: 1; min-width: 0; padding: 5px 7px; border-radius: 5px; }
.compare-cell.expected { background: #f4f7fb; }
.compare-cell.actual { background: #fff7f7; }
.cc-label { display: block; font-size: 8px; color: #a0aec0; margin-bottom: 2px; }
.compare-cell b { display: block; font-size: 9.5px; font-weight: 550; color: #475569; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.compare-cell.actual b { color: #b91c1c; }
.compare-arrow { color: #cbd5e1; font-size: 11px; flex: 0 0 auto; }
.di-suggestion { display: flex; gap: 4px; align-items: flex-start; margin-top: 7px; font-size: 9.5px; line-height: 1.5; color: #0f766e; }
.di-suggestion .el-icon { margin-top: 1px; flex: 0 0 auto; font-size: 10px; }
.di-arrow { position: absolute; right: 9px; bottom: 11px; font-size: 10px; color: #cbd5e1; opacity: 0; transition: .15s; }
.diff-item:hover .di-arrow, .diff-item.active .di-arrow { opacity: 1; color: #818cf8; }
.diff-empty { padding: 42px 20px; text-align: center; color: #a0aec0; }
.diff-empty .el-icon { font-size: 24px; color: #c3ccda; }
.diff-empty p { margin: 9px 0; font-size: 11px; }
.diff-empty button { border: none; background: transparent; color: var(--dc-primary); font-size: 10.5px; cursor: pointer; }
</style>
