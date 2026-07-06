<template>
  <div class="page-shell" v-loading="loading">
    <section class="section-card">
      <div class="toolbar">
        <div>
          <div class="page-title">项目中心</div>
          <p class="page-subtitle">维护客户、项目、子项目、合同质保和项目启用状态。</p>
        </div>
        <div v-if="isAdmin" class="toolbar-actions">
          <el-button type="primary" @click="openCreate('CUSTOMER')">新增客户</el-button>
          <el-button @click="openCreate('PROJECT_GROUP')">新增项目</el-button>
          <el-button @click="openCreate('PROJECT')">新增子项目</el-button>
        </div>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">客户数</div>
        <div class="metric-value">{{ numberText(customerCount) }}</div>
        <div class="metric-note">当前可见客户数量</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">启用子项目</div>
        <div class="metric-value">{{ numberText(activeLeafCount) }}</div>
        <div class="metric-note">可用于新建问题</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">禁用项目</div>
        <div class="metric-value danger-text">{{ numberText(disabledCount) }}</div>
        <div class="metric-note">历史问题仍可查看</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">未闭环问题</div>
        <div class="metric-value danger-text">{{ numberText(totalOpenCount) }}</div>
        <div class="metric-note">全部可见子项目汇总</div>
      </article>
    </section>

    <section class="project-layout">
      <article class="section-card">
        <div class="section-title">客户汇总</div>
        <p class="section-hint">点击客户可快速过滤右侧项目清单。</p>
        <div class="compact-list">
          <button
            v-for="item in customerSummary"
            :key="item.name"
            type="button"
            class="compact-row"
            :class="{ active: keyword === item.name }"
            @click="selectCustomer(item.name)"
          >
            <span>{{ item.name }}</span>
            <strong>
              {{ numberText(item.openCount) }} / {{ numberText(item.issueCount) }}
              <small>未闭环 / 总数</small>
            </strong>
          </button>
        </div>
      </article>

      <article class="section-card">
        <div class="table-toolbar">
          <div class="section-title">项目清单</div>
          <el-input v-model="keyword" clearable placeholder="搜索客户、项目、编码" class="search-input" />
        </div>
        <el-table
          :data="filteredProjects"
          row-key="id"
          highlight-current-row
          empty-text="暂无项目数据"
          @row-click="selectProject"
        >
          <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
          <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="projectCode" label="编码" min-width="180" show-overflow-tooltip />
          <el-table-column label="层级" width="110">
            <template #default="{ row }">
              <el-tag :type="levelMeta(row.projectLevel).type">{{ levelMeta(row.projectLevel).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="Number(row.isActive) === 1 ? 'success' : 'info'">
                {{ Number(row.isActive) === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="问题" width="110">
            <template #default="{ row }">{{ numberText(row.openCount || 0) }} / {{ numberText(row.issueCount || 0) }}</template>
          </el-table-column>
          <el-table-column v-if="isAdmin" label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button>
              <el-button
                link
                :type="Number(row.isActive) === 1 ? 'danger' : 'success'"
                @click.stop="toggleProject(row)"
              >
                {{ Number(row.isActive) === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>

    <section v-if="selectedProject" class="detail-grid">
      <article class="section-card">
        <div class="section-title marked">项目概览</div>
        <div class="summary-grid project-summary-grid">
          <div class="info-pair"><span>客户</span><strong>{{ selectedProject.customerName || '-' }}</strong></div>
          <div class="info-pair"><span>项目</span><strong>{{ selectedProject.projectName }}</strong></div>
          <div class="info-pair"><span>编码</span><strong>{{ selectedProject.projectCode || '-' }}</strong></div>
          <div class="info-pair"><span>提醒阈值</span><strong>{{ selectedProject.remindAfterDays || 7 }} 天</strong></div>
          <div class="info-pair"><span>状态</span><strong>{{ Number(selectedProject.isActive) === 1 ? '启用' : '禁用' }}</strong></div>
          <div class="info-pair"><span>未闭环</span><strong>{{ selectedProject.openCount || 0 }}</strong></div>
        </div>
        <div class="project-description">
          <div class="section-title">项目说明</div>
          <p>{{ selectedProject.description || '暂无项目说明' }}</p>
        </div>
      </article>

      <article class="section-card">
        <div class="section-title marked">项目联系人</div>
        <el-table :data="contacts" empty-text="暂无联系人">
          <el-table-column prop="positionTitle" label="岗位/职务" min-width="140" />
          <el-table-column prop="contactName" label="联系人" width="120" />
          <el-table-column prop="contactInfo" label="联系方式" min-width="170" />
          <el-table-column prop="responsibility" label="负责事项" min-width="220" show-overflow-tooltip />
        </el-table>
      </article>
    </section>

    <section v-if="selectedProject" class="section-card">
      <div class="table-toolbar">
        <div>
          <div class="section-title marked">合同与质保</div>
        </div>
        <div v-if="isAdmin" class="contract-actions">
          <el-button @click="openWarrantyCreate">手工录入</el-button>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".xls,.xlsx"
            :on-change="handleContractExcelChange"
          >
            <el-button :loading="contractExcelUploading">导入合同Excel</el-button>
          </el-upload>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".pdf"
            :on-change="handleContractPdfChange"
          >
            <el-button type="primary" :loading="contractPdfUploading">上传PDF合同</el-button>
          </el-upload>
        </div>
      </div>
      <el-table :data="warrantyList" empty-text="暂无合同记录">
        <el-table-column prop="contractType" label="合同类型" min-width="130" />
        <el-table-column label="开始日期" min-width="130">
          <template #default="{ row }">{{ formatDate(row.startAt || row.acceptanceAt || row.contractSignedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束日期" min-width="130">
          <template #default="{ row }">{{ formatDate(row.endAt || row.expireAt) }}</template>
        </el-table-column>
        <el-table-column label="合同状态" min-width="130">
          <template #default="{ row }">
            <el-tag :type="contractStatusType(row.contractStatus)">{{ row.contractStatus || '待补充' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="serviceScope" label="服务范围" min-width="220" show-overflow-tooltip />
        <el-table-column label="合同原件" min-width="160">
          <template #default="{ row }">
            <el-link v-if="row.downloadUrl" :href="row.downloadUrl" target="_blank" type="primary">
              {{ row.fileName || '下载PDF' }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="notes" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column v-if="isAdmin" label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openWarrantyEdit(row)">编辑信息</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingProject ? '编辑项目' : '新增项目'" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目层级" prop="projectLevel">
              <el-select v-model="form.projectLevel" :disabled="Boolean(editingProject)" @change="handleLevelChange">
                <el-option label="客户" value="CUSTOMER" />
                <el-option label="项目" value="PROJECT_GROUP" />
                <el-option label="子项目" value="PROJECT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上级项目" prop="parentProjectCode">
              <el-select v-model="form.parentProjectCode" clearable filterable :disabled="form.projectLevel === 'CUSTOMER'" placeholder="选择上级项目">
                <el-option
                  v-for="project in parentOptions"
                  :key="project.projectCode"
                  :label="`${project.projectName}（${levelMeta(project.projectLevel).label}）`"
                  :value="project.projectCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目名称" prop="projectName">
              <el-input v-model="form.projectName" @blur="fillProjectCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目编码" prop="projectCode">
              <el-input v-model="form.projectCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称">
              <el-input v-model="form.customerName" :disabled="form.projectLevel === 'CUSTOMER'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目组">
              <el-input v-model="form.projectGroup" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态">
              <el-switch v-model="form.isActive" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="提醒阈值">
              <el-input-number v-model="form.remindAfterDays" :min="1" :max="365" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="项目说明">
              <el-input v-model="form.description" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitProject">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="warrantyDialogVisible" title="编辑合同信息" width="640px">
      <el-form :model="warrantyForm" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="合同类型">
              <el-input v-model="warrantyForm.contractType" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质保期限">
              <el-input v-model="warrantyForm.warrantyTerm" placeholder="如：1年质保期" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="warrantyForm.startAt" type="date" value-format="YYYY-MM-DDT00:00:00" placeholder="选择开始日期" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-date-picker v-model="warrantyForm.endAt" type="date" value-format="YYYY-MM-DDT23:59:59" placeholder="选择结束日期" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="服务范围">
              <el-input v-model="warrantyForm.serviceScope" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="warrantyForm.notes" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="warrantyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="warrantySaving" @click="submitWarranty">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createProject, createProjectWarranty, disableProject, enableProject, getProjectContacts, getProjects, getProjectWarranty, updateProject, updateProjectWarranty, uploadProjectWarrantyFile } from '@/api/projects'
import { uploadExcel } from '@/api/imports'
import { useUserStore } from '@/stores/user'
import { formatDate, numberText } from '@/utils/format'

const userStore = useUserStore()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const editingProject = ref(null)
const keyword = ref('')
const contractExcelUploading = ref(false)
const contractPdfUploading = ref(false)
const warrantyDialogVisible = ref(false)
const warrantySaving = ref(false)
const editingWarranty = ref(null)
const projects = ref([])
const selectedProjectId = ref(null)
const contacts = ref([])
const warrantyList = ref([])

const form = reactive(defaultForm())
const warrantyForm = reactive(defaultWarrantyForm())
const rules = {
  projectLevel: [{ required: true, message: '请选择项目层级', trigger: 'change' }],
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  parentProjectCode: [{
    validator: (_rule, value, callback) => {
      if (form.projectLevel !== 'CUSTOMER' && !value) callback(new Error('请选择上级项目'))
      else callback()
    },
    trigger: 'change'
  }]
}

const isAdmin = computed(() => userStore.isAdmin)
const leafProjects = computed(() => projects.value.filter(project => (project.projectLevel || 'PROJECT') === 'PROJECT'))
const customerSummary = computed(() => summarizeByKey(leafProjects.value, 'customerName'))
const customerCount = computed(() => new Set(projects.value.map(project => project.customerName).filter(Boolean)).size)
const activeLeafCount = computed(() => leafProjects.value.filter(project => Number(project.isActive) === 1).length)
const disabledCount = computed(() => projects.value.filter(project => Number(project.isActive) !== 1).length)
const totalOpenCount = computed(() => leafProjects.value.reduce((total, item) => total + (item.openCount || 0), 0))
const selectedProject = computed(() => projects.value.find(project => project.id === selectedProjectId.value))
const filteredProjects = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return projects.value
  return projects.value.filter(project =>
    `${project.customerName || ''}${project.projectName || ''}${project.projectCode || ''}`.toLowerCase().includes(text)
  )
})
const parentOptions = computed(() => {
  return projects.value.filter(project => {
    if (editingProject.value && project.id === editingProject.value.id) return false
    if (form.projectLevel === 'PROJECT_GROUP') return project.projectLevel === 'CUSTOMER'
    if (form.projectLevel === 'PROJECT') return project.projectLevel === 'CUSTOMER' || project.projectLevel === 'PROJECT_GROUP'
    return false
  })
})

function defaultForm(level = 'PROJECT') {
  return {
    customerName: '',
    projectGroup: '',
    projectName: '',
    projectCode: '',
    parentProjectCode: '',
    projectLevel: level,
    description: '',
    reminderEnabled: 1,
    remindAfterDays: 7,
    isActive: 1
  }
}

function defaultWarrantyForm() {
  return {
    contractType: '',
    startAt: '',
    endAt: '',
    serviceScope: '',
    warrantyTerm: '',
    notes: ''
  }
}

function summarizeByKey(list, key) {
  const bucket = new Map()
  list.forEach(item => {
    const name = item[key] || '未归类客户'
    const current = bucket.get(name) || { name, issueCount: 0, openCount: 0 }
    current.issueCount += item.issueCount || 0
    current.openCount += item.openCount || 0
    bucket.set(name, current)
  })
  return Array.from(bucket.values()).sort((a, b) => b.openCount - a.openCount || b.issueCount - a.issueCount)
}

function levelMeta(level) {
  const map = {
    CUSTOMER: { label: '客户', type: 'info' },
    PROJECT_GROUP: { label: '项目', type: 'warning' },
    PROJECT: { label: '子项目', type: 'success' }
  }
  return map[level] || map.PROJECT
}

function contractStatusType(status) {
  const map = {
    '生效中/在保': 'success',
    '即将到期': 'warning',
    '已过期': 'danger',
    '待补充': 'info'
  }
  return map[status] || 'info'
}

async function loadProjects() {
  loading.value = true
  try {
    const res = await getProjects()
    projects.value = res.data || []
    if (!selectedProjectId.value && projects.value.length > 0) {
      await selectProject(projects.value[0])
    } else if (selectedProjectId.value) {
      const current = projects.value.find(project => project.id === selectedProjectId.value)
      if (current) await loadRelated(current.id)
    }
  } finally {
    loading.value = false
  }
}

async function selectProject(row) {
  selectedProjectId.value = row.id
  await loadRelated(row.id)
}

async function loadRelated(id) {
  const [contactsRes, warrantyRes] = await Promise.all([getProjectContacts(id), getProjectWarranty(id)])
  contacts.value = contactsRes.data || []
  warrantyList.value = warrantyRes.data || []
}

function selectCustomer(name) {
  keyword.value = name
}

function openCreate(level) {
  editingProject.value = null
  Object.assign(form, defaultForm(level))
  dialogVisible.value = true
}

function openEdit(row) {
  editingProject.value = row
  Object.assign(form, {
    customerName: row.customerName || '',
    projectGroup: row.projectGroup || '',
    projectName: row.projectName || '',
    projectCode: row.projectCode || '',
    parentProjectCode: row.parentProjectCode || '',
    projectLevel: row.projectLevel || 'PROJECT',
    description: row.description || '',
    reminderEnabled: row.reminderEnabled ?? 1,
    remindAfterDays: row.remindAfterDays || 7,
    isActive: Number(row.isActive) === 1 ? 1 : 0
  })
  dialogVisible.value = true
}

function handleLevelChange() {
  form.parentProjectCode = ''
  if (form.projectLevel === 'CUSTOMER') {
    form.customerName = form.projectName
    form.projectGroup = form.projectName
  }
  fillProjectCode()
}

function fillProjectCode() {
  if (editingProject.value || form.projectCode || !form.projectName) return
  const prefix = form.projectLevel === 'CUSTOMER' ? 'CUS' : form.projectLevel === 'PROJECT_GROUP' ? 'GRP' : 'PRJ'
  const ascii = form.projectName.toUpperCase().replace(/[^A-Z0-9]+/g, '-').replace(/(^-|-$)/g, '')
  form.projectCode = `${prefix}-${ascii || Math.abs(hashText(form.projectName)).toString(16).toUpperCase()}`
}

function hashText(text) {
  return Array.from(text).reduce((hash, char) => ((hash << 5) - hash + char.charCodeAt(0)) | 0, 0)
}

async function submitProject() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingProject.value) {
      await updateProject(editingProject.value.id, form)
      ElMessage.success('项目已更新')
    } else {
      const res = await createProject(form)
      selectedProjectId.value = res.data?.id || selectedProjectId.value
      ElMessage.success('项目已新增')
    }
    dialogVisible.value = false
    await loadProjects()
  } finally {
    saving.value = false
  }
}

async function toggleProject(row) {
  const active = Number(row.isActive) === 1
  await ElMessageBox.confirm(
    active ? '禁用后历史问题仍可查看，但新建问题不能再选择该项目。' : '启用后该子项目可重新用于新建问题。',
    active ? '确认禁用项目' : '确认启用项目',
    { type: active ? 'warning' : 'info' }
  )
  if (active) await disableProject(row.id)
  else await enableProject(row.id)
  ElMessage.success(active ? '项目已禁用' : '项目已启用')
  await loadProjects()
}

async function handleContractPdfChange(uploadFile) {
  if (!selectedProject.value || !uploadFile?.raw) return
  contractPdfUploading.value = true
  try {
    await uploadProjectWarrantyFile(selectedProject.value.id, uploadFile.raw)
    ElMessage.success('PDF合同已上传')
    await Promise.all([loadProjects(), loadRelated(selectedProject.value.id)])
  } finally {
    contractPdfUploading.value = false
  }
}

async function handleContractExcelChange(uploadFile) {
  if (!uploadFile?.raw) return
  contractExcelUploading.value = true
  try {
    const res = await uploadExcel(uploadFile.raw)
    const batch = res.data?.batch
    if (!batch?.id) {
      ElMessage.warning('未识别到可导入的合同数据')
      return
    }
    ElMessage.success('合同Excel已上传到导入中心，请确认后提交入库')
    router.push('/imports')
  } finally {
    contractExcelUploading.value = false
  }
}

function openWarrantyEdit(row) {
  editingWarranty.value = row
  Object.assign(warrantyForm, {
    contractType: row.contractType || '',
    startAt: normalizeDateValue(row.startAt || row.acceptanceAt),
    endAt: normalizeDateValue(row.endAt || row.expireAt),
    serviceScope: row.serviceScope || '',
    warrantyTerm: row.warrantyTerm || '',
    notes: row.notes || ''
  })
  warrantyDialogVisible.value = true
}

function openWarrantyCreate() {
  editingWarranty.value = null
  Object.assign(warrantyForm, defaultWarrantyForm())
  warrantyDialogVisible.value = true
}

async function submitWarranty() {
  if (!selectedProject.value) return
  warrantySaving.value = true
  try {
    if (editingWarranty.value) {
      await updateProjectWarranty(selectedProject.value.id, editingWarranty.value.id, warrantyForm)
      ElMessage.success('合同信息已更新')
    } else {
      await createProjectWarranty(selectedProject.value.id, warrantyForm)
      ElMessage.success('合同信息已录入')
    }
    warrantyDialogVisible.value = false
    await loadRelated(selectedProject.value.id)
  } finally {
    warrantySaving.value = false
  }
}

function normalizeDateValue(value) {
  if (!value) return ''
  return String(value).slice(0, 10) + (String(value).includes('23:59:59') ? 'T23:59:59' : 'T00:00:00')
}

onMounted(async () => {
  if (!userStore.userInfo && userStore.token) {
    await userStore.fetchUserInfo().catch(() => {})
  }
  await loadProjects()
})
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.contract-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.project-layout,
.detail-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.45fr) minmax(0, 1.55fr);
  gap: 18px;
}

.compact-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}

.compact-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
}

.compact-row:hover {
  border-color: #b9d7ff;
  background: #f4f9ff;
}

.compact-row.active {
  border-color: #91caff;
  background: var(--primary-soft);
  box-shadow: inset 3px 0 0 var(--primary);
}

.compact-row strong {
  flex: 0 0 auto;
  display: grid;
  gap: 3px;
  color: var(--text-main);
  text-align: right;
}

.compact-row small {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 400;
}

.search-input {
  width: 260px;
}

.marked {
  padding-left: 12px;
  border-left: 4px solid var(--primary);
}

.project-summary-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 18px;
}

.project-description {
  margin-top: 18px;
}

.project-description p {
  color: var(--text-muted);
  line-height: 1.8;
}

@media (max-width: 1200px) {
  .project-layout,
  .detail-grid,
  .project-summary-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions,
  .contract-actions,
  .search-input {
    width: 100%;
  }
}
</style>
