import request from './request'

export function listTemplates(params) {
  return request.get('/templates', { params })
}

export function uploadTemplate(formData) {
  return request.post('/templates', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function getTemplateMetadata(id, config = {}) {
  return request.get(`/templates/${id}/metadata`, config)
}

/** 模板原始 DOCX：用于 WPS 风格预览，不增加下载次数。 */
export function getTemplateDocument(id) {
  return request.get(`/templates/${id}/document`, {
    responseType: 'blob',
    timeout: 120000,
    silent: true
  })
}

/** 根据模板目录生成检索规则 YAML（附件下载，不修改服务端配置）。 */
export function getTemplateRulesYaml(id) {
  return request.get(`/templates/${id}/rules.yaml`, {
    responseType: 'blob',
    timeout: 60000,
    silent: true
  })
}

export function deleteTemplate(id) {
  return request.delete(`/templates/${id}`)
}

export async function downloadTemplate(id) {
  const response = await request.get(`/templates/${id}/download`, { responseType: 'blob' })
  return response
}

/** 模板图片预览，权限由后端按 PUBLIC/个人模板校验。 */
export function getTemplateImage(id, index) {
  return request.get(`/templates/${id}/images/${index}`, {
    responseType: 'blob',
    timeout: 30000,
    silent: true
  })
}
