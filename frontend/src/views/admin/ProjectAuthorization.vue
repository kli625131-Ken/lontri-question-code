<template>
  <div class="page-shell" v-loading="loading">
    <section class="section-card">
      <div class="page-title">项目授权</div>
      <p class="page-subtitle">管理员默认可查看全部项目；普通用户通过这里绑定可访问项目。</p>
    </section>

    <section class="auth-layout">
      <article class="section-card">
        <div class="section-title">用户</div>
        <div class="project-list">
          <button
            v-for="user in normalUsers"
            :key="user.id"
            type="button"
            class="project-row"
            :class="{ active: selectedUser?.id === user.id }"
            @click="selectUser(user)"
          >
            <div>
              <strong>{{ user.realName || user.username }}</strong>
              <span>{{ user.username }} / {{ user.roleName || '-' }}</span>
            </div>
            <el-tag :type="Number(user.status) === 1 ? 'success' : 'info'">{{ Number(user.status) === 1 ? '启用' : '禁用' }}</el-tag>
          </button>
        </div>
      </article>

      <article class="section-card">
        <div class="toolbar">
          <div>
            <div class="section-title">授权项目</div>
            <p class="page-subtitle">{{ selectedUser ? `当前用户：${selectedUser.realName || selectedUser.username}` : '请选择用户' }}</p>
          </div>
          <el-button type="primary" :disabled="!selectedUser" @click="submit">保存授权</el-button>
        </div>
        <el-select v-model="selectedProjectIds" multiple filterable collapse-tags placeholder="选择授权项目">
          <el-option v-for="project in projectOptions" :key="project.id" :label="project.projectName" :value="project.id" />
        </el-select>
        <el-table :data="selectedProjects" style="margin-top: 18px" empty-text="暂未选择项目">
          <el-table-column prop="customerName" label="客户" min-width="150" />
          <el-table-column prop="projectName" label="项目" min-width="220" />
          <el-table-column prop="projectCode" label="项目编码" min-width="180" />
        </el-table>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminUsers, saveUserProjectIds } from '@/api/admin'
import { getProjects } from '@/api/projects'

const loading = ref(false)
const users = ref([])
const projects = ref([])
const selectedUser = ref(null)
const selectedProjectIds = ref([])

const normalUsers = computed(() => users.value.filter(user => user.accountType !== 'TEMP' && Number(user.isAdmin) !== 1))
const projectOptions = computed(() => projects.value.filter(project => (project.projectLevel || 'PROJECT') === 'PROJECT'))
const selectedProjects = computed(() => projectOptions.value.filter(project => selectedProjectIds.value.includes(project.id)))

async function loadData() {
  loading.value = true
  try {
    const [userRes, projectRes] = await Promise.all([getAdminUsers(), getProjects()])
    users.value = userRes.data || []
    projects.value = projectRes.data || []
    if (!selectedUser.value && normalUsers.value.length) selectUser(normalUsers.value[0])
  } finally {
    loading.value = false
  }
}

function selectUser(user) {
  selectedUser.value = user
  selectedProjectIds.value = [...(user.projectIds || [])]
}

async function submit() {
  await saveUserProjectIds(selectedUser.value.id, selectedProjectIds.value)
  ElMessage.success('授权已保存')
  await loadData()
  const refreshed = normalUsers.value.find(user => user.id === selectedUser.value.id)
  if (refreshed) selectUser(refreshed)
}

onMounted(loadData)
</script>

<style scoped>
.auth-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.7fr) minmax(0, 1.3fr);
  gap: 18px;
}

.project-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}

.project-row {
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

.project-row.active,
.project-row:hover {
  border-color: #b9d7ff;
  background: #f4f9ff;
}

.project-row span {
  display: block;
  margin-top: 5px;
  color: var(--text-muted);
}
</style>
