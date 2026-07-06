import request from './index'

export function getIssueOverview(params) {
  return request.get('/issues/overview', { params })
}

export function getIssues(params) {
  return request.get('/issues', { params })
}

export function getIssueDetail(id) {
  return request.get(`/issues/${id}`)
}

export function createIssue(data) {
  return request.post('/issues', data)
}

export function updateIssue(id, data) {
  return request.put(`/issues/${id}`, data)
}

export function addIssueRecord(id, data) {
  return request.post(`/issues/${id}/records`, data)
}

export function closeIssue(id, data) {
  return request.post(`/issues/${id}/close`, data)
}

export function reopenIssue(id, data) {
  return request.post(`/issues/${id}/reopen`, data)
}

export function similarSearchIssues(params) {
  return request.get('/issues/similar-search', { params })
}

export function getIssueAttachments(id) {
  return request.get(`/issues/${id}/attachments`)
}

export function uploadIssueAttachment(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/issues/${id}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function deleteIssueAttachment(id, attachmentId) {
  return request.delete(`/issues/${id}/attachments/${attachmentId}`)
}
