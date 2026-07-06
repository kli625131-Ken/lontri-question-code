<template>
  <div class="page-shell" v-loading="loading">
    <div class="top-meta">
      <span>刷新时间：{{ nowText }}</span>
      <el-icon><Refresh /></el-icon>
    </div>

    <section class="import-top">
      <article class="section-card upload-card">
        <div class="section-title">1. 上传 Excel 文件</div>
        <el-upload ref="uploadRef" class="upload-panel" drag :auto-upload="false" :show-file-list="false" accept=".xlsx" @change="handleFileChange">
          <div class="upload-cloud"><el-icon><UploadFilled /></el-icon></div>
          <div class="upload-title">拖拽 Excel 文件到此处，或点击选择文件</div>
          <div class="upload-copy">仅支持 .xlsx 格式，单文件不超过 50MB</div>
          <el-button type="primary">选择文件</el-button>
        </el-upload>
        <div v-if="selectedBatch?.batch" class="file-success">
          <el-icon><Document /></el-icon>
          <span>{{ selectedBatch.batch.originalFileName }}</span>
          <strong>上传成功</strong>
        </div>
        <div class="template-hint">
          提示：请使用标准模板导入，
          <el-button link type="primary" @click="downloadTemplate">导入模板下载.csv</el-button>
        </div>
      </article>

      <article class="section-card batch-list-card">
        <div class="toolbar">
          <div class="section-title">2. 批次管理</div>
          <div>
            <el-button type="primary" @click="triggerUpload">
              <el-icon><Plus /></el-icon>
              新建导入
            </el-button>
            <el-button @click="loadBatches"><el-icon><Refresh /></el-icon></el-button>
          </div>
        </div>
        <el-table :data="batches" empty-text="暂无导入批次" @row-click="selectBatch">
          <el-table-column prop="id" label="批次号" min-width="140" />
          <el-table-column prop="originalFileName" label="文件名" min-width="210" show-overflow-tooltip />
          <el-table-column prop="createByName" label="上传人" width="100" />
          <el-table-column label="上传时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="行数统计" min-width="220">
            <template #default="{ row }">
              <span>总 {{ numberText(row.totalRows) }}</span>
              <span class="stat-gap">问题 {{ numberText(row.readyRows) }}</span>
              <span class="stat-gap">非问题 {{ numberText(row.reviewRows) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="batchStatusMeta(row.batchStatus).type">{{ batchStatusMeta(row.batchStatus).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="selectBatch(row)">查看</el-button>
              <el-button link type="primary" @click.stop="selectBatch(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>

    <section class="import-bottom">
      <article class="section-card detail-card">
        <div class="section-title">3. 批次详情</div>
        <template v-if="selectedBatch?.batch">
          <div class="batch-meta">
            <span>批次号：{{ selectedBatch.batch.id }}</span>
            <span>文件名：{{ selectedBatch.batch.originalFileName }}</span>
            <span>上传时间：{{ formatDateTime(selectedBatch.batch.createTime) }}</span>
          </div>

          <div class="row-toolbar">
            <el-radio-group v-model="rowFilter">
              <el-radio-button label="all">全部 ({{ numberText(selectedBatch.rows?.length || 0) }})</el-radio-button>
              <el-radio-button label="review">待确认</el-radio-button>
              <el-radio-button label="issue">仅问题主单</el-radio-button>
            </el-radio-group>
            <div class="row-tools">
              <el-button type="primary" @click="confirmSelectedRows">批量确认</el-button>
              <el-button @click="exportCurrentRows"><el-icon><Download /></el-icon>导出当前数据</el-button>
              <el-input v-model="rowKeyword" placeholder="搜索标题、问题编号、主数据编号等">
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
            </div>
          </div>

          <el-table :data="filteredRows" max-height="360" empty-text="暂无行数据" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="44" />
            <el-table-column label="序号" type="index" width="70" />
            <el-table-column prop="normalizedData.issueNo" label="问题编号" width="150">
              <template #default="{ row }">{{ row.normalizedData?.issueNo || row.normalizedData?.code || '-' }}</template>
            </el-table-column>
            <el-table-column label="标题" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ rowSummary(row) }}</template>
            </el-table-column>
            <el-table-column label="行类型" width="110">
              <template #default="{ row }"><el-tag :type="rowTypeMeta(row.rowType).type">{{ rowTypeMeta(row.rowType).label }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="sheetName" label="所属 Sheet" width="130" />
            <el-table-column label="数据状态" width="110">
              <template #default="{ row }"><el-tag :type="reviewStatusMeta(row.reviewStatus).type">{{ reviewStatusMeta(row.reviewStatus).label }}</el-tag></template>
            </el-table-column>
            <el-table-column label="提交状态" width="110">
              <template #default="{ row }"><el-tag :type="commitStatusMeta(row.commitStatus).type">{{ commitStatusMeta(row.commitStatus).label }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRowEditor(row)">编辑</el-button>
                <el-button link type="primary" @click="openRowEditor(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else description="先选择一个导入批次查看详情" />
      </article>

      <article class="section-card overview-card">
        <div class="section-title">4. 数据概览</div>
        <template v-if="selectedBatch?.batch">
          <div class="overview-metrics">
            <div v-for="item in overviewCards" :key="item.label" class="overview-metric">
              <el-icon :style="{ color: item.color }"><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
              <strong>{{ numberText(item.value) }} <small>行</small></strong>
              <em :class="item.up ? 'danger-text' : 'success-text'">{{ item.up ? '+' : '-' }}{{ item.rate }}</em>
            </div>
          </div>

          <div class="donut-grid">
            <div v-for="chart in donutCharts" :key="chart.title" class="donut-card">
              <div class="summary-title">{{ chart.title }}</div>
              <div class="donut" :style="{ background: chart.gradient }"></div>
              <div class="donut-legend">
                <span v-for="item in chart.items" :key="item.label">
                  <i :style="{ background: item.color }"></i>{{ item.label }} {{ item.value }}
                </span>
              </div>
            </div>
          </div>
        </template>
      </article>
    </section>

    <div class="submit-bar" v-if="selectedBatch?.batch">
      <span>温馨提示：数据确认后请及时提交入库，以便生成标准问题主单并纳入问题库。</span>
      <div>
        <el-button type="primary" @click="commitSelectedBatch">
          <el-icon><Promotion /></el-icon>
          提交入库
        </el-button>
      </div>
    </div>

    <el-dialog v-model="editorVisible" title="确认导入行" width="960px">
      <div v-if="editingRow" class="editor-grid">
        <section>
          <div class="section-title">标准化字段</div>
          <el-form label-position="top" class="editor-form">
            <el-form-item v-for="field in editorFields" :key="field.key" :label="field.label">
              <el-input v-model="editorModel[field.key]" :type="field.type === 'textarea' ? 'textarea' : 'text'" :rows="field.type === 'textarea' ? 3 : undefined" />
            </el-form-item>
            <el-form-item label="确认状态">
              <el-select v-model="editorReviewStatus">
                <el-option label="已确认" value="CONFIRMED" />
                <el-option label="仍需确认" value="NEEDS_REVIEW" />
              </el-select>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="editorReviewMessage" type="textarea" :rows="3" />
            </el-form-item>
          </el-form>
        </section>
        <section>
          <div class="section-title">原始行快照</div>
          <div class="raw-panel"><pre>{{ rawSnapshot }}</pre></div>
        </section>
      </div>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRow" @click="saveRow">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { commitImportBatch, getImportBatchDetail, getImportBatches, updateImportRow, uploadExcel } from '@/api/imports'
import { batchStatusMeta, commitStatusMeta, formatDateTime, normalizeMultiline, numberText, reviewStatusMeta, rowTypeMeta } from '@/utils/format'

const loading = ref(false)
const uploadRef = ref(null)
const batches = ref([])
const selectedBatch = ref(null)
const rowFilter = ref('all')
const rowKeyword = ref('')
const selectedRows = ref([])
const editorVisible = ref(false)
const editingRow = ref(null)
const editorModel = ref({})
const editorReviewStatus = ref('CONFIRMED')
const editorReviewMessage = ref('')
const savingRow = ref(false)

const labelMap = {
  projectName: '子项目',
  customerName: '客户',
  contractType: '合同类型',
  startAt: '开始日期',
  endAt: '结束日期',
  serviceScope: '服务范围',
  warrantyTerm: '质保/合同期限',
  contractSignedAt: '合同签订时间',
  acceptanceAt: '验收时间',
  expireAt: '到期时间',
  contactName: '联系人',
  contactInfo: '联系方式',
  responsibility: '负责事项',
  reporterName: '反馈人',
  categoryPath: '问题大类',
  buildingName: '建筑/楼栋',
  floorName: '楼层',
  areaName: '区域/房间',
  systemType: '系统/设备类型',
  devicePoint: '设备编号/点位',
  receivedAt: '收到反馈时间',
  foundAt: '收到反馈时间',
  itemTitle: '事项',
  description: '事项',
  impactScope: '影响范围',
  severity: '严重程度',
  priority: '优先级',
  ownerName: '处理人',
  latestProgress: '最新进展',
  causeCategory: '原因分类',
  customerFeedback: '客户反馈口径',
  reuseTags: '标签',
  completionStatus: '完成情况',
  notes: '备注'
}
const textAreaKeys = new Set(['itemTitle', 'description', 'impactScope', 'latestProgress', 'customerFeedback', 'notes'])
const hiddenEditorKeys = new Set(['projectGroup', 'projectCode'])

const nowText = computed(() => formatDateTime(new Date()))
const filteredRows = computed(() => {
  let rows = selectedBatch.value?.rows || []
  if (rowFilter.value === 'review') rows = rows.filter(row => row.reviewStatus === 'NEEDS_REVIEW')
  if (rowFilter.value === 'issue') rows = rows.filter(row => row.rowType === 'ISSUE')
  const keyword = rowKeyword.value.trim()
  if (keyword) {
    rows = rows.filter(row => JSON.stringify(row.normalizedData || {}).includes(keyword) || JSON.stringify(row.rawData || {}).includes(keyword))
  }
  return rows
})
const editorFields = computed(() =>
  Object.keys(editorModel.value || {})
    .filter(key => !hiddenEditorKeys.has(key))
    .map(key => ({ key, label: labelMap[key] || key, type: textAreaKeys.has(key) ? 'textarea' : 'input' }))
)
const rawSnapshot = computed(() => JSON.stringify(editingRow.value?.rawData || {}, null, 2))
const overviewCards = computed(() => {
  const batch = selectedBatch.value?.batch || {}
  return [
    { label: '总行数', value: batch.totalRows || 0, color: '#1677ff', icon: 'Document', rate: '12.3%', up: false },
    { label: '问题主单', value: batch.readyRows || 0, color: '#fa8c16', icon: 'Warning', rate: '5.6%', up: true },
    { label: '非问题行', value: batch.reviewRows || 0, color: '#1fa463', icon: 'Select', rate: '3.2%', up: false }
  ]
})
const donutCharts = computed(() => {
  const batch = selectedBatch.value?.batch || {}
  return [
    {
      title: 'Sheet 分布',
      gradient: 'conic-gradient(#1677ff 0 73%, #24c482 73% 90%, #a8b3c5 90% 100%)',
      items: [{ label: '问题清单', value: batch.readyRows || 0, color: '#1677ff' }, { label: '解决方案', value: batch.reviewRows || 0, color: '#24c482' }]
    },
    {
      title: '行类型分布',
      gradient: 'conic-gradient(#1677ff 0 74%, #24c482 74% 92%, #a8b3c5 92% 100%)',
      items: [{ label: '问题主单', value: batch.readyRows || 0, color: '#1677ff' }, { label: '非问题行', value: batch.reviewRows || 0, color: '#24c482' }]
    },
    {
      title: '清洗确认情况统计',
      gradient: 'conic-gradient(#1fa463 0 72%, #fa8c16 72% 100%)',
      items: [{ label: '已确认', value: batch.readyRows || 0, color: '#1fa463' }, { label: '待确认', value: batch.reviewRows || 0, color: '#fa8c16' }]
    }
  ]
})

async function loadBatches() {
  loading.value = true
  try {
    const res = await getImportBatches()
    batches.value = res.data || []
    if (!selectedBatch.value?.batch?.id && batches.value.length > 0) await selectBatch(batches.value[0])
  } finally {
    loading.value = false
  }
}

async function selectBatch(batch) {
  if (!batch?.id) return
  const res = await getImportBatchDetail(batch.id)
  selectedBatch.value = res.data
}

async function handleFileChange(file) {
  if (!file?.raw) return
  loading.value = true
  try {
    const res = await uploadExcel(file.raw)
    selectedBatch.value = res.data
    rowFilter.value = 'all'
    ElMessage.success('导入预览已生成')
    await loadBatches()
  } finally {
    loading.value = false
  }
}

function rowSummary(row) {
  const data = row.normalizedData || {}
  return normalizeMultiline(data.itemTitle || data.description || data.latestProgress || data.contactName || data.projectName || `${row.sheetName || ''} / ${row.rowNumber || ''}`)
}

function triggerUpload() {
  const input = uploadRef.value?.$el?.querySelector('input[type="file"]')
  input?.click()
}

function openRowEditor(row) {
  editingRow.value = row
  editorModel.value = { ...(row.normalizedData || {}) }
  editorReviewStatus.value = row.reviewStatus || 'CONFIRMED'
  editorReviewMessage.value = row.reviewMessage || ''
  editorVisible.value = true
}

function handleSelectionChange(rows) {
  selectedRows.value = rows || []
}

async function saveRow() {
  if (!editingRow.value || !selectedBatch.value?.batch?.id) return
  savingRow.value = true
  try {
    await updateImportRow(selectedBatch.value.batch.id, editingRow.value.id, {
      normalizedData: editorModel.value,
      reviewStatus: editorReviewStatus.value,
      reviewMessage: editorReviewMessage.value
    })
    ElMessage.success('导入行已更新')
    editorVisible.value = false
    await selectBatch(selectedBatch.value.batch)
    await loadBatches()
  } finally {
    savingRow.value = false
  }
}

async function confirmSelectedRows() {
  if (!selectedBatch.value?.batch?.id) return
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选需要确认的导入行')
    return
  }
  savingRow.value = true
  try {
    await Promise.all(selectedRows.value.map(row => updateImportRow(selectedBatch.value.batch.id, row.id, {
      normalizedData: row.normalizedData || {},
      reviewStatus: 'CONFIRMED',
      reviewMessage: row.reviewMessage || ''
    })))
    selectedRows.value = []
    ElMessage.success('已确认选中导入行')
    await selectBatch(selectedBatch.value.batch)
    await loadBatches()
  } finally {
    savingRow.value = false
  }
}

function downloadTemplate() {
  const headers = ['projectName', 'reporterName', 'receivedAt', 'itemTitle', 'description', 'priority', 'ownerName', 'categoryPath', 'buildingName', 'floorName', 'areaName', 'systemType', 'devicePoint', 'latestProgress', 'causeCategory', 'reuseTags']
  const labels = headers.map(key => labelMap[key] || key)
  downloadCsv('问题导入模板.csv', [labels, ['示例子项目', '张三', '2026-06-16 09:00:00', '示例问题标题', '示例问题现象', '中', '未分配', '待确认问题', '1号楼', '2F', '会议室', '未确认', '点位001', '', '原因待确认', '']])
}

function exportCurrentRows() {
  const rows = filteredRows.value
  if (!rows.length) {
    ElMessage.warning('暂无可导出的数据')
    return
  }
  const keys = Array.from(new Set(rows.flatMap(row => Object.keys(row.normalizedData || {}))))
  const header = ['批次行ID', 'Sheet', '行号', '行类型', '确认状态', '提交状态', ...keys.map(key => labelMap[key] || key)]
  const body = rows.map(row => [
    row.id,
    row.sheetName,
    row.rowNumber,
    rowTypeMeta(row.rowType).label,
    reviewStatusMeta(row.reviewStatus).label,
    commitStatusMeta(row.commitStatus).label,
    ...keys.map(key => row.normalizedData?.[key] ?? '')
  ])
  downloadCsv(`导入数据-${selectedBatch.value?.batch?.id || 'batch'}.csv`, [header, ...body])
}

function downloadCsv(fileName, rows) {
  const content = rows.map(row => row.map(value => `"${String(value ?? '').replace(/"/g, '""')}"`).join(',')).join('\r\n')
  const blob = new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

async function commitSelectedBatch() {
  if (!selectedBatch.value?.batch?.id) return
  loading.value = true
  try {
    const res = await commitImportBatch(selectedBatch.value.batch.id)
    selectedBatch.value = res.data
    ElMessage.success('批次已提交入库')
    await loadBatches()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadBatches().catch(() => {})
})
</script>

<style scoped>
.top-meta {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
}

.import-top,
.import-bottom {
  display: grid;
  grid-template-columns: minmax(320px, 0.62fr) minmax(0, 1.38fr);
  gap: 18px;
}

.import-bottom {
  grid-template-columns: minmax(0, 1.28fr) minmax(380px, 0.72fr);
}

.upload-panel {
  margin-top: 16px;
}

.upload-panel :deep(.el-upload-dragger) {
  width: 100%;
  min-height: 220px;
  display: grid;
  place-items: center;
  border-style: dashed;
  border-color: #b9d7ff;
  background: #f8fbff;
}

.upload-cloud {
  width: 62px;
  height: 62px;
  display: grid;
  place-items: center;
  margin: 0 auto 12px;
  border-radius: 50%;
  color: #fff;
  font-size: 30px;
  background: linear-gradient(135deg, #73b7ff, #1677ff);
}

.upload-title {
  font-weight: 800;
  color: var(--text-main);
}

.upload-copy,
.template-hint {
  margin: 10px 0;
  color: var(--text-muted);
  font-size: 13px;
}

.file-success {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: var(--success);
}

.batch-meta,
.row-toolbar,
.submit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.batch-meta {
  justify-content: flex-start;
  margin-top: 14px;
  color: var(--text-muted);
}

.row-toolbar {
  margin: 18px 0 14px;
}

.row-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.row-tools .el-input {
  width: 300px;
}

.stat-gap {
  margin-left: 12px;
}

.overview-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.overview-metric {
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
}

.overview-metric span,
.overview-metric em {
  display: block;
  margin-top: 8px;
  color: var(--text-muted);
  font-style: normal;
}

.overview-metric strong {
  display: block;
  margin-top: 10px;
  font-size: 26px;
}

.overview-metric small {
  font-size: 13px;
  color: var(--text-muted);
}

.donut-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.donut-card {
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
}

.summary-title {
  font-weight: 800;
}

.donut {
  width: 96px;
  height: 96px;
  margin: 16px auto;
  border-radius: 50%;
  position: relative;
}

.donut::after {
  content: '';
  position: absolute;
  inset: 24px;
  border-radius: 50%;
  background: #fff;
}

.donut-legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--text-muted);
  font-size: 13px;
}

.donut-legend i {
  width: 8px;
  height: 8px;
  display: inline-block;
  margin-right: 8px;
  border-radius: 50%;
}

.submit-bar {
  position: sticky;
  bottom: 0;
  z-index: 5;
  padding: 14px 18px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--shadow-card);
  color: var(--text-muted);
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 18px;
}

.editor-form {
  margin-top: 16px;
}

@media (max-width: 1280px) {
  .import-top,
  .import-bottom,
  .donut-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .overview-metrics,
  .editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
