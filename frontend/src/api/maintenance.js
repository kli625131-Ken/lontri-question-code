import request from './index'

export function getMaintenanceOverview(params) {
  return request.get('/maintenance/overview', { params })
}

export function getMaintenanceVisits(params) {
  return request.get('/maintenance/visits', { params })
}

export function createMaintenanceVisit(data) {
  return request.post('/maintenance/visits', data)
}

export function importYunweiMaintenance() {
  return request.post('/maintenance/import/yunwei')
}

export function getMaintenanceVisit(id) {
  return request.get(`/maintenance/visits/${id}`)
}

export function getMaintenanceVisitByFinding(findingId) {
  return request.get(`/maintenance/findings/${findingId}/visit`)
}

export function updateMaintenanceVisit(id, data) {
  return request.put(`/maintenance/visits/${id}`, data)
}

export function startMaintenanceVisit(id) {
  return request.post(`/maintenance/visits/${id}/start`)
}

export function closeMaintenanceVisit(id, data) {
  return request.post(`/maintenance/visits/${id}/close`, data)
}

export function createMaintenanceAssignment(id, data) {
  return request.post(`/maintenance/visits/${id}/assignments`, data)
}

export function updateMaintenanceAssignment(id, assignmentId, data) {
  return request.put(`/maintenance/visits/${id}/assignments/${assignmentId}`, data)
}

export function deleteMaintenanceAssignment(id, assignmentId) {
  return request.delete(`/maintenance/visits/${id}/assignments/${assignmentId}`)
}

export function createMaintenancePersonnel(id, data) {
  return request.post(`/maintenance/visits/${id}/personnel`, data)
}

export function updateMaintenancePersonnel(id, personnelId, data) {
  return request.put(`/maintenance/visits/${id}/personnel/${personnelId}`, data)
}

export function deleteMaintenancePersonnel(id, personnelId) {
  return request.delete(`/maintenance/visits/${id}/personnel/${personnelId}`)
}

export function createMaintenanceFinding(id, data) {
  return request.post(`/maintenance/visits/${id}/findings`, data)
}

export function updateMaintenanceFinding(id, findingId, data) {
  return request.put(`/maintenance/visits/${id}/findings/${findingId}`, data)
}

export function deleteMaintenanceFinding(id, findingId) {
  return request.delete(`/maintenance/visits/${id}/findings/${findingId}`)
}

export function createMaintenanceQuoteItem(id, data) {
  return request.post(`/maintenance/visits/${id}/quote-items`, data)
}

export function updateMaintenanceQuoteItem(id, quoteItemId, data) {
  return request.put(`/maintenance/visits/${id}/quote-items/${quoteItemId}`, data)
}

export function deleteMaintenanceQuoteItem(id, quoteItemId) {
  return request.delete(`/maintenance/visits/${id}/quote-items/${quoteItemId}`)
}

export function uploadMaintenanceFindingAttachment(findingId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/maintenance/findings/${findingId}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function exportMaintenanceReportExcel(id) {
  return request.get(`/maintenance/visits/${id}/report/export-excel`, { responseType: 'blob' })
}
