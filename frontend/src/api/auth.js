import request from './request'

export function loginApi(payload) {
  return request.post('/auth/login', payload)
}

export function registerApi(payload) {
  return request.post('/auth/register', payload)
}

export function meApi() {
  return request.get('/auth/me')
}

export function profileApi() {
  return request.get('/auth/profile')
}

export function updateProfileApi(payload) {
  return request.put('/auth/profile', payload)
}

export function changePasswordApi(payload) {
  return request.put('/auth/password', payload)
}

export function logoutApi() {
  return request.post('/auth/logout')
}
