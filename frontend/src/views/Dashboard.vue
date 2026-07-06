<template>
  <div class="page-shell" v-loading="loading">
    <div class="top-meta">
      <span>刷新时间：{{ nowText }}</span>
      <el-icon><Refresh /></el-icon>
    </div>

    <section class="metric-grid">
      <article v-for="card in statCards" :key="card.label" class="metric-card has-icon">
        <div class="metric-icon" :style="{ background: card.gradient }">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div>
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value">{{ numberText(card.value) }}</div>
          <div class="metric-note">{{ card.note }}</div>
        </div>
        <svg class="sparkline" viewBox="0 0 120 40" preserveAspectRatio="none">
          <polyline :points="card.line" fill="none" :stroke="card.stroke" stroke-width="2.4" />
        </svg>
      </article>
    </section>

    <section class="dashboard-grid">
      <article class="board-card">
        <div class="toolbar">
          <div class="section-title">最近问题列表</div>
          <el-button link type="primary" @click="goIssues">查看全部</el-button>
        </div>

        <el-table :data="recentIssues" empty-text="暂无问题数据" @row-click="goIssue">
          <el-table-column prop="issueNo" label="问题编号" min-width="150" />
          <el-table-column prop="projectName" label="子项目" min-width="130" />
          <el-table-column prop="categoryPath" label="分类" min-width="130" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.currentStatus).type">{{ statusMeta(row.currentStatus).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ownerName" label="处理人" width="110" />
          <el-table-column label="更新时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.updateTime || row.receivedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="goIssue(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="board-card alert-card">
        <div class="toolbar">
          <div class="section-title">待办预警</div>
          <el-button link type="primary" @click="goIssues">去处理问题</el-button>
        </div>

        <div class="alert-summary">
          <div v-for="item in alertCards" :key="item.label" class="alert-metric" :class="item.tone">
            <div class="alert-icon">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div>
              <span>{{ item.label }}</span>
              <strong>{{ numberText(item.value) }}</strong>
            </div>
          </div>
        </div>

        <div class="urgent-list">
          <div class="list-head">
            <span>优先关注</span>
            <small>超期、未分配和高优先级问题</small>
          </div>
          <div v-if="urgentIssues.length" class="urgent-items">
            <button v-for="item in urgentIssues" :key="item.id" class="urgent-item" type="button" @click="goIssue(item)">
              <div>
                <strong>{{ item.itemTitle || item.categoryPath || item.issueNo }}</strong>
                <span>{{ item.projectName || '-' }} · {{ item.ownerName || '未分配' }}</span>
              </div>
              <div class="urgent-meta">
                <el-tag v-if="item.overdue" type="danger" size="small">超期</el-tag>
                <el-tag v-else :type="statusMeta(item.currentStatus).type" size="small">
                  {{ statusMeta(item.currentStatus).label }}
                </el-tag>
                <small>{{ issueAgeText(item) }}</small>
              </div>
            </button>
          </div>
          <el-empty v-else description="暂无待办预警" :image-size="72" />
        </div>
      </article>
    </section>

    <section class="dashboard-grid lower-grid">
      <article class="board-card">
        <div class="toolbar">
          <div class="section-title">客户汇总（未关闭问题数 TOP10）</div>
          <el-select v-model="range" style="width: 110px">
            <el-option label="近30天" value="30" />
            <el-option label="近90天" value="90" />
          </el-select>
        </div>
        <div class="bar-chart">
          <div v-for="item in chartItems" :key="item.name" class="bar-item">
            <div class="bar-value">{{ numberText(item.openCount) }}</div>
            <div class="bar" :style="{ height: `${item.height}px` }"></div>
            <div class="bar-name">{{ item.name }}</div>
          </div>
        </div>
      </article>

      <article class="board-card workload-card">
        <div class="toolbar">
          <div class="section-title">负责人处理负载</div>
          <el-button link type="primary" @click="goIssues">查看台账</el-button>
        </div>
        <el-table :data="ownerWorkload" empty-text="暂无负责人负载数据" class="workload-table">
          <el-table-column prop="ownerName" label="负责人" min-width="120" />
          <el-table-column prop="total" label="未关闭" width="90" />
          <el-table-column prop="openCount" label="待处理" width="90" />
          <el-table-column prop="inProgressCount" label="处理中" width="90" />
          <el-table-column label="超期" width="90">
            <template #default="{ row }">
              <span :class="row.overdueCount ? 'danger-text' : ''">{{ numberText(row.overdueCount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="负载" min-width="150">
            <template #default="{ row }">
              <el-progress :percentage="row.percent" :show-text="false" :color="workloadColor(row.percent)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default>
              <el-button link type="primary" @click="goIssues">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getIssueOverview, getIssues } from '@/api/issues'
import { getProjects } from '@/api/projects'
import { formatDateTime, numberText, statusMeta } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const range = ref('30')
const overview = ref({ totalCount: 0, openCount: 0, inProgressCount: 0, closedCount: 0, overdueCount: 0 })
const recentIssues = ref([])
const issuePool = ref([])
const projects = ref([])

const nowText = computed(() => formatDateTime(new Date()))
const leafProjects = computed(() => (projects.value || []).filter(project => (project.projectLevel || 'PROJECT') === 'PROJECT'))
const activeIssues = computed(() => issuePool.value.filter(issue => !isClosed(issue)))

const statCards = computed(() => [
  {
    label: '问题总量',
    value: overview.value.totalCount,
    note: '全部问题记录',
    icon: 'Collection',
    gradient: 'linear-gradient(135deg, #1677ff, #0f62d8)',
    stroke: '#1677ff',
    line: '0,28 14,20 28,25 42,18 56,22 70,8 84,18 98,5 112,16 120,13'
  },
  {
    label: '待处理',
    value: overview.value.openCount,
    note: '需要响应的问题',
    icon: 'Timer',
    gradient: 'linear-gradient(135deg, #ff9f43, #ff6b00)',
    stroke: '#ff7a1a',
    line: '0,30 12,22 26,28 40,16 54,25 68,10 82,18 96,5 110,12 120,18'
  },
  {
    label: '处理中',
    value: overview.value.inProgressCount,
    note: '正在推进的问题',
    icon: 'Loading',
    gradient: 'linear-gradient(135deg, #2ac36a, #15944b)',
    stroke: '#1fa463',
    line: '0,26 14,18 28,24 42,20 56,28 70,12 84,18 98,17 112,22 120,16'
  },
  {
    label: '超期问题',
    value: overview.value.overdueCount,
    note: '超过提醒阈值',
    icon: 'WarningFilled',
    gradient: 'linear-gradient(135deg, #f56c6c, #d9363e)',
    stroke: '#d9363e',
    line: '0,27 12,20 24,25 36,12 48,23 60,8 72,18 84,5 96,14 108,9 120,20'
  }
])

const alertCards = computed(() => [
  { label: '超期问题', value: overview.value.overdueCount, icon: 'WarningFilled', tone: 'danger' },
  { label: '待处理', value: overview.value.openCount, icon: 'Timer', tone: 'warning' },
  { label: '未分配', value: unassignedCount.value, icon: 'User', tone: 'primary' }
])

const unassignedCount = computed(() => activeIssues.value.filter(issue => !issue.ownerName).length)

const urgentIssues = computed(() => {
  return [...activeIssues.value]
    .filter(issue => issue.overdue || !issue.ownerName || isHighPriority(issue) || isLongRunning(issue))
    .sort((a, b) => urgentScore(b) - urgentScore(a))
    .slice(0, 5)
})

const chartItems = computed(() => {
  const list = summarizeByKey(leafProjects.value, 'customerName').slice(0, 10)
  const max = Math.max(...list.map(item => item.openCount || 0), 1)
  return list.map(item => ({ ...item, height: 28 + Math.round(((item.openCount || 0) / max) * 150) }))
})

const ownerWorkload = computed(() => {
  const bucket = new Map()
  activeIssues.value.forEach(issue => {
    const ownerName = issue.ownerName || '未分配'
    const current = bucket.get(ownerName) || {
      ownerName,
      total: 0,
      openCount: 0,
      inProgressCount: 0,
      overdueCount: 0
    }
    current.total += 1
    if (issue.currentStatus === 'OPEN') current.openCount += 1
    if (issue.currentStatus === 'IN_PROGRESS') current.inProgressCount += 1
    if (issue.overdue) current.overdueCount += 1
    bucket.set(ownerName, current)
  })

  const list = Array.from(bucket.values()).sort((a, b) => b.overdueCount - a.overdueCount || b.total - a.total)
  const max = Math.max(...list.map(item => item.total), 1)
  return list.slice(0, 8).map(item => ({ ...item, percent: Math.round((item.total / max) * 100) }))
})

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

function isClosed(issue) {
  return issue.currentStatus === 'CLOSED' || issue.closureStatus === 'CLOSED'
}

function issueAgeText(issue) {
  const days = daysSince(issue.receivedAt || issue.foundAt)
  if (!days) return '今日新增'
  return `${days} 天`
}

function daysSince(value) {
  if (!value) return 0
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 0
  return Math.max(0, Math.floor((Date.now() - date.getTime()) / 86400000))
}

function isHighPriority(issue) {
  const text = `${issue.priority || ''}${issue.severity || ''}`
  return /高|紧急|严重|P0|P1/i.test(text)
}

function isLongRunning(issue) {
  return issue.currentStatus === 'IN_PROGRESS' && daysSince(issue.receivedAt || issue.foundAt) >= Number(issue.remindAfterDays || 7)
}

function urgentScore(issue) {
  let score = 0
  if (issue.overdue) score += 100
  if (!issue.ownerName) score += 40
  if (isHighPriority(issue)) score += 30
  if (issue.currentStatus === 'OPEN') score += 20
  score += Math.min(daysSince(issue.receivedAt || issue.foundAt), 30)
  return score
}

function workloadColor(percent) {
  if (percent >= 80) return '#f56c6c'
  if (percent >= 50) return '#fa8c16'
  return '#1fa463'
}

async function loadDashboard() {
  loading.value = true
  try {
    const [overviewRes, issueRes, projectRes] = await Promise.all([
      getIssueOverview(),
      getIssues({ page: 1, pageSize: 200 }),
      getProjects()
    ])
    overview.value = { ...overview.value, ...(overviewRes.data || {}) }
    issuePool.value = issueRes.data?.items || []
    recentIssues.value = issuePool.value.slice(0, 6)
    projects.value = projectRes.data || []
  } finally {
    loading.value = false
  }
}

function goIssues() {
  router.push('/issues')
}

function goIssue(row) {
  if (row?.id) router.push(`/issues/${row.id}`)
}

onMounted(loadDashboard)
</script>

<style scoped>
.top-meta {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.lower-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.alert-card {
  min-height: 360px;
}

.alert-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.alert-metric {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
}

.alert-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 8px;
  color: #fff;
}

.alert-metric.danger .alert-icon {
  background: #f56c6c;
}

.alert-metric.warning .alert-icon {
  background: #fa8c16;
}

.alert-metric.primary .alert-icon {
  background: #1677ff;
}

.alert-metric span {
  display: block;
  color: var(--text-muted);
  font-size: 13px;
}

.alert-metric strong {
  display: block;
  margin-top: 4px;
  color: var(--text-main);
  font-size: 24px;
}

.urgent-list {
  margin-top: 20px;
}

.list-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-main);
  font-weight: 700;
}

.list-head small {
  color: var(--text-muted);
  font-weight: 400;
}

.urgent-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.urgent-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.urgent-item:hover {
  border-color: #91caff;
  background: #f7fbff;
}

.urgent-item strong,
.urgent-item span {
  display: block;
}

.urgent-item strong {
  max-width: 320px;
  overflow: hidden;
  color: var(--text-main);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.urgent-item span {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.urgent-meta {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.urgent-meta small {
  color: var(--text-muted);
}

.bar-chart {
  height: 286px;
  display: flex;
  align-items: end;
  gap: 18px;
  padding: 24px 4px 4px;
  overflow-x: auto;
}

.bar-item {
  width: 54px;
  flex: 0 0 54px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: end;
  gap: 8px;
}

.bar-value {
  font-size: 12px;
}

.bar {
  width: 30px;
  min-height: 24px;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(180deg, #73b7ff 0%, #1677ff 100%);
  box-shadow: 0 8px 16px rgba(22, 119, 255, 0.16);
}

.bar-name {
  width: 70px;
  color: var(--text-muted);
  text-align: center;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workload-card :deep(.el-progress-bar__outer) {
  background-color: #edf1f7;
}

@media (max-width: 1280px) {
  .dashboard-grid,
  .lower-grid,
  .alert-summary {
    grid-template-columns: 1fr;
  }

  .urgent-item {
    align-items: flex-start;
  }
}
</style>
