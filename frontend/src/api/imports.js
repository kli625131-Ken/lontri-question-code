import request from './index'

export function uploadExcel(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/imports/excel', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function getImportBatches() {
  return request.get('/imports')
}

export function getImportBatchDetail(id) {
  return request.get(`/imports/${id}`)
}

export function updateImportRow(batchId, rowId, data) {
  return request.put(`/imports/${batchId}/rows/${rowId}`, data)
}

export function commitImportBatch(id) {
  return request.post(`/imports/${id}/commit`)
}
