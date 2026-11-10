<template>
  <div class="progress-panel" :class="{ 'is-compact': compact }">
    <div class="progress-head">
      <div class="status-wrap">
        <span class="status-dot" :class="statusKey"></span>
        <div>
          <div class="status-title">{{ statusInfo.label }}</div>
          <div class="status-stage">{{ stage || defaultStage }}</div>
        </div>
      </div>
      <div class="percent" :class="statusKey">{{ safeProgress }}<small>%</small></div>
    </div>

    <div class="track">
      <div class="fill" :class="statusKey" :style="{ width: `${safeProgress}%` }"></div>
      <div class="shine" :class="statusKey" :style="{ width: `${safeProgress}%` }"></div>
    </div>

    <div v-if="!compact" class="steps">
      <div
        v-for="(step, index) in steps"
        :key="step"
        class="step"
        :class="{ done: index < currentStep, active: index === currentStep && statusKey === 'RUNNING' }"
      >
        <span class="step-dot">
          <el-icon v-if="index < currentStep"><Check /></el-icon>
          <span v-else>{{ index + 1 }}</span>
        </span>
        <span class="step-label">{{ step }}</span>
        <span v-if="index < steps.length - 1" class="step-line"></span>
      </div>
    </div>

    <el-alert
      v-if="(status === 'FAILED' || status === 'DEAD') && errorMessage"
      class="error-box"
      type="error"
      :closable="false"
      show-icon
      :title="errorMessage"
    />

    <div v-if="status === 'SUCCESS'" class="success-note">
      <el-icon><CircleCheckFilled /></el-icon>
      <span>{{ summary || '格式校验完成，差异明细已生成。' }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { statusMeta } from '@/utils/format'

const props = defineProps({
  status: { type: String, default: 'QUEUED' },
  progress: { type: Number, default: 0 },
  stage: { type: String, default: '' },
  errorMessage: { type: String, default: '' },
  summary: { type: String, default: '' },
  compact: { type: Boolean, default: false }
})

const steps = ['任务排队', '解析文档', '智能比对', '生成报告']

const statusKey = computed(() => {
  return ['QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'DEAD'].includes(props.status) ? props.status : 'QUEUED'
})

const statusInfo = computed(() => statusMeta(props.status))

const safeProgress = computed(() => {
  if (props.status === 'SUCCESS' || props.status === 'DEAD') return 100
  const value = Number(props.progress) || 0
  return Math.max(1, Math.min(99, value))
})

const currentStep = computed(() => {
  if (props.status === 'SUCCESS') return 4
  if (props.status === 'FAILED') return Math.min(3, Math.floor(safeProgress.value / 30))
  if (props.status === 'DEAD') return 5
  if (props.status === 'QUEUED') return 0
  if (safeProgress.value < 32) return 1
  if (safeProgress.value < 78) return 2
  return 3
})

const defaultStage = computed(() => {
  if (props.status === 'QUEUED') return '等待校验工作进程领取任务'
  if (props.status === 'SUCCESS') return '全部校验步骤已完成'
  if (props.status === 'FAILED') return '任务执行中断'
  if (props.status === 'DEAD') return '任务已终止，请查看失败原因'
  return '正在处理中'
})
</script>

<style scoped>
.progress-panel {
  padding: 20px;
  border: 1px solid var(--dc-line);
  border-radius: 14px;
  background: #fff;
}

.progress-panel.is-compact { padding: 15px 16px; }

.progress-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.status-wrap { display: flex; gap: 11px; align-items: flex-start; }

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 5px;
  background: #94a3b8;
  flex: 0 0 auto;
}

.status-dot.RUNNING { background: #d97706; box-shadow: 0 0 0 4px rgba(217, 119, 6, 0.14); animation: pulse 1.4s infinite; }
.status-dot.SUCCESS { background: #0f9f8f; box-shadow: 0 0 0 4px rgba(15, 159, 143, 0.12); }
.status-dot.FAILED, .status-dot.DEAD { background: #dc2626; box-shadow: 0 0 0 4px rgba(220, 38, 38, 0.12); }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

.status-title { font-size: 14px; font-weight: 700; color: #1e293b; }

.status-stage {
  margin-top: 4px;
  font-size: 12px;
  color: #7c8aa0;
  line-height: 1.5;
}

.percent {
  font-size: 26px;
  font-weight: 760;
  letter-spacing: -0.03em;
  color: var(--dc-primary);
  font-variant-numeric: tabular-nums;
}

.percent small { font-size: 12px; font-weight: 600; margin-left: 2px; }
.percent.SUCCESS { color: var(--dc-teal); }
.percent.FAILED, .percent.DEAD { color: var(--dc-danger); }

.track {
  position: relative;
  height: 7px;
  border-radius: 999px;
  background: #eef1f7;
  margin-top: 16px;
  overflow: hidden;
}

.fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #6366f1, #818cf8);
  transition: width 0.5s ease;
}

.fill.SUCCESS { background: linear-gradient(90deg, #0f9f8f, #2dd4bf); }
.fill.FAILED, .fill.DEAD { background: linear-gradient(90deg, #991b1b, #ef4444); }

.shine {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  animation: shine 1.8s infinite;
  opacity: 0;
}

.shine.RUNNING { opacity: 1; }

@keyframes shine {
  from { transform: translateX(-100%); }
  to { transform: translateX(300%); }
}

.steps {
  display: flex;
  align-items: center;
  margin-top: 20px;
}

.step {
  display: flex;
  align-items: center;
  gap: 7px;
  flex: 1;
  min-width: 0;
  color: #a0aec0;
}

.step:last-child { flex: 0 0 auto; }

.step-dot {
  width: 21px;
  height: 21px;
  border-radius: 50%;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  display: grid;
  place-items: center;
  font-size: 9px;
  font-weight: 700;
  flex: 0 0 auto;
}

.step.done .step-dot { background: #ecfdf5; border-color: #a7f3d0; color: #0f9f8f; }
.step.active .step-dot { background: #eef2ff; border-color: #c7d2fe; color: #4f46e5; }

.step-label { font-size: 11.5px; white-space: nowrap; }
.step.done .step-label { color: #64748b; }
.step.active .step-label { color: #4338ca; font-weight: 600; }

.step-line {
  flex: 1;
  height: 1px;
  background: #e8edf5;
  margin: 0 8px;
}

.error-box { margin-top: 16px; }

.success-note {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 14px;
  font-size: 12px;
  color: #0f766e;
}

.success-note .el-icon { font-size: 15px; }

@media (max-width: 640px) {
  .steps { display: none; }
}
</style>
