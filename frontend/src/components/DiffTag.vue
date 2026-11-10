<template>
  <span class="diff-tag" :class="[`sev-${severityKey}`, size]">
    <span class="dot"></span>
    <span>{{ text }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { severityMeta } from '@/utils/format'

const props = defineProps({
  severity: { type: String, default: 'middle' },
  label: { type: String, default: '' },
  size: { type: String, default: 'normal' }
})

const severityKey = computed(() => {
  const value = String(props.severity || '').toLowerCase()
  return ['high', 'middle', 'low'].includes(value) ? value : 'middle'
})

const text = computed(() => props.label || severityMeta(severityKey.value).label)
</script>

<style scoped>
.diff-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 650;
  white-space: nowrap;
  line-height: 1;
}

.diff-tag.small { height: 19px; padding: 0 6px; font-size: 10px; }

.dot { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }

.sev-high { color: #b91c1c; background: #fef2f2; border: 1px solid #fee2e2; }
.sev-middle { color: #b45309; background: #fffbeb; border: 1px solid #fef3c7; }
.sev-low { color: #475569; background: #f8fafc; border: 1px solid #e2e8f0; }
</style>
