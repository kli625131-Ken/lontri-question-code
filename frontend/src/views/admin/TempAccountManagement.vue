<template>
  <div class="page-shell" v-loading="loading">
    <section class="section-card">
      <div class="toolbar">
        <div>
          <div class="page-title">临时账号管理</div>
          <p class="page-subtitle">临时账号需设置有效期和项目，只能新建问题、上传图片并查看自己创建的问题。</p>
        </div>
        <el-button type="primary" @click="openCreate">创建临时账号</el-button>
      </div>
    </section>

    <section class="section-card">
      <el-table :data="users" row-key="id" empty-text="暂无临时账号">
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column label="联系方式" min-width="180">
          <template #default="{ row }">{{ row.phone || row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="有效期" width="170">
          <template #default="{ row }">{{ formatDateTime(row.expireAt) }}</template>
        </el-table-column>
        <el-table-column label="授权项目" width="120">
          <template #default="{ row }">{{ row.projectIds?.length || 0 }} 个</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tempStatus(row).type">{{ tempStatus(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="disable(row)" :disabled="Number(row.status) !== 1">禁用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingUser ? '编辑临时账号' : '创建临时账号'" width="720px">
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
          <el-form-item label="有效期" required>
            <el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
          </el-form-item>
        </div>
        <el-form-item label="绑定项目">
          <el-select v-model="form.projectIds" multiple filterable collapse-tags placeholder="选择绑定项目">
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
import { createTempUser, disableTempUser, getTempUsers, updateTempUser } from '@/api/admin'
import { getProjects } from '@/api/projects'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const dialogVisible = ref(false)
const editingUser = ref(null)
const users = ref([])
const projects = ref([])
const form = reactive(defaultForm())

const projectOptions = computed(() => projects.value.filter(project => (project.projectLevel || 'PROJECT') === 'PROJECT'))

function defaultForm() {
  return { username: '', password: '', realName: '', phone: '', email: '', expireAt: '', status: 1, projectIds: [] }
}

function resetForm(data = defaultForm()) {
  Object.assign(form, defaultForm(), data)
}

function tempStatus(row) {
  if (Number(row.status) !== 1) return { label: '禁用', type: 'info' }
  if (row.expireAt && new Date(row.expireAt).getTime() <= Date.now()) return { label: '已过期', type: 'danger' }
  return { label: '有效', type: 'success' }
}

async function loadData() {
  loading.value = true
  try {
    const [userRes, projectRes] = await Promise.all([getTempUsers(), getProjects()])
    users.value = userRes.data || []
    projects.value = projectRes.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingUser.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingUser.value = row
  resetForm({ ...row, projectIds: [...(row.projectIds || [])] })
  dialogVisible.value = true
}

async function submit() {
  if (!form.username || !form.expireAt) {
    ElMessage.error('请填写用户名和有效期')
    return
  }
  if (editingUser.value) {
    await updateTempUser(editingUser.value.id, { ...form })
  } else {
    await createTempUser({ ...form })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadData()
}

async function disable(row) {
  await ElMessageBox.confirm(`确认禁用临时账号 ${row.username}？`, '禁用账号')
  await disableTempUser(row.id)
  ElMessage.success('已禁用')
  await loadData()
}

onMounted(loadData)
</script>
