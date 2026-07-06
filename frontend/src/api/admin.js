import request from './index'

export function getAdminUsers() {
  return request.get('/admin/users')
}

export function createAdminUser(data) {
  return request.post('/admin/users', data)
}

export function updateAdminUser(id, data) {
  return request.put(`/admin/users/${id}`, data)
}

export function updateAdminUserStatus(id, status) {
  return request.patch(`/admin/users/${id}/status`, null, { params: { status } })
}

export function resetAdminUserPassword(id) {
  return request.post(`/admin/users/${id}/reset-password`)
}

export function getUserProjectIds(id) {
  return request.get(`/admin/users/${id}/projects`)
}

export function saveUserProjectIds(id, projectIds) {
  return request.put(`/admin/users/${id}/projects`, { projectIds })
}

export function getAdminRoles() {
  return request.get('/admin/roles')
}

export function updateAdminRole(id, data) {
  return request.put(`/admin/roles/${id}`, data)
}

export function getTempUsers() {
  return request.get('/admin/temp-users')
}

export function createTempUser(data) {
  return request.post('/admin/temp-users', data)
}

export function updateTempUser(id, data) {
  return request.put(`/admin/temp-users/${id}`, data)
}

export function disableTempUser(id) {
  return request.patch(`/admin/temp-users/${id}/disable`)
}
