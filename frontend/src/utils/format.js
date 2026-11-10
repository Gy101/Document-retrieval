export function formatDateTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

export function formatFileSize(bytes) {
  if (bytes === null || bytes === undefined) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

export function formatDuration(ms) {
  if (!ms && ms !== 0) return '—'
  if (ms < 1000) return `${ms} ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} s`
  return `${Math.floor(ms / 60000)} 分 ${Math.round((ms % 60000) / 1000)} 秒`
}

export const STATUS_META = {
  QUEUED: { label: '排队中', type: 'info' },
  RUNNING: { label: '处理中', type: 'warning' },
  SUCCESS: { label: '校验成功', type: 'success' },
  FAILED: { label: '校验失败', type: 'danger' },
  DEAD: { label: '已终止', type: 'danger' }
}

export const SEVERITY_META = {
  high: { label: '高危', type: 'danger' },
  middle: { label: '中危', type: 'warning' },
  low: { label: '低危', type: 'info' }
}

export function statusMeta(status) {
  return STATUS_META[status] || { label: status || '未知', type: 'info' }
}

export function severityMeta(severity) {
  return SEVERITY_META[severity] || { label: severity || '提示', type: 'info' }
}

export function scopeLabel(scope) {
  return scope === 'PUBLIC' ? '公共模板' : '个人模板'
}

export function parseStatusMeta(status) {
  if (status === 'SUCCESS') return { label: '解析完成', type: 'success' }
  if (status === 'FAILED') return { label: '解析失败', type: 'danger' }
  return { label: '解析中', type: 'info' }
}
