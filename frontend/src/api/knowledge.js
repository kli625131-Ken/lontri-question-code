import request from './index'

export function getKnowledgeList(params) {
  return request.get('/knowledge', { params })
}

export function getKnowledgeDetail(id) {
  return request.get(`/knowledge/${id}`)
}

export function createKnowledgeFromIssue(issueId) {
  return request.post(`/knowledge/from-issue/${issueId}`)
}

export function syncClosedKnowledge() {
  return request.post('/knowledge/sync-closed')
}

export function uploadKnowledgeDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/knowledge/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function updateKnowledge(id, data) {
  return request.put(`/knowledge/${id}`, data)
}

export function publishKnowledge(id) {
  return request.patch(`/knowledge/${id}/publish`)
}

export function disableKnowledge(id) {
  return request.patch(`/knowledge/${id}/disable`)
}
