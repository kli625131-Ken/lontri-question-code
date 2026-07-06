<template>
  <header class="header-bar">
    <div class="breadcrumb">
      <el-icon><House /></el-icon>
      <span>/</span>
      <strong>{{ route.meta?.title || '仪表盘首页' }}</strong>
    </div>

    <div class="header-actions">
      <div class="global-search">
        <el-icon><Search /></el-icon>
        <span>搜索问题编号、标题、项目、客户等</span>
        <kbd>⌘ K</kbd>
      </div>

      <el-badge :value="12" class="notice-badge">
        <el-icon><Bell /></el-icon>
      </el-badge>
      <el-icon class="circle-icon"><QuestionFilled /></el-icon>

      <el-dropdown @command="handleCommand">
        <div class="user-chip">
          <el-avatar :size="38" :src="avatarUrl">{{ userInitial }}</el-avatar>
          <div class="user-copy">
            <div class="user-name">{{ displayName }}</div>
            <div class="user-role">{{ userRole }}</div>
          </div>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { userInfo } = storeToRefs(userStore)

const displayName = computed(() => userInfo.value?.realName || userInfo.value?.username || '张伟')
const userRole = computed(() => (userInfo.value?.isAdmin ? '超级管理员' : '运维管理员'))
const userInitial = computed(() => (displayName.value?.slice(0, 1) || '张').toUpperCase())
const avatarUrl = computed(() => userInfo.value?.avatar || '')

onMounted(async () => {
  if (!userInfo.value && userStore.token) {
    await userStore.fetchUserInfo().catch(() => {})
  }
})

async function handleCommand(command) {
  if (command === 'logout') {
    await userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.header-bar {
  height: 76px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 0 24px;
  border-bottom: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(14px);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 180px;
  font-size: 17px;
}

.breadcrumb strong {
  font-weight: 900;
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  min-width: 0;
}

.global-search {
  width: min(460px, 34vw);
  height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  color: var(--text-muted);
  background: #fff;
}

.global-search span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-search kbd {
  color: #56657e;
  font-size: 13px;
  font-family: inherit;
}

.notice-badge,
.circle-icon {
  font-size: 22px;
  color: #0f1f3d;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.user-name {
  font-weight: 900;
}

.user-role {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

@media (max-width: 860px) {
  .header-bar {
    height: auto;
    align-items: flex-start;
    flex-direction: column;
    padding: 14px 16px;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .global-search {
    width: 100%;
    order: 2;
  }
}
</style>
