<template>
  <div class="page-shell" v-loading="loading">
    <section class="section-card">
      <div class="page-title">角色说明</div>
      <p class="page-subtitle">维护基础角色说明；页面访问、项目范围和账号状态在用户管理与项目授权中配置。</p>
    </section>

    <section class="section-card">
      <el-table :data="roles" row-key="id" empty-text="暂无角色">
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="roleCode" label="角色编码" width="170" />
        <el-table-column prop="description" label="说明" min-width="320" show-overflow-tooltip />
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" title="编辑角色说明" width="560px">
      <el-form label-position="top">
        <el-form-item label="角色">
          <el-input :model-value="editingRole?.roleName" disabled />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="description" type="textarea" :rows="4" />
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
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminRoles, updateAdminRole } from '@/api/admin'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const roles = ref([])
const dialogVisible = ref(false)
const editingRole = ref(null)
const description = ref('')

async function loadRoles() {
  loading.value = true
  try {
    const res = await getAdminRoles()
    roles.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  editingRole.value = row
  description.value = row.description || ''
  dialogVisible.value = true
}

async function submit() {
  await updateAdminRole(editingRole.value.id, { description: description.value })
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadRoles()
}

onMounted(loadRoles)
</script>
