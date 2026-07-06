<template>
  <div class="sidebar-shell">
    <div class="brand-block">
      <img :src="logoImage" alt="问题库运维平台" class="brand-logo" />
      <div class="brand-name">问题库运维平台</div>
    </div>

    <el-menu :default-active="route.path" class="sidebar-menu" router>
      <el-menu-item index="/dashboard">
        <el-icon><House /></el-icon>
        <span>仪表盘首页</span>
      </el-menu-item>
      <el-menu-item index="/issues">
        <el-icon><Tickets /></el-icon>
        <span>问题台账</span>
      </el-menu-item>
      <el-menu-item index="/projects">
        <el-icon><Calendar /></el-icon>
        <span>项目中心</span>
      </el-menu-item>
      <el-menu-item index="/knowledge">
        <el-icon><Collection /></el-icon>
        <span>知识库</span>
      </el-menu-item>
      <el-menu-item index="/imports">
        <el-icon><Download /></el-icon>
        <span>数据导入</span>
      </el-menu-item>
      <el-sub-menu v-if="isAdmin" index="/admin">
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>账号授权</span>
        </template>
        <el-menu-item index="/admin/users">用户管理</el-menu-item>
        <el-menu-item index="/admin/roles">角色说明</el-menu-item>
        <el-menu-item index="/admin/project-auth">项目授权</el-menu-item>
        <el-menu-item index="/admin/temp-users">临时账号</el-menu-item>
      </el-sub-menu>
    </el-menu>

    <div class="sidebar-spacer"></div>

    <button type="button" class="collapse-button">
      <el-icon><DArrowLeft /></el-icon>
      <span>收起菜单</span>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'
import logoImage from '@/img/lontrilogo.png'

const route = useRoute()
const userStore = useUserStore()
const { userInfo } = storeToRefs(userStore)
const isAdmin = computed(() => Number(userInfo.value?.isAdmin) === 1 || userInfo.value?.username === 'admin')
</script>

<style scoped>
.sidebar-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px 16px 18px;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 10px 24px;
}

.brand-logo {
  width: 34px;
  height: 34px;
  object-fit: contain;
}

.brand-name {
  font-size: 21px;
  font-weight: 900;
  color: var(--text-main);
}

.sidebar-menu {
  border: none;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 50px;
  margin: 7px 0;
  border-radius: 8px;
  color: #263955;
  font-size: 16px;
  font-weight: 700;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: var(--primary-soft);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: linear-gradient(135deg, #1677ff 0%, #0f62d8 100%);
  box-shadow: 0 8px 18px rgba(22, 119, 255, 0.22);
}

.sidebar-spacer {
  flex: 1;
}

.collapse-button {
  height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

@media (max-width: 1080px) {
  .sidebar-shell {
    height: auto;
  }
}
</style>
