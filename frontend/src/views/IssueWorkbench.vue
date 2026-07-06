<template>
  <div class="page-shell">
    <div class="top-meta">
      <span>数据更新时间：{{ nowText }}</span>
      <el-icon><Refresh /></el-icon>
    </div>

    <section class="section-card filter-card">
      <div class="filter-card-head">
        <div>
          <div class="section-title">问题筛选</div>
          <p class="section-hint">常用条件保持可见，低频条件收纳到高级筛选，减少首屏干扰。</p>
        </div>
        <el-button link type="primary" @click="showAdvancedFilters = !showAdvancedFilters">
          {{ showAdvancedFilters ? '收起高级筛选' : '展开高级筛选' }}
          <el-icon><ArrowDown /></el-icon>
        </el-button>
      </div>
      <el-form label-position="top" class="filter-grid">
        <el-form-item label="子项目">
          <el-select v-model="filters.projectId" clearable placeholder="请选择子项目">
            <el-option v-for="project in selectableProjects" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.currentStatus" clearable placeholder="请选择状态">
            <el-option label="待处理" value="OPEN" />
            <el-option label="处理中" value="IN_PROGRESS" />
            <el-option label="待确认" value="PENDING_CONFIRM" />
            <el-option label="已挂起" value="SUSPENDED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="filters.priority" clearable placeholder="请选择优先级">
            <el-option v-for="item in priorityOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人">
          <el-input v-model="filters.ownerName" placeholder="请选择处理人" />
        </el-form-item>
        <el-form-item label="收到反馈时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="→"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item label="关键词" class="wide-item">
          <el-input v-model="filters.keyword" placeholder="搜索问题编号、标题、反馈人等" @keyup.enter="loadIssues">
            <template #suffix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-form-item>
        <template v-if="showAdvancedFilters">
        <el-form-item label="分类关键词">
          <el-input v-model="filters.categoryKeyword" placeholder="请选择分类" />
        </el-form-item>
        <el-form-item label="原因分类">
          <el-select v-model="filters.causeCategory" clearable placeholder="请选择原因">
            <el-option v-for="item in causeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源渠道">
          <el-select v-model="filters.source" clearable placeholder="请选择来源">
            <el-option v-for="item in sourceOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统/设备">
          <el-select v-model="filters.systemType" clearable placeholder="请选择系统">
            <el-option v-for="item in systemTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="闭环状态">
          <el-select v-model="filters.closureStatus" clearable placeholder="请选择闭环状态">
            <el-option label="未闭环" value="OPEN" />
            <el-option label="已闭环" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度">
          <el-input v-model="filters.severity" placeholder="搜索严重程度" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="filters.tagKeyword" placeholder="搜索标签" />
        </el-form-item>
        </template>
        <el-form-item class="action-item">
          <div class="filter-actions">
            <el-button @click="resetFilters">重置</el-button>
            <el-button type="primary" @click="applyFilters">查询</el-button>
          </div>
        </el-form-item>
      </el-form>
    </section>

    <section class="metric-grid">
      <article v-for="card in statCards" :key="card.label" class="metric-card has-icon">
        <div class="metric-icon" :style="{ background: card.gradient }">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div>
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value">{{ numberText(card.value) }}</div>
          <div class="metric-note">
            较上周 <span :class="card.up ? 'danger-text' : 'success-text'">{{ card.up ? '↑' : '↓' }} {{ card.rate }}</span>
          </div>
        </div>
        <svg class="sparkline" viewBox="0 0 120 40" preserveAspectRatio="none">
          <polyline :points="card.line" fill="none" :stroke="card.stroke" stroke-width="2.4" />
        </svg>
      </article>
    </section>

    <section class="section-card">
      <div class="table-actions">
        <div>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            手工新建
          </el-button>
          <el-button :loading="exporting" @click="exportIssues">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
        </div>
      </div>

      <el-table :data="issues" v-loading="loading" row-key="id" empty-text="暂无符合条件的问题" @row-click="goDetail">
        <el-table-column prop="issueNo" label="问题编号" min-width="150" />
        <el-table-column label="子项目" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="project-name-cell" :title="row.projectName">{{ displayProjectName(row.projectName) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reporterName" label="反馈人" width="110" />
        <el-table-column prop="categoryPath" label="分类" min-width="140" show-overflow-tooltip />
        <el-table-column label="区域" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ [row.buildingName, row.floorName, row.areaName].filter(Boolean).join(' / ') || '-' }}</template>
        </el-table-column>
        <el-table-column prop="systemType" label="系统/设备" width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.currentStatus).type">{{ statusMeta(row.currentStatus).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="处理人" width="120" />
        <el-table-column label="收到反馈时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.receivedAt || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.updateTime || row.createTime || row.receivedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <span>共 {{ numberText(pagination.total) }} 条</span>
        <el-pagination
          background
          layout="sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          :current-page="pagination.page"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" width="1080px" class="create-issue-dialog" destroy-on-close>
      <template #header>
        <div class="create-dialog-header">
          <div>
            <h2>手工新建问题</h2>
            <p>先快速完成基础登记，定位与分类信息可以随后补充。</p>
          </div>
          <div class="time-tip">
            <el-icon><Clock /></el-icon>
            <div>
              <strong>预计 1-3 分钟完成基础登记</strong>
              <span>必填项已前置，后续可在详情页继续完善</span>
            </div>
          </div>
        </div>
      </template>

      <div class="create-steps">
        <template v-for="(step, index) in createSteps" :key="step.title">
          <button
            type="button"
            class="step-pill"
            :class="{ active: createStep === index, done: createStep > index }"
            @click="createStep = index"
          >
            <span>{{ index + 1 }}</span>{{ step.title }}
          </button>
          <div v-if="index < createSteps.length - 1" class="step-line" :class="{ done: createStep > index }"></div>
        </template>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="create-form">
        <section v-show="createStep === 0" class="create-section required-section">
          <div class="dialog-section-title">基础登记</div>
          <p class="section-hint">先填写项目、反馈人和问题现象，保证问题可以被快速接手。</p>
          <div class="dialog-grid three-cols">
            <el-form-item label="子项目" prop="projectId">
              <el-select v-model="form.projectId" placeholder="选择子项目" filterable>
                <el-option v-for="project in selectableProjects" :key="project.id" :label="project.projectName" :value="project.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="收到反馈时间" prop="receivedAt">
              <el-date-picker v-model="form.receivedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
            <el-form-item label="反馈人" prop="reporterName">
              <el-input v-model="form.reporterName" placeholder="输入反馈人姓名" />
            </el-form-item>
            <el-form-item label="处理人">
              <el-input v-model="form.ownerName" placeholder="默认未分配" />
            </el-form-item>
            <el-form-item label="当前状态">
              <el-select v-model="form.currentStatus">
                <el-option label="待处理" value="OPEN" />
                <el-option label="处理中" value="IN_PROGRESS" />
                <el-option label="待确认" value="PENDING_CONFIRM" />
                <el-option label="已挂起" value="SUSPENDED" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源渠道">
              <el-select v-model="form.source">
                <el-option v-for="item in sourceOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <section v-show="createStep === 1" class="create-section">
          <div class="dialog-section-title">定位信息</div>
          <p class="section-hint">用于后续派单和复盘，不确定时可保留“未确认”。</p>
          <div class="dialog-grid">
            <el-form-item label="建筑/楼栋">
              <el-input v-model="form.buildingName" placeholder="不清楚填未确认" />
            </el-form-item>
            <el-form-item label="楼层">
              <el-input v-model="form.floorName" placeholder="如 2F、屋顶、厂区" />
            </el-form-item>
            <el-form-item label="区域/房间">
              <el-input v-model="form.areaName" placeholder="如 办公区、会议室" />
            </el-form-item>
            <el-form-item label="系统/设备类型">
              <el-select v-model="form.systemType" filterable allow-create default-first-option>
                <el-option v-for="item in systemTypeOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="设备编号/点位">
              <el-input v-model="form.devicePoint" placeholder="GW、CU、MACID、二维码点位等" />
            </el-form-item>
          </div>
        </section>

        <section v-show="createStep === 2" class="create-section">
          <div class="dialog-section-title">问题定义</div>
          <p class="section-hint">把问题标题、现象和影响范围先说清楚，便于识别与检索。</p>
          <div class="dialog-grid">
            <el-form-item label="问题分类">
              <el-select v-model="form.categoryPath" filterable allow-create default-first-option>
                <el-option v-for="item in problemCategoryOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="紧急程度">
              <el-select v-model="form.priority" clearable placeholder="请选择">
                <el-option v-for="item in priorityOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="问题标题" prop="itemTitle">
            <el-input v-model="form.itemTitle" maxlength="200" show-word-limit placeholder="一句话说明问题，便于识别与检索" />
          </el-form-item>
          <el-form-item label="问题现象" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="详细描述问题发生的现象、时间、频率等" />
          </el-form-item>
          <el-form-item label="影响范围">
            <el-input v-model="form.impactScope" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明受影响的区域、设备或业务范围（可选）" />
          </el-form-item>
        </section>

        <section v-show="createStep === 3" class="create-section">
          <div class="dialog-section-title">补充信息</div>
          <p class="section-hint">补充图片、初步说明和原因判断，后续处理时可以继续完善。</p>
          <div class="dialog-grid">
            <div class="image-upload-wrap">
              <div class="field-label required-label">问题图片</div>
              <div
                class="image-upload-panel"
                :class="{ dragging: isDraggingImages }"
                @dragenter.prevent="isDraggingImages = true"
                @dragover.prevent="isDraggingImages = true"
                @dragleave.prevent="isDraggingImages = false"
                @drop.prevent="handleCreateImagesDrop"
              >
                <input ref="imageInputRef" class="hidden-file-input" type="file" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" multiple @change="handleCreateImagesChange" />
                <div class="upload-dropzone" @click="triggerCreateImageSelect">
                  <el-icon><Upload /></el-icon>
                  <strong>点击或拖拽图片到这里上传</strong>
                  <span>必填，支持 jpg / jpeg / png / webp，单张不超过 10MB，上传前会自动压缩到 1920px 宽以内。</span>
                </div>
              </div>
            </div>
            <div>
              <el-form-item label="初步原因判断">
                <el-select v-model="form.causeCategory">
                  <el-option v-for="item in causeOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item label="初步说明 / 补充备注">
                <el-input v-model="form.latestProgress" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="补充背景、已沟通信息或初步处理判断（可选）" />
              </el-form-item>
            </div>
          </div>
          <div v-if="pendingImages.length" class="image-preview-grid">
            <div v-for="image in pendingImages" :key="image.uid" class="image-preview-item">
              <img :src="image.url" :alt="image.name" />
              <button type="button" @click.stop="removeCreateImage(image.uid)">
                <el-icon><Close /></el-icon>
              </button>
              <span :title="image.name">{{ image.name }}</span>
              <small>{{ formatFileSize(image.size) }}</small>
            </div>
          </div>
        </section>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :disabled="createStep === 0" @click="goCreateStep(-1)">上一步</el-button>
        <el-button v-if="createStep < createSteps.length - 1" type="primary" plain @click="goCreateStep(1)">下一步</el-button>
        <el-button type="primary" :loading="submitting && submitTarget === 'detail'" @click="submitIssue('detail')">保存并进入详情</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createIssue, getIssueOverview, getIssues, uploadIssueAttachment } from '@/api/issues'
import { getProjects } from '@/api/projects'
import { getRuleOptions } from '@/api/ruleOptions'
import { formatDateTime, numberText, statusMeta, toDateTimeQuery } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const exporting = ref(false)
const submitting = ref(false)
const submitTarget = ref('')
const dialogVisible = ref(false)
const formRef = ref()
const imageInputRef = ref()
const dateRange = ref([])
const showAdvancedFilters = ref(false)
const isDraggingImages = ref(false)
const createStep = ref(0)
const overview = ref({ totalCount: 0, openCount: 0, inProgressCount: 0, pendingReviewCount: 0 })
const projects = ref([])
const issues = ref([])
const ruleOptions = ref({})
const pendingImages = ref([])

const selectableProjects = computed(() => (projects.value || []).filter(project =>
  (project.projectLevel || 'PROJECT') === 'PROJECT' && Number(project.isActive) === 1 && hasActiveParents(project)
))
const nowText = computed(() => formatDateTime(new Date()))
const createSteps = [
  { title: '基础信息' },
  { title: '定位信息' },
  { title: '问题定义' },
  { title: '补充信息' }
]
const fieldStepMap = {
  projectId: 0,
  receivedAt: 0,
  reporterName: 0,
  itemTitle: 2,
  description: 2
}

const filters = reactive({ projectId: undefined, currentStatus: '', closureStatus: '', categoryKeyword: '', causeCategory: '', source: '', priority: '', severity: '', systemType: '', tagKeyword: '', ownerName: '', keyword: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const sourceOptions = computed(() => ruleOptions.value.sourceChannels || ['客户微信群', '电话', '邮件', '现场巡检', '系统告警', 'Excel/CSV 导入', '内部排查', '手动录入'])
const priorityOptions = computed(() => ruleOptions.value.priorities || ['高', '中', '低'])
const causeOptions = computed(() => ruleOptions.value.causeCategories || ['原因待确认'])
const problemCategoryOptions = computed(() => ruleOptions.value.problemCategories || ['待确认问题'])
const systemTypeOptions = computed(() => ruleOptions.value.systemTypes || ['未确认'])

function hasActiveParents(project) {
  const byCode = new Map((projects.value || []).map(item => [item.projectCode, item]))
  let parentCode = project.parentProjectCode
  while (parentCode) {
    const parent = byCode.get(parentCode)
    if (!parent) return true
    if (Number(parent.isActive) !== 1) return false
    parentCode = parent.parentProjectCode
  }
  return true
}

const form = reactive({
  projectId: undefined,
  reporterName: '',
  categoryPath: '待确认问题',
  source: '手动录入',
  buildingName: '未确认',
  floorName: '未确认',
  areaName: '未确认',
  systemType: '未确认',
  devicePoint: '未确认',
  receivedAt: '',
  createTime: '',
  itemTitle: '',
  description: '',
  impactScope: '',
  priority: '中',
  ownerName: '未分配',
  currentStatus: 'OPEN',
  latestProgress: '',
  completionStatus: '',
  completedAt: '',
  notes: '',
  sourceType: 'MANUAL',
  causeCategory: '原因待确认',
  reminderEnabled: 1,
  remindAfterDays: 7
})

const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  receivedAt: [{ required: true, message: '请选择收到反馈时间', trigger: 'change' }],
  reporterName: [{ required: true, message: '请输入反馈人', trigger: 'blur' }],
  itemTitle: [{ required: true, message: '请输入问题标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入问题现象', trigger: 'blur' }]
}

const statCards = computed(() => [
  { label: '问题总量', value: overview.value.totalCount, icon: 'Collection', rate: '12.6%', up: true, gradient: 'linear-gradient(135deg, #1677ff, #0f62d8)', stroke: '#1677ff', line: '0,28 14,20 28,25 42,18 56,22 70,8 84,18 98,5 112,16 120,13' },
  { label: '待处理', value: overview.value.openCount, icon: 'Timer', rate: '8.3%', up: true, gradient: 'linear-gradient(135deg, #ff9f43, #ff6b00)', stroke: '#ff7a1a', line: '0,30 12,22 26,28 40,16 54,25 68,10 82,18 96,5 110,12 120,18' },
  { label: '处理中', value: overview.value.inProgressCount, icon: 'Loading', rate: '6.1%', up: false, gradient: 'linear-gradient(135deg, #2ac36a, #15944b)', stroke: '#1fa463', line: '0,26 14,18 28,24 42,20 56,28 70,12 84,18 98,17 112,22 120,16' },
  { label: '待确认导入', value: overview.value.pendingReviewCount, icon: 'UploadFilled', rate: '15.4%', up: true, gradient: 'linear-gradient(135deg, #8b6cff, #6546de)', stroke: '#7357f6', line: '0,27 12,20 24,25 36,12 48,23 60,8 72,18 84,5 96,14 108,9 120,20' }
])

async function loadIssues() {
  loading.value = true
  try {
    const [issueRes, overviewRes] = await Promise.all([
      getIssues({
        ...buildQueryParams(),
        page: pagination.page,
        pageSize: pagination.pageSize
      }),
      getIssueOverview(buildQueryParams())
    ])
    issues.value = issueRes.data?.items || []
    pagination.total = issueRes.data?.total || 0
    overview.value = overviewRes.data || overview.value
  } finally {
    loading.value = false
  }
}

function buildQueryParams() {
  return {
    ...filters,
    startDate: toDateTimeQuery(dateRange.value?.[0]),
    endDate: toDateTimeQuery(dateRange.value?.[1], true)
  }
}

async function loadProjects() {
  const res = await getProjects()
  projects.value = res.data || []
}

async function loadRuleOptions() {
  const res = await getRuleOptions()
  ruleOptions.value = res.data || {}
}

function applyFilters() {
  pagination.page = 1
  loadIssues()
}

function resetFilters() {
  Object.assign(filters, { projectId: undefined, currentStatus: '', closureStatus: '', categoryKeyword: '', causeCategory: '', source: '', priority: '', severity: '', systemType: '', tagKeyword: '', ownerName: '', keyword: '' })
  dateRange.value = []
  pagination.page = 1
  loadIssues()
}

function handlePageChange(page) {
  pagination.page = page
  loadIssues()
}

function handleSizeChange(size) {
  pagination.pageSize = size
  pagination.page = 1
  loadIssues()
}

function goDetail(row) {
  const issueId = row?.id || row
  if (issueId) router.push(`/issues/${issueId}`)
}

function displayProjectName(projectName) {
  if (!projectName) return '-'
  return String(projectName).replace('上海交通大学', '上海交大')
}

async function exportIssues() {
  exporting.value = true
  try {
    const res = await getIssues({
      ...buildQueryParams(),
      page: 1,
      pageSize: Math.max(pagination.total || issues.value.length || 20, 20)
    })
    const rows = res.data?.items || []
    if (!rows.length) {
      ElMessage.warning('暂无可导出的数据')
      return
    }
    downloadCsv(`问题台账-${new Date().toISOString().slice(0, 10)}.csv`, rowsToCsv(rows))
    ElMessage.success('导出已生成')
  } finally {
    exporting.value = false
  }
}

function rowsToCsv(rows) {
  const columns = [
    ['问题编号', 'issueNo'],
    ['子项目', 'projectName'],
    ['反馈人', 'reporterName'],
    ['分类', 'categoryPath'],
    ['系统/设备', 'systemType'],
    ['状态', row => statusMeta(row.currentStatus).label],
    ['优先级', 'priority'],
    ['处理人', 'ownerName'],
    ['收到反馈时间', row => formatDateTime(row.receivedAt || row.createTime)],
    ['更新时间', row => formatDateTime(row.updateTime || row.createTime || row.receivedAt)]
  ]
  const header = columns.map(([label]) => csvCell(label)).join(',')
  const body = rows.map(row => columns.map(([, getter]) => csvCell(typeof getter === 'function' ? getter(row) : row[getter])).join(','))
  return [header, ...body].join('\r\n')
}

function csvCell(value) {
  return `"${String(value ?? '').replace(/"/g, '""')}"`
}

function downloadCsv(fileName, content) {
  const blob = new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

function resetCreateForm() {
  clearPendingImages()
  Object.assign(form, {
    projectId: undefined,
    reporterName: '',
    categoryPath: '待确认问题',
    source: '手动录入',
    buildingName: '未确认',
    floorName: '未确认',
    areaName: '未确认',
    systemType: '未确认',
    devicePoint: '未确认',
    receivedAt: defaultReceivedAt(),
    createTime: defaultReceivedAt(),
    itemTitle: '',
    description: '',
    impactScope: '',
    priority: '中',
    ownerName: '未分配',
    currentStatus: 'OPEN',
    latestProgress: '',
    completionStatus: '',
    completedAt: '',
    notes: '',
    sourceType: 'MANUAL',
    causeCategory: '原因待确认',
    reminderEnabled: 1,
    remindAfterDays: 7
  })
}

function triggerCreateImageSelect() {
  imageInputRef.value?.click()
}

async function handleCreateImagesChange(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  await processCreateImageFiles(files)
}

async function handleCreateImagesDrop(event) {
  isDraggingImages.value = false
  const files = Array.from(event.dataTransfer?.files || [])
  await processCreateImageFiles(files)
}

async function processCreateImageFiles(files) {
  for (const file of files) {
    try {
      const compressed = await compressImageFile(file)
      pendingImages.value.push({
        uid: `${Date.now()}-${Math.random()}`,
        name: compressed.name,
        size: compressed.size,
        file: compressed,
        url: URL.createObjectURL(compressed)
      })
    } catch (error) {
      ElMessage.error(error.message || '上传失败，请稍后重试或检查文件格式')
    }
  }
}

function removeCreateImage(uid) {
  const image = pendingImages.value.find(item => item.uid === uid)
  if (image?.url) URL.revokeObjectURL(image.url)
  pendingImages.value = pendingImages.value.filter(item => item.uid !== uid)
}

function clearPendingImages() {
  pendingImages.value.forEach(image => {
    if (image.url) URL.revokeObjectURL(image.url)
  })
  pendingImages.value = []
  isDraggingImages.value = false
}

function compressImageFile(file) {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    return Promise.reject(new Error('仅支持 jpg、jpeg、png、webp 图片'))
  }

  return new Promise((resolve, reject) => {
    const image = new Image()
    const objectUrl = URL.createObjectURL(file)
    image.onload = () => {
      const scale = image.width > 1920 ? 1920 / image.width : 1
      const width = Math.round(image.width * scale)
      const height = Math.round(image.height * scale)
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const context = canvas.getContext('2d')
      context.drawImage(image, 0, 0, width, height)
      const outputType = file.type === 'image/png' ? 'image/png' : file.type === 'image/webp' ? 'image/webp' : 'image/jpeg'
      canvas.toBlob(blob => {
        URL.revokeObjectURL(objectUrl)
        if (!blob) {
          reject(new Error('上传失败，请稍后重试或检查文件格式'))
          return
        }
        if (blob.size > 10 * 1024 * 1024) {
          reject(new Error('文件超过 10MB，请压缩后再上传'))
          return
        }
        const extension = outputType === 'image/png' ? 'png' : outputType === 'image/webp' ? 'webp' : 'jpg'
        const baseName = file.name.replace(/\.[^.]+$/, '')
        resolve(new File([blob], `${baseName}.${extension}`, { type: outputType }))
      }, outputType, 0.75)
    }
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('上传失败，请稍后重试或检查文件格式'))
    }
    image.src = objectUrl
  })
}

function defaultReceivedAt() {
  const date = new Date()
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

function openCreateDialog() {
  resetCreateForm()
  createStep.value = 0
  dialogVisible.value = true
}

function goCreateStep(offset) {
  createStep.value = Math.min(createSteps.length - 1, Math.max(0, createStep.value + offset))
}

async function validateCreateForm() {
  try {
    await formRef.value?.validate()
    return true
  } catch (invalidFields) {
    const firstField = Object.keys(invalidFields || {})[0]
    if (fieldStepMap[firstField] !== undefined) {
      createStep.value = fieldStepMap[firstField]
    }
    return false
  }
}

async function submitIssue(target = 'detail') {
  const valid = await validateCreateForm()
  if (!valid) return

  submitting.value = true
  submitTarget.value = target
  try {
    const res = await createIssue(form)
    const issueId = res.data?.id
    if (issueId && pendingImages.value.length) {
      const results = await Promise.allSettled(pendingImages.value.map(image => uploadIssueAttachment(issueId, image.file)))
      if (results.some(result => result.status === 'rejected')) {
        ElMessage.warning('问题已创建，部分图片上传失败，可到详情页补传')
      } else {
        ElMessage.success('问题创建成功，图片已上传')
      }
    } else {
      ElMessage.success('问题创建成功')
    }
    clearPendingImages()
    dialogVisible.value = false
    pagination.page = 1
    await loadIssues()
    if (target === 'detail' && issueId) {
      router.push(`/issues/${issueId}`)
    }
  } finally {
    submitting.value = false
    submitTarget.value = ''
  }
}

function formatFileSize(size) {
  const value = Number(size || 0)
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${value} B`
}

onMounted(async () => {
  await Promise.all([loadRuleOptions(), loadProjects(), loadIssues()])
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

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 18px;
}

.filter-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.filter-card-head .el-button .el-icon {
  margin-left: 4px;
  transition: transform 0.2s ease;
}

.filter-grid :deep(.el-form-item) {
  margin-bottom: 0;
}

.wide-item {
  grid-column: span 2;
}

.action-item {
  align-self: end;
}

.filter-actions,
.table-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.table-actions {
  margin-bottom: 14px;
}

.pager {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 18px;
  color: var(--text-muted);
}

.project-name-cell {
  display: inline-block;
  max-width: 100%;
  white-space: normal;
  line-height: 1.35;
  word-break: break-word;
}

.create-issue-dialog :deep(.el-dialog) {
  border-radius: 10px;
}

.create-issue-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}

.create-dialog-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
}

.create-dialog-header h2 {
  margin: 0;
  font-size: 24px;
  line-height: 1.25;
}

.create-dialog-header p {
  margin: 8px 0 0;
  color: var(--text-muted);
}

.time-tip {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 280px;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  background: var(--primary-soft);
  color: var(--primary);
}

.time-tip .el-icon {
  font-size: 28px;
}

.time-tip div {
  display: grid;
  gap: 4px;
}

.time-tip span {
  color: var(--text-muted);
  font-size: 12px;
}

.create-steps {
  display: grid;
  grid-template-columns: auto minmax(24px, 1fr) auto minmax(24px, 1fr) auto minmax(24px, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.step-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}

.step-pill span {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eef3fa;
  color: #56657d;
}

.step-pill.active {
  color: var(--primary);
}

.step-pill.active span,
.step-pill.done span {
  background: var(--primary);
  color: #fff;
}

.step-pill.done {
  color: #315d9f;
}

.step-line {
  height: 1px;
  background: linear-gradient(90deg, var(--primary), var(--line-soft));
}

.step-line.done {
  background: var(--primary);
}

.create-form {
  max-height: min(68vh, 760px);
  overflow: auto;
  padding-right: 6px;
}

.create-section {
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
}

.create-section + .create-section {
  margin-top: 14px;
}

.required-section {
  background: linear-gradient(180deg, #fbfdff, #fff);
  border-color: #cfe0f8;
}

.field-label {
  margin-bottom: 8px;
  color: #606266;
  font-size: 14px;
  line-height: 1.2;
}

.required-label::after {
  content: " *";
  color: var(--danger);
}

.image-upload-wrap {
  min-width: 0;
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.dialog-grid.three-cols {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.dialog-section-title {
  margin: 0 0 8px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  font-weight: 800;
  color: var(--text-main);
}

.hidden-file-input {
  display: none;
}

.image-upload-panel {
  margin-bottom: 4px;
}

.upload-dropzone {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 22px;
  border: 1px dashed #9ec5ff;
  border-radius: var(--radius-md);
  background: #f7fbff;
  color: var(--primary);
  text-align: center;
  cursor: pointer;
}

.upload-dropzone .el-icon {
  font-size: 30px;
}

.upload-dropzone span {
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.image-upload-panel.dragging .upload-dropzone {
  border-color: var(--primary);
  background: #edf6ff;
  box-shadow: inset 0 0 0 1px rgba(22, 119, 255, 0.18);
}

.image-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(118px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.image-preview-item {
  position: relative;
  min-width: 0;
}

.image-preview-item img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--line-soft);
}

.image-preview-item button {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 50%;
  color: #fff;
  background: rgba(0, 0, 0, 0.56);
  cursor: pointer;
}

.image-preview-item span {
  display: block;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-preview-item small {
  display: block;
  margin-top: 2px;
  color: #8a98ad;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .filter-grid,
  .dialog-grid.three-cols {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .filter-grid,
  .dialog-grid,
  .dialog-grid.three-cols {
    grid-template-columns: 1fr;
  }

  .wide-item {
    grid-column: span 1;
  }

  .create-dialog-header {
    flex-direction: column;
  }

  .time-tip {
    min-width: 0;
    width: 100%;
  }

  .create-steps {
    grid-template-columns: 1fr;
  }

  .step-line {
    display: none;
  }
}
</style>
