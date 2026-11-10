import request from './request'

export function createTask(formData) {
  return request.post('/tasks', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function listTasks(params) {
  return request.get('/tasks', { params })
}

export function getTaskDetail(id, light = true) {
  return request.get(`/tasks/${id}`, { params: { light } })
}

/** 任务文档元数据懒加载，避免详情首屏携带完整段落/表格 JSON。 */
export function getTaskMetadata(id) {
  return request.get(`/tasks/${id}/metadata`)
}

/** 差异分页接口。 */
export function getTaskDiffs(id, params = {}) {
  return request.get(`/tasks/${id}/diffs`, { params: { page: 1, size: 200, ...params } })
}

export function getTaskDiffTypes(id) {
  return request.get(`/tasks/${id}/diff-types`)
}

export function getTaskProgress(id) {
  return request.get(`/tasks/${id}/progress`)
}

/** 原始 DOCX：返回 AxiosResponse，data 为 Blob，供 docx-preview 渲染。 */
export function getTaskDocument(id) {
  return request.get(`/tasks/${id}/document`, {
    responseType: 'blob',
    timeout: 120000,
    silent: true
  })
}

/** 受保护图片读取：返回 AxiosResponse，data 为 Blob。 */
export function getTaskImage(id, index) {
  return request.get(`/tasks/${id}/images/${index}`, {
    responseType: 'blob',
    timeout: 30000,
    // 历史任务可能还有无法定位的旧图片，失败时只降级为占位，不弹全局错误。
    silent: true
  })
}
