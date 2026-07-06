<template>
  <div class="page-shell" v-loading="loading">
    <section class="section-card">
      <div class="toolbar">
        <div>
          <div class="page-title">用户管理</div>
          <p class="page-subtitle">维护普通账号、角色、状态、联系方式和项目授权。</p>
        </div>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>
    </section>

    <section class="section-card">
      <el-table :data="normalUsers" row-key="id" empty-text="暂无用户">
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column label="手机号/邮箱" min-width="190">
          <template #default="{ row }">{{ row.phone || row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="最后登录" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="toggleStatus(row)">{{ Number(row.status) === 1 ? '禁用' : '启用' }}</el-button>
            <el-button link type="primary" @click="resetPassword(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingUser ? '编辑用户' : '新增用户'" width="720px">
      <el-form :model="form" label-position="top">
        <div class="dialog-grid">
          <el-form-item label="用户名" required>
            <el-input v-model="form.username" :disabled="!!editingUser" />
          </el-form-item>
          <el-form-item v-if="!editingUser" label="初始密码">
            <el-input v-model="form.password" placeholder="默认密码由系统配置决定" />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="form.realName" />
          </el-form-item>
          <el-form-item label="手机">
            <el-input v-model="form.phone" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.email" />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="form.roleId" placeholder="选择角色">
              <el-option v-for="role in normalRoles" :key="role.id" :label="role.roleName" :value="role.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
          </el-form-item>
          <el-form-item label="全局检索">
            <el-switch v-model="form.globalSearchEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </div>
        <el-form-item label="授权项目">
          <el-select v-model="form.projectIds" multiple filterable collapse-tags placeholder="选择可访问项目">
            <el-option v-for="project in projectOptions" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createAdminUser, getAdminRoles, getAdminUsers, resetAdminUserPassword, updateAdminUser, updateAdminUserStatus } from '@/api/admin'
import { getProjects } from '@/api/projects'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const dialogVisible = ref(false)
const editingUser = ref(null)
const users = ref([])
const roles = ref([])
const projects = ref([])
const form = reactive(defaultForm())

const normalUsers = computed(() => users.value.filter(user => user.accountType !== 'TEMP'))
const normalRoles = computed(() => roles.value.filter(role => role.roleCode !== 'TEMP_WORKER'))
const projectOptions = computed(() => projects.value.filter(project => (project.projectLevel || 'PROJECT') === 'PROJECT'))

function defaultForm() {
  return { username: '', password: '', realName: '', phone: '', email: '', roleId: null, status: 1, globalSearchEnabled: 0, projectIds: [] }
}

function resetForm(data = defaultForm()) {
  Object.assign(form, defaultForm(), data)
}

async function loadData() {
  loading.value = true
  try {
    const [userRes, roleRes, projectRes] = await Promise.all([getAdminUsers(), getAdminRoles(), getProjects()])
    users.value = userRes.data || []
    roles.value = roleRes.data || []
    projects.value = projectRes.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingUser.value = null
  resetForm({ roleId: normalRoles.value.find(role => role.roleCode === 'ENGINEER')?.id || null })
  dialogVisible.value = true
}

function openEdit(row) {
  editingUser.value = row
  resetForm({ ...row, projectIds: [...(row.projectIds || [])] })
  dialogVisible.value = true
}

async function submit() {
  if (!form.username) {
    ElMessage.error('请输入用户名')
    return
  }
  const payload = { ...form }
  if (editingUser.value) {
    await updateAdminUser(editingUser.value.id, payload)
  } else {
    await createAdminUser(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadData()
}

async function toggleStatus(row) {
  const status = Number(row.status) === 1 ? 0 : 1
  await updateAdminUserStatus(row.id, status)
  ElMessage.success(status === 1 ? '已启用' : '已禁用')
  await loadData()
}

async function resetPassword(row) {
  await ElMessageBox.confirm(`确认将 ${row.username} 的密码重置为默认密码？`, '重置密码')
  const res = await resetAdminUserPassword(row.id)
  ElMessage.success(`已重置为 ${res.data?.temporaryPassword || '系统默认临时密码'}`)
}

onMounted(loadData)
</script>
