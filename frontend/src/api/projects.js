import request from './index'

export function getProjects() {
  return request.get('/projects')
}

export function createProject(data) {
  return request.post('/projects', data)
}

export function updateProject(id, data) {
  return request.put(`/projects/${id}`, data)
}

export function disableProject(id) {
  return request.patch(`/projects/${id}/disable`)
}

export function enableProject(id) {
  return request.patch(`/projects/${id}/enable`)
}

export function getProjectContacts(id) {
  return request.get(`/projects/${id}/contacts`)
}

export function getProjectWarranty(id) {
  return request.get(`/projects/${id}/warranty`)
}

export function uploadProjectWarrantyFile(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/projects/${id}/warranty/upload`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function createProjectWarranty(id, data) {
  return request.post(`/projects/${id}/warranty`, data)
}

export function updateProjectWarranty(id, warrantyId, data) {
  return request.put(`/projects/${id}/warranty/${warrantyId}`, data)
}
