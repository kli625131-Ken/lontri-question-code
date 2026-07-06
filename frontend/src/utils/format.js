function pad(value) {
  return String(value).padStart(2, '0')
}

function toDate(value) {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDate(value) {
  const date = toDate(value)
  if (!date) return '-'
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function formatDateTime(value) {
  const date = toDate(value)
  if (!date) return '-'
  return `${formatDate(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function toDateTimeQuery(value, end = false) {
  const date = toDate(value)
  if (!date) return undefined
  return `${formatDate(date)} ${end ? '23:59:59' : '00:00:00'}`
}

export function statusMeta(status) {
  const map = {
    OPEN: { label: '待处理', type: 'danger', tone: 'danger' },
    IN_PROGRESS: { label: '处理中', type: 'primary', tone: 'primary' },
    PENDING_CONFIRM: { label: '待确认', type: 'warning', tone: 'warning' },
    SUSPENDED: { label: '已挂起', type: 'info', tone: 'default' },
    CLOSED: { label: '已关闭', type: 'success', tone: 'success' }
  }
  return map[status] || { label: status || '-', type: 'info', tone: 'default' }
}

export function closureMeta(status) {
  const map = {
    OPEN: { label: '未闭环', type: 'warning', tone: 'warning' },
    CLOSED: { label: '已闭环', type: 'success', tone: 'success' }
  }
  return map[status] || { label: status || '-', type: 'info', tone: 'default' }
}

export function batchStatusMeta(status) {
  const map = {
    PENDING_REVIEW: { label: '待确认', type: 'warning' },
    READY_TO_COMMIT: { label: '可提交', type: 'primary' },
    PARTIALLY_COMMITTED: { label: '部分入库', type: 'warning' },
    COMMITTED: { label: '已入库', type: 'success' },
    FAILED: { label: '导入失败', type: 'danger' }
  }
  return map[status] || { label: status || '-', type: 'info' }
}

export function reviewStatusMeta(status) {
  const map = {
    NEEDS_REVIEW: { label: '待确认', type: 'warning' },
    CONFIRMED: { label: '已确认', type: 'success' }
  }
  return map[status] || { label: status || '-', type: 'info' }
}

export function rowTypeMeta(type) {
  const map = {
    ISSUE: { label: '问题主单', type: 'primary' },
    CONTACT: { label: '联系人', type: 'info' },
    WARRANTY: { label: '合同质保', type: 'success' },
    CATEGORY: { label: '分类字典', type: 'warning' },
    SOLUTION: { label: '解决方案', type: 'success' }
  }
  return map[type] || { label: type || '-', type: 'info' }
}

export function sourceTypeMeta(type) {
  const map = {
    MANUAL: { label: '手工录入', type: 'success' },
    EXCEL: { label: 'Excel 导入', type: 'warning' }
  }
  return map[type] || { label: type || '-', type: 'info' }
}

export function projectLevelMeta(level) {
  const map = {
    CUSTOMER: { label: '客户', type: 'info' },
    PROJECT_GROUP: { label: '项目组', type: 'warning' },
    PROJECT: { label: '子项目', type: 'success' }
  }
  return map[level] || { label: level || '-', type: 'info' }
}

export function commitStatusMeta(status) {
  const map = {
    PENDING: { label: '未提交', type: 'info' },
    COMMITTED: { label: '已入库', type: 'success' },
    SKIPPED: { label: '已跳过', type: 'warning' }
  }
  return map[status] || { label: status || '-', type: 'info' }
}

export function yesNoLabel(value) {
  return Number(value) === 1 ? '已开启' : '未开启'
}

export function normalizeMultiline(value) {
  if (!value && value !== 0) return '-'
  return String(value).replace(/\s+/g, ' ').trim() || '-'
}

export function numberText(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}
